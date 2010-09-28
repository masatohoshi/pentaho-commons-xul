package org.pentaho.ui.xul.gwt.tags;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import com.allen_sauer.gwt.dnd.client.*;
import com.allen_sauer.gwt.dnd.client.drop.AbstractPositioningDropController;
import com.google.gwt.user.client.ui.*;
import org.pentaho.gwt.widgets.client.buttons.ImageButton;
import org.pentaho.gwt.widgets.client.listbox.CustomListBox;
import org.pentaho.gwt.widgets.client.table.BaseTable;
import org.pentaho.gwt.widgets.client.table.ColumnComparators.BaseColumnComparator;
import org.pentaho.gwt.widgets.client.ui.Draggable;
import org.pentaho.gwt.widgets.client.utils.string.StringUtils;
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulEventSource;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingConvertor;
import org.pentaho.ui.xul.binding.InlineBindingExpression;
import org.pentaho.ui.xul.components.XulTreeCell;
import org.pentaho.ui.xul.components.XulTreeCol;
import org.pentaho.ui.xul.containers.*;
import org.pentaho.ui.xul.dnd.DataTransfer;
import org.pentaho.ui.xul.dnd.DropEvent;
import org.pentaho.ui.xul.dnd.DropPosition;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.dom.Element;
import org.pentaho.ui.xul.gwt.AbstractGwtXulContainer;
import org.pentaho.ui.xul.gwt.GwtXulHandler;
import org.pentaho.ui.xul.gwt.GwtXulParser;
import org.pentaho.ui.xul.gwt.binding.GwtBinding;
import org.pentaho.ui.xul.gwt.binding.GwtBindingContext;
import org.pentaho.ui.xul.gwt.binding.GwtBindingMethod;
import org.pentaho.ui.xul.gwt.tags.util.TreeItemWidget;
import org.pentaho.ui.xul.gwt.util.Resizable;
import org.pentaho.ui.xul.gwt.util.XulDragController;
import org.pentaho.ui.xul.impl.XulEventHandler;
import org.pentaho.ui.xul.stereotype.Bindable;
import org.pentaho.ui.xul.util.TreeCellEditor;
import org.pentaho.ui.xul.util.TreeCellEditorCallback;
import org.pentaho.ui.xul.util.TreeCellRenderer;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.widgetideas.table.client.SourceTableSelectionEvents;
import com.google.gwt.widgetideas.table.client.TableSelectionListener;
import com.google.gwt.widgetideas.table.client.SelectionGrid.SelectionPolicy;

public class GwtTree extends AbstractGwtXulContainer implements XulTree, Resizable {

  /**
   * Cached elements.
   */
  private Collection elements;
  private GwtBindingMethod dropVetoerMethod;
  private XulEventHandler dropVetoerController;


  public static void register() {
    GwtXulParser.registerHandler("tree",
    new GwtXulHandler() {
      public Element newInstance() {
        return new GwtTree();
      }
    });
  }

  XulTreeCols columns = null;
  XulTreeChildren rootChildren = null;
  private XulDomContainer domContainer;
  private Tree tree;
  private boolean suppressEvents = false;
  private boolean editable = false;
  private boolean visible = true;
  private String command;

  private Map<String, TreeCellEditor> customEditors = new HashMap<String, TreeCellEditor>();
  private Map<String, TreeCellRenderer> customRenderers = new HashMap<String, TreeCellRenderer>();

  private List<Binding> elementBindings = new ArrayList<Binding>();
  private List<Binding> expandBindings = new ArrayList<Binding>();

  private DropPositionIndicator dropPosIndicator = new DropPositionIndicator();

  @Bindable
  public boolean isVisible() {
    return visible;
  }

  @Bindable
  public void setVisible(boolean visible) {
    this.visible = visible;
    if(simplePanel != null) {
      simplePanel.setVisible(visible);
    }
  }

  /**
   * Used when this widget is a tree. Not used when this widget is a table.
   */
  private FlowPanel scrollPanel = new FlowPanel();
  {
    scrollPanel.setStylePrimaryName("scroll-panel");
  }

  /**
   * The managed object. If this widget is a tree, then the tree is added to the scrollPanel, which is added to this
   * simplePanel. If this widget is a table, then the table is added directly to this simplePanel.
   */
  private SimplePanel simplePanel = new SimplePanel();

  /**
   * Clears the parent panel and adds the given widget.
   * @param widget tree or table to set in parent panel
   */
  protected void setWidgetInPanel(final Widget widget) {
    if (isHierarchical()) {
      scrollPanel.clear();
      simplePanel.add(scrollPanel);
      scrollPanel.add(widget);
      scrollPanel.add(dropPosIndicator);
    } else {
      simplePanel.clear();
      simplePanel.add(widget);
    }
  }

  public GwtTree() {
    super("tree");

    // managedObject is neither a native GWT tree nor a table since the entire native GWT object is thrown away each
    // time we call setup{Tree|Table}; because the widget is thrown away, we need to reconnect the new widget to the
    // simplePanel, which is the managedObject
    setManagedObject(simplePanel);

  }

  public void init(com.google.gwt.xml.client.Element srcEle, XulDomContainer container) {
    super.init(srcEle, container);
    setOnselect(srcEle.getAttribute("onselect"));
    setOnedit(srcEle.getAttribute("onedit"));
    setSeltype(srcEle.getAttribute("seltype"));
    setOndrop(srcEle.getAttribute("pen:ondrop"));
    setOndrag(srcEle.getAttribute("pen:ondrag"));
    setDrageffect(srcEle.getAttribute("pen:drageffect"));

    setDropvetoer(srcEle.getAttribute("pen:dropvetoer"));
    
    this.setEditable("true".equals(srcEle.getAttribute("editable")));
    this.domContainer = container;
  }
  private List<TreeItemDropController> dropHandlers = new ArrayList<TreeItemDropController>();
  public void addChild(Element element) {
    super.addChild(element);
    if (element.getName().equals("treecols")) {
      columns = (XulTreeCols)element;
    } else if (element.getName().equals("treechildren")) {
      rootChildren = (XulTreeChildren)element;
    }
  }

  public void addTreeRow(XulTreeRow row) {
      GwtTreeItem item = new GwtTreeItem();
      item.setRow(row);
      this.rootChildren.addItem(item);

      // update UI
      updateUI();
  }
  private BaseTable table;
  private boolean isHierarchical = false;
  private boolean firstLayout = true;
  private Object[][] currentData;

  // need to handle layouting
  public void layout() {
    if(this.getRootChildren() == null){
      //most likely an overlay
      return;
    }
    if(firstLayout){

      XulTreeItem item = (XulTreeItem) this.getRootChildren().getFirstChild();

      if(item != null && item.getAttributeValue("container") != null && item.getAttributeValue("container").equals("true")){
        isHierarchical = true;
      }
      firstLayout = false;
    }

    if(isHierarchical()){
      setupTree();
    } else {
      setupTable();
    }
    setVisible(visible);
  }

  private int prevSelectionPos = -1;
  private void setupTree(){
    if(tree == null){
      tree = new Tree();
      setWidgetInPanel(tree);
    }
    scrollPanel.setWidth("100%"); //$NON-NLS-1$
    scrollPanel.setHeight("100%"); //$NON-NLS-1$
    scrollPanel.getElement().getStyle().setProperty("backgroundColor", "white");  //$NON-NLS-1$//$NON-NLS-2$
    if (getWidth() > 0){
      scrollPanel.setWidth(getWidth()+"px"); //$NON-NLS-1$
    }
    if (getHeight() > 0) {
      scrollPanel.setHeight(getHeight()+"px"); //$NON-NLS-1$
    }

    tree.addTreeListener(new TreeListener(){

      public void onTreeItemSelected(TreeItem arg0) {
        int pos = -1;
        int curPos = 0;
        for(int i=0; i < tree.getItemCount(); i++){
          TreeItem tItem = tree.getItem(i);
          TreeCursor cursor = GwtTree.this.findPosition(tItem, arg0, curPos);
          pos = cursor.foundPosition;
          curPos = cursor.curPos+1;
          if(pos > -1){
            break;
          }
        }

        if(pos > -1 && GwtTree.this.suppressEvents == false && prevSelectionPos != pos){
          GwtTree.this.changeSupport.firePropertyChange("selectedRows",null,new int[]{pos});
          GwtTree.this.changeSupport.firePropertyChange("absoluteSelectedRows",null,new int[]{pos});
          GwtTree.this.fireSelectedItems();
        }
        prevSelectionPos = pos;

      }

      public void onTreeItemStateChanged(TreeItem item) {
        ((TreeItemWidget) item.getWidget()).getTreeItem().setExpanded(item.getState());
      }

    });
    updateUI();
  }

  private class TreeCursor{
    int foundPosition = -1;
    int curPos;
    TreeItem itemToFind;
    public TreeCursor(int curPos, TreeItem itemToFind, int foundPosition){
      this.foundPosition = foundPosition;
      this.curPos = curPos;
      this.itemToFind = itemToFind;
    }
  }

  private TreeCursor findPosition(TreeItem curItem, TreeItem itemToFind, int curPos){
    if(curItem == itemToFind){
      TreeCursor p = new TreeCursor(curPos, itemToFind, curPos);
      return p;
//    } else if(curItem.getChildIndex(itemToFind) > -1) {
//      curPos = curPos+1;
//      return new TreeCursor(curPos+curItem.getChildIndex(itemToFind) , itemToFind, curPos+curItem.getChildIndex(itemToFind));
    } else {
      for(int i=1; i-1<curItem.getChildCount(); i++){
        TreeCursor p = findPosition(curItem.getChild(i-1), itemToFind, curPos +1);
        curPos = p.curPos;
        if(p.foundPosition > -1){
          return p;
        }
      }
        //curPos += curItem.getChildCount() ;
      return new TreeCursor(curPos, itemToFind, -1);
    }

  }

  private void populateTree(){
    tree.removeItems();
    TreeItem topNode = new TreeItem("placeholder");
    if(this.rootChildren == null){
      this.rootChildren = (XulTreeChildren) this.getChildNodes().get(1);
    }
    for (XulComponent c : this.rootChildren.getChildNodes()){
      XulTreeItem item = (XulTreeItem) c;
      TreeItem node = createNode(item);
      tree.addItem(node);
    }


    if(StringUtils.isEmpty(this.ondrag) == false){
      for (int i = 0; i < tree.getItemCount(); i++) {
        madeDraggable(tree.getItem(i));
      }
    }

  }

  private void madeDraggable(TreeItem item){

    XulDragController.getInstance().makeDraggable(item.getWidget());

      for (int i = 0; i < item.getChildCount(); i++) {
        madeDraggable(item.getChild(i));
      }
  }

  private void populateTable(){

    currentData = new Object[getRootChildren().getItemCount()][getColumns().getColumnCount()];

    for (int i = 0; i < getRootChildren().getItemCount(); i++) {
      for (int j = 0; j < getColumns().getColumnCount(); j++) {
        currentData[i][j] = getColumnEditor(j,i);
      }
    }

    table.populateTable(currentData);
    int totalFlex = 0;
    for (int i = 0; i < getColumns().getColumnCount(); i++) {
      totalFlex += getColumns().getColumn(i).getFlex();
    }
    if(totalFlex > 0){
      table.fillWidth();
    }

    if(this.selectedRows != null && this.selectedRows.length > 0){
      for(int i=0; i<this.selectedRows.length; i++){
        int idx = this.selectedRows[i];
        if(idx > -1 && idx < currentData.length){
          table.selectRow(idx);
        }
      }
    }

  }

  private TreeItem createNode(final XulTreeItem item){
    TreeItem node = new TreeItem("empty"){
      @Override
      public void setSelected( boolean selected ) {
        super.setSelected(selected);
        if(selected){
          this.getWidget().addStyleDependentName("selected");
        } else {
          this.getWidget().removeStyleDependentName("selected");
        }
      }
    };
    item.setManagedObject(node);
    if(item == null || item.getRow() == null || item.getRow().getChildNodes().size() == 0){
      return node;
    }
    final TreeItemWidget tWidget = new TreeItemWidget(item);

    PropertyChangeListener listener = new PropertyChangeListener(){
      public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName().equals("image") && item.getImage() != null){
          tWidget.setImage(new Image(GWT.getModuleBaseURL() + item.getImage()));
        } else if(evt.getPropertyName().equals("label")){
          tWidget.setLabel(item.getRow().getCell(0).getLabel());
        }
      }
    };

    ((GwtTreeItem) item).addPropertyChangeListener("image", listener);
    ((GwtTreeCell) item.getRow().getCell(0)).addPropertyChangeListener("label", listener);;
    tWidget.setImage(new Image(GWT.getModuleBaseURL() + item.getImage()));



    tWidget.setLabel(item.getRow().getCell(0).getLabel());
    if(this.ondrop != null){
      TreeItemDropController controller = new TreeItemDropController(item, tWidget);
      XulDragController.getInstance().registerDropController(controller);
      this.dropHandlers.add(controller);
    }

    node.setWidget(tWidget);

    if(item.getChildNodes().size() > 1){
      //has children
      //TODO: make this more defensive
      XulTreeChildren children = (XulTreeChildren) item.getChildNodes().get(1);
      for(XulComponent c : children.getChildNodes()){

        TreeItem childNode = createNode((XulTreeItem) c);
        node.addItem(childNode);
      }
    }
    return node;
  }

  private String extractDynamicColType(Object row, int columnPos) {
    GwtBindingMethod method = GwtBindingContext.typeController.findGetMethod(row, this.columns.getColumn(columnPos).getColumntypebinding());
    try{
      return (String) method.invoke(row, new Object[]{});
    } catch (Exception e){
      System.out.println("Could not extract column type from binding");
    }
    return "text"; // default //$NON-NLS-1$
  }

  private Binding createBinding(XulEventSource source, String prop1, XulEventSource target, String prop2){
    if(bindingProvider != null){
      return bindingProvider.getBinding(source, prop1, target, prop2);
    }
    return new GwtBinding(source, prop1, target, prop2);
  }

  private Widget getColumnEditor(final int x, final int y){


    final XulTreeCol column = this.columns.getColumn(x);
    String colType = this.columns.getColumn(x).getType();
    String val = getRootChildren().getItem(y).getRow().getCell(x).getLabel();
    final GwtTreeCell cell = (GwtTreeCell) getRootChildren().getItem(y).getRow().getCell(x);

    // If collection bound, bindings may need to be updated for runtime changes in column types.
    if(this.elements != null){
      try {
        this.addBindings(column, cell, this.elements.toArray()[y]);
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      } catch (XulException e) {
        e.printStackTrace();
      }
    }

    if(StringUtils.isEmpty(colType) == false && colType.equalsIgnoreCase("dynamic")){
      Object row = elements.toArray()[y];
      colType = extractDynamicColType(row, x);
    }

    if(!colType.equals("checkbox") && (StringUtils.isEmpty(colType) || !column.isEditable() || getSelectedRows().length != 1  || getSelectedRows()[0] != y)){
      if(colType.equalsIgnoreCase("combobox") || colType.equalsIgnoreCase("editablecombobox")){
        Vector vals = (Vector) cell.getValue();
        int idx = cell.getSelectedIndex();
        if(colType.equalsIgnoreCase("editablecombobox")){
           return new HTML(cell.getLabel());
        } else if(idx < vals.size()){
          return new HTML(""+vals.get(idx));
        } else {
          return new HTML("");
        }
      } else {
        return new HTML(val);
      }
    } else if(colType.equalsIgnoreCase("text")){

      try{

        GwtTextbox b = (GwtTextbox) this.domContainer.getDocumentRoot().createElement("textbox");
        b.setDisabled(!column.isEditable());
        b.layout();
        b.setValue(val);
        Binding bind = createBinding(cell, "label", b, "value");
        bind.setBindingType(Binding.Type.BI_DIRECTIONAL);
        domContainer.addBinding(bind);
        bind.fireSourceChanged();

        bind = createBinding(cell, "disabled", b, "disabled");
        bind.setBindingType(Binding.Type.BI_DIRECTIONAL);
        domContainer.addBinding(bind);
        bind.fireSourceChanged();

        return (Widget) b.getManagedObject();
      } catch(Exception e){
        System.out.println("error creating textbox, fallback");
        e.printStackTrace();
        final TextBox b = new TextBox();
        b.addKeyboardListener(new KeyboardListener(){


          public void onKeyDown(Widget arg0, char arg1, int arg2) {}

          public void onKeyPress(Widget arg0, char arg1, int arg2) {}

          public void onKeyUp(Widget arg0, char arg1, int arg2) {
            getRootChildren().getItem(y).getRow().getCell(x).setLabel(b.getText());
          }

        });
        b.setText(val);
        return b;
      }
    } else if (colType.equalsIgnoreCase("checkbox")) {
      try {
        GwtCheckbox checkBox = (GwtCheckbox) this.domContainer.getDocumentRoot().createElement("checkbox");
        checkBox.setDisabled(!column.isEditable());
        checkBox.layout();
        checkBox.setChecked(Boolean.parseBoolean(val));
        Binding bind = createBinding(cell, "value", checkBox, "checked");
        bind.setBindingType(Binding.Type.BI_DIRECTIONAL);
        domContainer.addBinding(bind);
        bind.fireSourceChanged();

        bind = createBinding(cell, "disabled", checkBox, "disabled");
        bind.setBindingType(Binding.Type.BI_DIRECTIONAL);
        domContainer.addBinding(bind);

        return (Widget)checkBox.getManagedObject();
      } catch (Exception e) {
        final CheckBox cb = new CheckBox();
        return cb;
      }


    } else if(colType.equalsIgnoreCase("combobox") || colType.equalsIgnoreCase("editablecombobox")){
      try{
        final GwtMenuList glb = (GwtMenuList) this.domContainer.getDocumentRoot().createElement("menulist");

        final CustomListBox lb = glb.getNativeListBox();

        lb.setWidth("100%");
        final String fColType = colType;

        // Binding to elements of listbox not giving us the behavior we want. Menulists select the first available
        // item by default. We don't want anything selected by default in these cell editors
        PropertyChangeListener listener = new PropertyChangeListener(){
          public void propertyChange(PropertyChangeEvent evt) {

            Vector vals = (Vector) cell.getValue();
            lb.clear();
            lb.setSuppressLayout(true);
            for(Object label : vals){
              lb.addItem(label.toString());
            }
            lb.setSuppressLayout(false);

            int idx = cell.getSelectedIndex();
            if(idx < 0){
              idx = 0;
            }
            if(idx < vals.size()){
              lb.setSelectedIndex(idx);
            }

            if(fColType.equalsIgnoreCase("editablecombobox")){
              lb.setValue("");
            }
          }
        };
        listener.propertyChange(null);
        cell.addPropertyChangeListener("value", listener);

        lb.addChangeListener(new ChangeListener(){

          public void onChange(Widget arg0) {
            if(fColType.equalsIgnoreCase("editablecombobox")){
              cell.setLabel(lb.getValue());
            } else {
              cell.setSelectedIndex(lb.getSelectedIndex());
            }
          }

        });

        if(colType.equalsIgnoreCase("editablecombobox")){
          lb.setEditable(true);

          Binding bind = createBinding(cell, "selectedIndex", glb, "selectedIndex");
          bind.setBindingType(Binding.Type.BI_DIRECTIONAL);
          domContainer.addBinding(bind);
          if(cell.getLabel() == null){
            bind.fireSourceChanged();
          }

          bind = createBinding(cell, "label", glb, "value");
          bind.setBindingType(Binding.Type.BI_DIRECTIONAL);
          domContainer.addBinding(bind);
          bind.fireSourceChanged();


        } else {

          Binding bind = createBinding(cell, "selectedIndex", glb, "selectedIndex");
          bind.setBindingType(Binding.Type.BI_DIRECTIONAL);
          domContainer.addBinding(bind);
          bind.fireSourceChanged();

        }

        Binding bind = createBinding(cell, "disabled", glb, "disabled");
        bind.setBindingType(Binding.Type.BI_DIRECTIONAL);
        domContainer.addBinding(bind);
        bind.fireSourceChanged();

        return lb;
      } catch(Exception e){
        System.out.println("error creating menulist, fallback");
        e.printStackTrace();
        final CustomListBox lb = new CustomListBox();

        lb.setWidth("100%");

        Vector vals = (Vector) cell.getValue();
        lb.setSuppressLayout(true);
        for(Object label : vals){
          lb.addItem(label.toString());
        }
        lb.setSuppressLayout(false);
        lb.addChangeListener(new ChangeListener(){

          public void onChange(Widget arg0) {
            if(column.getType().equalsIgnoreCase("editablecombobox")){
              cell.setLabel(lb.getValue());
            } else {
              cell.setSelectedIndex(lb.getSelectedIndex());
            }
          }

        });

        int idx = cell.getSelectedIndex();
        if(idx < 0){
          idx = 0;
        }
        if(idx < vals.size()){
          lb.setSelectedIndex(idx);
        }
        if(colType.equalsIgnoreCase("editablecombobox")){
          lb.setEditable(true);
        }
        lb.setValue(cell.getLabel());

        return lb;
      }
    } else if (colType != null && customEditors.containsKey(colType)){
      if(this.customRenderers.containsKey(colType)){
        return new CustomCellEditorWrapper(cell, customEditors.get(colType), customRenderers.get(colType));
      } else {
        return new CustomCellEditorWrapper(cell, customEditors.get(colType));
      }

    } else {
      if(val == null || val.equals("")){
        return new HTML("&nbsp;");
      }
      return new HTML(val);
    }


  }

  private int curSelectedRow = -1;
  private void setupTable(){
    String cols[] = new String[getColumns().getColumnCount()];


    SelectionPolicy selectionPolicy = null;
    if ("single".equals(getSeltype())) {
      selectionPolicy = SelectionPolicy.ONE_ROW;
    } else if ("multiple".equals(getSeltype())) {
      selectionPolicy = SelectionPolicy.MULTI_ROW;
    }

    int[] widths = new int[cols.length];
    int totalFlex = 0;
    for (int i = 0; i < cols.length; i++) {
      totalFlex += getColumns().getColumn(i).getFlex();
    }

    for (int i = 0; i < cols.length; i++) {
      cols[i] = getColumns().getColumn(i).getLabel();
      if(totalFlex > 0 && getWidth() > 0){
    	  widths[i] = (int) (getWidth() * ((double) getColumns().getColumn(i).getFlex() / totalFlex));
      } else if(getColumns().getColumn(i).getWidth() > 0){
    	  widths[i] = getColumns().getColumn(i).getWidth();
      }
    }

    table = new BaseTable(cols, widths, new BaseColumnComparator[cols.length], selectionPolicy);

    if (getHeight() != 0) {
	  table.setHeight(getHeight() + "px");
	} else {
		table.setHeight("100%");
    }
	if (getWidth() != 0) {
	  table.setWidth(getWidth() + "px");
	} else {
		table.setWidth("100%");
	}

    table.addTableSelectionListener(new TableSelectionListener() {
      public void onAllRowsDeselected(SourceTableSelectionEvents sender) {
      }
      public void onCellHover(SourceTableSelectionEvents sender, int row, int cell) {
      }
      public void onCellUnhover(SourceTableSelectionEvents sender, int row, int cell) {
      }
      public void onRowDeselected(SourceTableSelectionEvents sender, int row) {
      }
      public void onRowHover(SourceTableSelectionEvents sender, int row) {
      }
      public void onRowUnhover(SourceTableSelectionEvents sender, int row) {
      }
      public void onRowsSelected(SourceTableSelectionEvents sender, int firstRow, int numRows) {
        try {
          if (getOnselect() != null && getOnselect().trim().length() > 0) {
            getXulDomContainer().invoke(getOnselect(), new Object[]{});
          }
          Integer[] selectedRows = table.getSelectedRows().toArray(new Integer[table.getSelectedRows().size()]);
          //set.toArray(new Integer[]) doesn't unwrap ><
          int[] rows = new int[selectedRows.length];
          for(int i=0; i<selectedRows.length; i++){
            rows[i] = selectedRows[i];
          }
          GwtTree.this.setSelectedRows(rows);
          if(curSelectedRow > -1){
            Object[] curSelectedRowOriginal = new Object[getColumns().getColumnCount()];

            for (int j = 0; j < getColumns().getColumnCount(); j++) {
              curSelectedRowOriginal[j] = getColumnEditor(j,curSelectedRow);
            }
            table.replaceRow(curSelectedRow, curSelectedRowOriginal);
          }
          curSelectedRow = rows[0];
          Object[] newRow = new Object[getColumns().getColumnCount()];
          
          for (int j = 0; j < getColumns().getColumnCount(); j++) {
            newRow[j] = getColumnEditor(j,rows[0]);
          }
          table.replaceRow(rows[0], newRow);
        } catch (XulException e) {
          e.printStackTrace();
        }
      }
    });

    setWidgetInPanel(table);
    updateUI();
  }

  public void updateUI() {
    if(this.suppressLayout) {
      return;
    }
    if(this.isHierarchical()){
      populateTree();
      for(Binding expBind : expandBindings){
        try {
          expBind.fireSourceChanged();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
//        expandBindings.clear();
    } else {
      populateTable();
    };
  }

  public void afterLayout() {
    updateUI();
  }

  public void clearSelection() {
    this.setSelectedRows(new int[]{});
  }

  public int[] getActiveCellCoordinates() {
    // TODO Auto-generated method stub
    return null;
  }

  public XulTreeCols getColumns() {
    return columns;
  }

  public Object getData() {
    // TODO Auto-generated method stub
    return null;
  }

  @Bindable
  public <T> Collection<T> getElements() {
    return this.elements;
  }

  public String getOnedit() {
    return getAttributeValue("onedit");
  }

  public String getOnselect() {
    return getAttributeValue("onselect");
  }

  public XulTreeChildren getRootChildren() {
    return rootChildren;
  }

  @Bindable
  public int getRows() {
    if (rootChildren == null) {
      return 0;
    } else {
      return rootChildren.getItemCount();
    }
  }

  @Bindable
  public int[] getSelectedRows() {
    if(this.isHierarchical()){
      TreeItem item = tree.getSelectedItem();
      for(int i=0; i <tree.getItemCount(); i++){
        if(tree.getItem(i) == item){
          return new int[]{i};
        }
      }

      return new int[]{};

    } else {
      if (table == null) {
        return new int[0];
      }
      Set<Integer> rows = table.getSelectedRows();
      int rarr[] = new int[rows.size()];
      int i = 0;
      for (Integer v : rows) {
        rarr[i++] = v;
      }
      return rarr;
    }
  }

  public int[] getAbsoluteSelectedRows() {
    return getSelectedRows();
  }

  private int[] selectedRows;
  public void setSelectedRows(int[] rows) {
    if (table == null) {
      // this only works after the table has been materialized
      return;
    }

    int[] prevSelected = selectedRows;
    selectedRows = rows;;

    for (int r : rows) {
      table.selectRow(r);
    }
    if(this.suppressEvents == false && Arrays.equals(selectedRows, prevSelected) == false){
      this.changeSupport.firePropertyChange("selectedRows", prevSelected, rows);
      this.changeSupport.firePropertyChange("absoluteSelectedRows", prevSelected, rows);
    }
  }

  public String getSeltype() {
    return getAttributeValue("seltype");
  }

  public Object[][] getValues() {

    Object[][] data = new Object[getRootChildren().getChildNodes().size()][getColumns().getColumnCount()];
    int y = 0;
    for (XulComponent component : getRootChildren().getChildNodes()) {
      XulTreeItem item = (XulTreeItem)component;
      for (XulComponent childComp : item.getChildNodes()) {
        XulTreeRow row = (XulTreeRow)childComp;
        for (int x = 0; x < getColumns().getColumnCount(); x++) {
          XulTreeCell cell = row.getCell(x);
          switch (columns.getColumn(x).getColumnType()) {
            case CHECKBOX:
              Boolean flag = (Boolean) cell.getValue();
              if (flag == null) {
                flag = Boolean.FALSE;
              }
              data[y][x] = flag;
              break;
            case COMBOBOX:
              Vector values = (Vector) cell.getValue();
              int idx = cell.getSelectedIndex();
              data[y][x] = values.get(idx);
              break;
            default: //label
              data[y][x] = cell.getLabel();
              break;
          }
        }
        y++;
      }

    }
    return data;
  }

  @Bindable
  public boolean isEditable() {
    return editable;
  }

  public boolean isEnableColumnDrag() {
    return false;
  }

  public boolean isHierarchical() {
    return isHierarchical;
  }

  public void removeTreeRows(int[] rows) {
    // sort the rows high to low
    ArrayList<Integer> rowArray = new ArrayList<Integer>();
    for (int i = 0; i < rows.length; i++) {
      rowArray.add(rows[i]);
    }
    Collections.sort(rowArray, Collections.reverseOrder());

    // remove the items in that order
    for (int i = 0; i < rowArray.size(); i++) {
      int item = rowArray.get(i);
      if (item >= 0 && item < rootChildren.getItemCount()) {
        this.rootChildren.removeItem(item);
      }
    }
    updateUI();
  }

  public void setActiveCellCoordinates(int row, int column) {
    // TODO Auto-generated method stub

  }

  public void setColumns(XulTreeCols columns) {
    if (getColumns() != null) {
      this.removeChild(getColumns());
    }
    addChild(columns);
  }

  public void setData(Object data) {
    // TODO Auto-generated method stub

  }

  @Bindable
  public void setEditable(boolean edit) {
    this.editable = edit;
  }

  @Bindable
  public <T> void setElements(Collection<T> elements) {

    try{
      this.elements = elements;
      suppressEvents = true;
      prevSelectionPos = -1;
      this.getRootChildren().removeAll();

      for(Binding b : expandBindings){
        b.destroyBindings();
      }
      this.expandBindings.clear();

      for(TreeItemDropController d : this.dropHandlers){
        XulDragController.getInstance().unregisterDropController(d);
      }
      dropHandlers.clear();


      if(elements == null || elements.size() == 0){
        updateUI();
        return;
      }
      try {
        if(table != null){
          for (T o : elements) {
            XulTreeRow row = this.getRootChildren().addNewRow();

            for (int x=0; x< this.getColumns().getChildNodes().size(); x++) {
              XulComponent col = this.getColumns().getColumn(x);

              XulTreeCol column = ((XulTreeCol) col);
              final XulTreeCell cell = (XulTreeCell) getDocument().createElement("treecell");

              addBindings(column, (GwtTreeCell) cell, o);

              row.addCell(cell);
            }

          }
        } else { //tree

          for (T o : elements) {
            XulTreeRow row = this.getRootChildren().addNewRow();

            ((XulTreeItem) row.getParent()).setBoundObject(o);
            addTreeChild(o, row);
          }

        }
        //treat as a selection change
      } catch (XulException e) {
        Window.alert("error adding elements "+e);
        System.out.println(e.getMessage());
        e.printStackTrace();
      } catch (Exception e) {
        Window.alert("error adding elements "+e);
        System.out.println(e.getMessage());
        e.printStackTrace();
      }

      suppressEvents = false;
      this.clearSelection();
      updateUI();
    } catch(Exception e){
      System.out.println(e.getMessage());
      e.printStackTrace();
    }
  }

  private <T> void addTreeChild(T element, XulTreeRow row){
    try{
      GwtTreeCell cell = (GwtTreeCell) getDocument().createElement("treecell");

      for (InlineBindingExpression exp : ((XulTreeCol) this.getColumns().getChildNodes().get(0)).getBindingExpressions()) {
        Binding binding = createBinding((XulEventSource) element, exp.getModelAttr(), cell, exp.getXulCompAttr());
        binding.setBindingType(Binding.Type.ONE_WAY);
        domContainer.addBinding(binding);
        binding.fireSourceChanged();
      }

      XulTreeCol column = (XulTreeCol) this.getColumns().getChildNodes().get(0);
      String expBind = column.getExpandedbinding();
      if(expBind != null){
        Binding binding = createBinding((XulEventSource) element, expBind, row.getParent(), "expanded");
        elementBindings.add(binding);
        binding.setBindingType(Binding.Type.BI_DIRECTIONAL);
        domContainer.addBinding(binding);
        expandBindings.add(binding);
      }

      String imgBind = column.getImagebinding();
      if(imgBind != null){
        Binding binding = createBinding((XulEventSource) element, imgBind, row.getParent(), "image");
        elementBindings.add(binding);
        binding.setBindingType(Binding.Type.BI_DIRECTIONAL);
        domContainer.addBinding(binding);
        binding.fireSourceChanged();
      }


      row.addCell(cell);


      //find children
      String property = ((XulTreeCol) this.getColumns().getChildNodes().get(0)).getChildrenbinding();

      GwtBindingMethod childrenMethod = GwtBindingContext.typeController.findGetMethod(element, property);
      Collection<T> children = null;
      if(childrenMethod != null){
        children = (Collection<T>) childrenMethod.invoke(element, new Object[] {});
      } else if(element instanceof Collection ){
        children = (Collection<T>) element;
      }

      XulTreeChildren treeChildren = null;

      if(children != null && children.size() > 0){
        treeChildren = (XulTreeChildren) getDocument().createElement("treechildren");
        row.getParent().addChild(treeChildren);
      }
      for(T child : children){
        row = treeChildren.addNewRow();
        ((XulTreeItem) row.getParent()).setBoundObject(child);
        addTreeChild(child, row);
      }
    } catch (Exception e) {
      Window.alert("error adding elements "+e.getMessage());
      e.printStackTrace();
    }
  }

  public void setEnableColumnDrag(boolean drag) {
    // TODO Auto-generated method stub

  }

  public void setOnedit(String onedit) {
    this.setAttribute("onedit", onedit);
  }

  public void setOnselect(String select) {
    this.setAttribute("onselect", select);
  }

  public void setRootChildren(XulTreeChildren rootChildren) {
    if (getRootChildren() != null) {
      this.removeChild(getRootChildren());
    }
    addChild(rootChildren);
  }

  public void setRows(int rows) {
    // TODO Auto-generated method stub

  }



  public void setSeltype(String type) {
    // TODO Auto-generated method stub
    // SINGLE, CELL, MULTIPLE, NONE
    this.setAttribute("seltype", type);
  }

  public void update() {
    layout();
    updateUI();
  }

  public void adoptAttributes(XulComponent component) {
    super.adoptAttributes(component);
    layout();
  }

  public void registerCellEditor(String key, TreeCellEditor editor){
    customEditors.put(key, editor);
  }


  public void registerCellRenderer(String key, TreeCellRenderer renderer) {
    customRenderers.put(key, renderer);

  }

  private void addBindings(final XulTreeCol col, final GwtTreeCell cell, final Object o) throws InvocationTargetException, XulException{
    for (InlineBindingExpression exp : col.getBindingExpressions()) {

      String colType = col.getType();
      if(StringUtils.isEmpty(colType) == false && colType.equalsIgnoreCase("dynamic")){
        colType = extractDynamicColType(o, col.getParent().getChildNodes().indexOf(col));
      }

      if(colType != null && (colType.equalsIgnoreCase("combobox") || colType.equalsIgnoreCase("editablecombobox"))){

        // Only add bindings if they haven't been already applied.
        if(cell.valueBindingsAdded() == false){
          Binding binding = createBinding((XulEventSource) o, col.getCombobinding(), cell, "value");
          binding.setBindingType(Binding.Type.ONE_WAY);
          domContainer.addBinding(binding);
          binding.fireSourceChanged();

          cell.setValueBindingsAdded(true);
        }
        if(cell.selectedIndexBindingsAdded() == false){

          Binding binding = createBinding((XulEventSource) o, ((XulTreeCol) col).getBinding(), cell, "selectedIndex");
          binding.setBindingType(Binding.Type.BI_DIRECTIONAL);
          binding.setConversion(new BindingConvertor<Object, Integer>(){

            @Override
            public Integer sourceToTarget(Object value) {
              int index = ((Vector) cell.getValue()).indexOf(value);
              return index > -1 ? index : 0;
            }

            @Override
            public Object targetToSource(Integer value) {
              return ((Vector)cell.getValue()).get(value);
            }

          });

          domContainer.addBinding(binding);
          binding.fireSourceChanged();
          cell.setSelectedIndexBindingsAdded(true);
        }

        if(cell.labelBindingsAdded() == false && colType.equalsIgnoreCase("editablecombobox")){
          Binding binding = createBinding((XulEventSource) o, exp.getModelAttr(), cell, "label");
          binding.setBindingType(Binding.Type.BI_DIRECTIONAL);
          domContainer.addBinding(binding);
          binding.fireSourceChanged();
          cell.setLabelBindingsAdded(true);
        }


      } else if(colType != null && this.customEditors.containsKey(colType)){

        Binding binding = createBinding((XulEventSource) o, exp.getModelAttr(), cell, "value");
        binding.setBindingType(Binding.Type.BI_DIRECTIONAL);
        domContainer.addBinding(binding);
        binding.fireSourceChanged();

        cell.setValueBindingsAdded(true);
      } else if(colType != null && colType.equalsIgnoreCase("checkbox")) {
        Binding binding = createBinding((XulEventSource) o, exp.getModelAttr(), cell, "value");
        if(col.isEditable() == false){
          binding.setBindingType(Binding.Type.ONE_WAY);
        } else {
          binding.setBindingType(Binding.Type.BI_DIRECTIONAL);
        }
        domContainer.addBinding(binding);
        binding.fireSourceChanged();

        cell.setValueBindingsAdded(true);

      } else if(o instanceof XulEventSource && StringUtils.isEmpty(exp.getModelAttr()) == false){
        Binding binding = createBinding((XulEventSource) o, exp.getModelAttr(), cell, exp.getXulCompAttr());
        if(col.isEditable() == false){
          binding.setBindingType(Binding.Type.ONE_WAY);
        } else {
          binding.setBindingType(Binding.Type.BI_DIRECTIONAL);
        }
        domContainer.addBinding(binding);
        binding.fireSourceChanged();
        cell.setLabelBindingsAdded(true);
      } else {
        cell.setLabel(o.toString());
      }
    }

    if(StringUtils.isEmpty(col.getDisabledbinding()) == false){
      String prop = col.getDisabledbinding();
      Binding bind = createBinding((XulEventSource) o, col.getDisabledbinding(), cell, "disabled");
      bind.setBindingType(Binding.Type.ONE_WAY);
      // the default string to string was causing issues, this is really a boolean to boolean, no conversion necessary
      bind.setConversion(null);
      domContainer.addBinding(bind);
      bind.fireSourceChanged();
    }

  }


  @Deprecated
  public void onResize() {
//    if(table != null){
//      table.onResize();
//    }
  }

  public class CustomCellEditorWrapper extends SimplePanel implements TreeCellEditorCallback{

    private TreeCellEditor editor;
    private TreeCellRenderer renderer;
    private Label label = new Label();
    private XulTreeCell cell;

    public CustomCellEditorWrapper(XulTreeCell cell, TreeCellEditor editor){
      super();
      this.sinkEvents(Event.MOUSEEVENTS);
      this.editor = editor;
      this.cell = cell;
      HorizontalPanel hPanel = new HorizontalPanel();
      hPanel.setStylePrimaryName("slimTable");
      SimplePanel labelWrapper = new SimplePanel();
      labelWrapper.getElement().getStyle().setProperty("overflow", "hidden");
      labelWrapper.setWidth("100%");
      labelWrapper.add(label);

      hPanel.add(labelWrapper);
      hPanel.setCellWidth(labelWrapper, "100%");
      ImageButton btn = new ImageButton(GWT.getModuleBaseURL() + "/images/open_new.png", GWT.getModuleBaseURL() + "/images/open_new.png", "", 29, 24);
      btn.getElement().getStyle().setProperty("margin", "0px");

      hPanel.add(btn);
      hPanel.setSpacing(0);
      hPanel.setBorderWidth(0);
      hPanel.setWidth("100%");
      labelWrapper.getElement().getParentElement().getStyle().setProperty("padding", "0px");
      labelWrapper.getElement().getParentElement().getStyle().setProperty("border", "0px");
      btn.getElement().getParentElement().getStyle().setProperty("padding", "0px");
      btn.getElement().getParentElement().getStyle().setProperty("border", "0px");

      this.add( hPanel );
      if(cell.getValue() != null) {
      this.label.setText((cell.getValue() != null) ? cell.getValue().toString() : " ");
      }
    }

    public CustomCellEditorWrapper(XulTreeCell cell, TreeCellEditor editor, TreeCellRenderer renderer){
      this(cell, editor);
      this.renderer = renderer;

      if(this.renderer.supportsNativeComponent()){
        this.clear();
        this.add((Widget) this.renderer.getNativeComponent());
      } else {
        this.label.setText((this.renderer.getText(cell.getValue()) != null) ? this.renderer.getText(cell.getValue()) : " ");
      }

    }

    public void onCellEditorClosed(Object value) {
      cell.setValue(value);
      if(this.renderer == null){
        this.label.setText(value.toString());
      } else if(this.renderer.supportsNativeComponent()){
        this.clear();
        this.add((Widget) this.renderer.getNativeComponent());
      } else {
        this.label.setText((this.renderer.getText(cell.getValue()) != null) ? this.renderer.getText(cell.getValue()) : " ");
      }

    }

    @Override
    public void onBrowserEvent(Event event) {
      int code = event.getTypeInt();
      switch(code){
        case Event.ONMOUSEUP:
          editor.setValue(cell.getValue());

          int col = cell.getParent().getChildNodes().indexOf(cell);

          XulTreeItem item = (XulTreeItem) cell.getParent().getParent();
          int row = item.getParent().getChildNodes().indexOf(item);

          Object boundObj = (GwtTree.this.getElements() != null) ? GwtTree.this.getElements().toArray()[row] : null;
          String columnBinding = GwtTree.this.getColumns().getColumn(col).getBinding();

          editor.show(row, col, boundObj, columnBinding, this);
        default:
          break;
      }
      super.onBrowserEvent(event);
    }


  }

  public void setBoundObjectExpanded(Object o, boolean expanded) {
    throw new UnsupportedOperationException("not implemented");
  }

  public void setTreeItemExpanded(XulTreeItem item, boolean expanded) {
    ((TreeItem) item.getManagedObject()).setState(expanded);
  }

  public void collapseAll() {
    if (this.isHierarchical()){
      //TODO: Not yet implemented
    }

  }

  public void expandAll() {
    if (this.isHierarchical()){
       //TODO: not implemented yet
    }

  }

  @Bindable
  public <T> Collection<T> getSelectedItems() {
    // TODO Auto-generated method stub
    return null;
  }

  @Bindable
  public <T> void setSelectedItems(Collection<T> items) {
    // TODO Auto-generated method stub

  }

  public boolean isHiddenrootnode() {
    // TODO Auto-generated method stub
    return false;
  }

  public void setHiddenrootnode(boolean hidden) {
    // TODO Auto-generated method stub

  }

  public String getCommand() {
    return command;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public boolean isPreserveexpandedstate() {
    return false;
  }

  public void setPreserveexpandedstate(boolean preserve) {

  }

  public boolean isSortable() {
    // TODO Auto-generated method stub
    return false;
  }

  public void setSortable(boolean sort) {
    // TODO Auto-generated method stub

  }

  public boolean isTreeLines() {
    // TODO Auto-generated method stub
    return false;
  }

  public void setTreeLines(boolean visible) {
    // TODO Auto-generated method stub

  }
  public void setNewitembinding(String binding){
  }

  public String getNewitembinding(){
    return null;
  }

  public void setAutocreatenewrows(boolean auto){
  }

  public boolean getAutocreatenewrows(){
    return false;
  }

  public boolean isPreserveselection() {
    // TODO This method is not fully implemented. We need to completely implement this in this class
    return false;
  }

  public void setPreserveselection(boolean preserve) {
    // TODO This method is not fully implemented. We need to completely implement this in this class

  }


  private void fireSelectedItems( ) {
    Object selected = getSelectedItem();
    this.changeSupport.firePropertyChange("selectedItem", null, selected);
}

  @Bindable
  public Object getSelectedItem() {
    if (this.isHierarchical && this.elements != null) {
      int pos = -1;
      int curPos = 0;
      for(int i=0; i < tree.getItemCount(); i++){
        TreeItem tItem = tree.getItem(i);
        TreeCursor cursor = GwtTree.this.findPosition(tItem, tree.getSelectedItem(), curPos);
        pos = cursor.foundPosition;
        curPos = cursor.curPos+1;
        if(pos > -1){
          break;
        }
      }
      int[] vals = new int[]{pos};

      String property = ((XulTreeCol) this.getColumns().getChildNodes().get(0)).getChildrenbinding();
      FindSelectedItemTuple tuple = findSelectedItem(this.elements, property, new FindSelectedItemTuple(vals[0]));
      return tuple != null ? tuple.selectedItem : null;
    }
    return null;
  }

  private static class FindSelectedItemTuple {
    Object selectedItem = null;
    int curpos = -1; // ignores first element (root)
    int selectedIndex;

    public FindSelectedItemTuple(int selectedIndex) {
      this.selectedIndex = selectedIndex;
    }
  }


  private FindSelectedItemTuple findSelectedItem(Object parent, String childrenMethodProperty,
      FindSelectedItemTuple tuple) {
    if (tuple.curpos == tuple.selectedIndex) {
      tuple.selectedItem = parent;
      return tuple;
    }
    Collection children = getChildCollection(parent, childrenMethodProperty);

    if (children == null || children.size() == 0) {
      return null;
    }

    for (Object child : children) {
      tuple.curpos++;
      findSelectedItem(child, childrenMethodProperty, tuple);
      if (tuple.selectedItem != null) {
        return tuple;
      }
    }
    return null;
  }


  private static Collection getChildCollection(Object obj, String childrenMethodProperty) {
    Collection children = null;
    GwtBindingMethod childrenMethod = GwtBindingContext.typeController.findGetMethod(obj, childrenMethodProperty);

    try {
      if (childrenMethod != null) {
        children = (Collection) childrenMethod.invoke(obj, new Object[] {});
      } else if(obj instanceof Collection ){
        children = (Collection) obj;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }

    return children;
  }


  @Override
  public void setOndrag(String ondrag) {
    super.setOndrag(ondrag);

  }

  @Override
  public void setOndrop(String ondrop) {
    super.setOndrop(ondrop);
  }

  @Override
  public void setDropvetoer(String dropVetoMethod) {
    if(StringUtils.isEmpty(dropVetoMethod)){
      return;
    }
    super.setDropvetoer(dropVetoMethod);
  }
  
  private void resolveDropVetoerMethod(){
    if(dropVetoerMethod == null){

      String id = getDropvetoer().substring(0, getDropvetoer().indexOf("."));
      try {
        XulEventHandler controller = getXulDomContainer().getEventHandler(id);
        this.dropVetoerMethod = GwtBindingContext.typeController.findMethod(controller, getDropvetoer().substring(getDropvetoer().indexOf(".")+1, getDropvetoer().indexOf("(")));
        this.dropVetoerController = controller;
      } catch (XulException e) {
        e.printStackTrace();
      }
    }
    
  }

  private class TreeItemDropController extends AbstractPositioningDropController {
    private XulTreeItem xulItem;
    private TreeItemWidget item;
    private DropPosition curPos;
    private long lasPositionPoll = 0;

    public TreeItemDropController(XulTreeItem xulItem, TreeItemWidget item){
      super(item);
      this.xulItem = xulItem;
      this.item = item;
    }

    @Override
    public void onEnter(DragContext context) {
      super.onEnter(context);
      resolveDropVetoerMethod();
      if(dropVetoerMethod != null){
        Object objToMove = ((XulDragController) context.dragController).getDragObject();

        DropEvent event = new DropEvent();
        DataTransfer dataTransfer = new DataTransfer();
        dataTransfer.setData(Collections.singletonList(objToMove));
        event.setDataTransfer(dataTransfer);
        event.setAccepted(true);
        event.setDropPosition(curPos);
        if(curPos == DropPosition.MIDDLE){
          event.setDropParent(xulItem.getBoundObject());
        } else {

          XulComponent parent = xulItem.getParent().getParent();
          Object parentObj;
          if(parent instanceof GwtTree){
            parentObj = GwtTree.this.elements;
          } else {
            parentObj = ((XulTreeItem) parent).getBoundObject();
          }
          event.setDropParent(parentObj);
        }

        try {
          // Consult Vetoer method to see if this is a valid drop operation
          dropVetoerMethod.invoke(dropVetoerController, new Object[]{event});

          //Check to see if this drop candidate should be rejected.
          if(event.isAccepted() == false){
            return;
          }
        } catch (XulException e) {
          e.printStackTrace();
        }
      }

      ((Draggable) XulDragController.getInstance().getProxy()).setDropValid(true);
    }

    @Override
    public void onLeave(DragContext context) {
      super.onLeave(context);
      item.getElement().getStyle().setProperty("border", "");

      item.highLightDrop(false);
      dropPosIndicator.setVisible(false);
      Widget proxy = XulDragController.getInstance().getProxy();
      if(proxy != null){
        ((Draggable) proxy).setDropValid(false);
      }
    }


    @Override
    public void onMove(DragContext context) {
      super.onMove(context);

      int scrollPanelX = 0;
      int scrollPanelY = 0;
      int scrollPanelScrollTop = 0;
      int scrollPanelScrollLeft = 0;

      if(System.currentTimeMillis() > lasPositionPoll+3000){
        scrollPanelX = scrollPanel.getElement().getAbsoluteLeft();
        scrollPanelScrollLeft = scrollPanel.getElement().getScrollLeft();
        scrollPanelY = scrollPanel.getElement().getAbsoluteTop();
        scrollPanelScrollTop = scrollPanel.getElement().getScrollTop();
      }
      int x = item.getAbsoluteLeft();
      int y = item.getAbsoluteTop();
      int height = item.getOffsetHeight();
      int middleGround = height/4;

      if(context.mouseY < (y+height/2)-middleGround){
        curPos = DropPosition.ABOVE;
      } else if(context.mouseY > (y+height/2)+middleGround){
        curPos = DropPosition.BELOW;
      } else {
        curPos = DropPosition.MIDDLE;
      }

      resolveDropVetoerMethod();
      if(dropVetoerMethod != null){
        Object objToMove = ((XulDragController) context.dragController).getDragObject();

        DropEvent event = new DropEvent();
        DataTransfer dataTransfer = new DataTransfer();
        dataTransfer.setData(Collections.singletonList(objToMove));
        event.setDataTransfer(dataTransfer);
        event.setAccepted(true);
        event.setDropPosition(curPos);
        if(curPos == DropPosition.MIDDLE){
          event.setDropParent(xulItem.getBoundObject());
        } else {

          XulComponent parent = xulItem.getParent().getParent();
          Object parentObj;
          if(parent instanceof GwtTree){
            parentObj = GwtTree.this.elements;
          } else {
            parentObj = ((XulTreeItem) parent).getBoundObject();
          }
          event.setDropParent(parentObj);
        }
        
        try {
          // Consult Vetoer method to see if this is a valid drop operation
          dropVetoerMethod.invoke(dropVetoerController, new Object[]{event});

          //Check to see if this drop candidate should be rejected.
          if(event.isAccepted() == false){

            ((Draggable) XulDragController.getInstance().getProxy()).setDropValid(false);
            dropPosIndicator.setVisible(false);
            return;
          }
        } catch (XulException e) {
          e.printStackTrace();
        }
      }

      ((Draggable) XulDragController.getInstance().getProxy()).setDropValid(true);

      switch (curPos) {
        case ABOVE:
          item.highLightDrop(false);

          int posX = item.getElement().getAbsoluteLeft()
              - scrollPanelX
              + scrollPanelScrollLeft;

          int posY = item.getElement().getAbsoluteTop()
              - scrollPanelY
              + scrollPanelScrollTop
              -4;
          dropPosIndicator.setPosition(posX, posY, item.getOffsetWidth());
          break;
        case MIDDLE:
          item.highLightDrop(true);
          dropPosIndicator.setVisible(false);
          break;
        case BELOW:
          item.highLightDrop(false);

          posX = item.getElement().getAbsoluteLeft()
              - scrollPanelX
              + scrollPanelScrollLeft;

          posY = item.getElement().getAbsoluteTop()
              + item.getElement().getOffsetHeight()
              - scrollPanelY
              + scrollPanelScrollTop
              -4;

          dropPosIndicator.setPosition(posX, posY, item.getOffsetWidth());
          
          break;
      }

    }

    @Override
    public void onPreviewDrop(DragContext context) throws VetoDragException {
      int i=0;
    }

    @Override
    public void onDrop(DragContext context) {
      super.onDrop(context);


      Object objToMove = ((XulDragController) context.dragController).getDragObject();

      DropEvent event = new DropEvent();
        DataTransfer dataTransfer = new DataTransfer();
        dataTransfer.setData(Collections.singletonList(objToMove));
        event.setDataTransfer(dataTransfer);
        event.setAccepted(true);

        if(curPos == DropPosition.MIDDLE){
          event.setDropParent(xulItem.getBoundObject());
        } else {

          XulComponent parent = xulItem.getParent().getParent();
          Object parentObj;
          if(parent instanceof GwtTree){
            parentObj = GwtTree.this.elements;
          } else {
            parentObj = ((XulTreeItem) parent).getBoundObject();
          }
          event.setDropParent(parentObj);
        }
        //event.setDropIndex(index);

        final String method = getOndrop();
        if (method != null) {
          try{
            Document doc = getDocument();
            XulRoot window = (XulRoot) doc.getRootElement();
            final XulDomContainer con = window.getXulDomContainer();
            con.invoke(method, new Object[]{event});
          } catch (XulException e){
            e.printStackTrace();
            System.out.println("Error calling ondrop event: "+ method); //$NON-NLS-1$
          }
        }

        if (!event.isAccepted()) {
          return;
        }

      // ==============


      ((Draggable) context.draggable).notifyDragFinished();

      if(curPos == DropPosition.MIDDLE){
        String property = ((XulTreeCol) GwtTree.this.getColumns().getChildNodes().get(0)).getChildrenbinding();
        GwtBindingMethod childrenMethod = GwtBindingContext.typeController.findGetMethod(xulItem.getBoundObject(), property);
        Collection children = null;
        try {
          if(childrenMethod != null){
            children = (Collection) childrenMethod.invoke(xulItem.getBoundObject(), new Object[] {});
          } else if(xulItem.getBoundObject() instanceof Collection ){
            children = (Collection) xulItem.getBoundObject();
          }
          for(Object o : event.getDataTransfer().getData()){
            children.add(o);
          }
        } catch (XulException e) {
          e.printStackTrace();
        }
      } else {
        XulComponent parent = xulItem.getParent().getParent();
        List parentList;
        if(parent instanceof GwtTree){
          parentList = (List) GwtTree.this.elements;
        } else {
          parentList = (List) ((XulTreeItem) parent).getBoundObject();
        }
        int idx = parentList.indexOf(xulItem.getBoundObject());

        if (curPos == DropPosition.BELOW){
          idx++;
        }

        for(Object o : event.getDataTransfer().getData()){
          parentList.add(idx, o);
        }

      }
    }
  }

  public void notifyDragFinished(XulTreeItem xulItem){
    if(this.drageffect.equalsIgnoreCase("copy")){
      return;
    }
    XulComponent parent = xulItem.getParent().getParent();
    List parentList;
    if(parent instanceof GwtTree){
      parentList = (List) GwtTree.this.elements;
    } else {
      parentList = (List) ((XulTreeItem) parent).getBoundObject();
    }
    parentList.remove(xulItem.getBoundObject());

  }

  private class DropPositionIndicator extends SimplePanel{
    public DropPositionIndicator(){
      setStylePrimaryName("tree-drop-indicator-symbol");
      SimplePanel line = new SimplePanel();
      line.setStylePrimaryName("tree-drop-indicator");
      add(line);
      setVisible(false);
    }

    public void setPosition(int scrollLeft, int scrollTop, int offsetWidth) {
      this.setVisible(true);
      setWidth(offsetWidth+"px");
      getElement().getStyle().setProperty("top", scrollTop+"px");
      getElement().getStyle().setProperty("left", (scrollLeft -4)+"px");
    }
  }
}
