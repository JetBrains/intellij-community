// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import com.google.common.base.MoreObjects;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.InternalActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.StripeTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.tree.TreeUtil;
import net.miginfocom.layout.*;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.table.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.internal.inspector.UiInspectorUtil.collectAnActionInfo;
import static com.intellij.openapi.actionSystem.ex.CustomComponentAction.ACTION_KEY;

public class UiInspectorAction extends ToggleAction implements DumbAware {
  private static final String CLICK_INFO = "CLICK_INFO";
  private static final String CLICK_INFO_POINT = "CLICK_INFO_POINT";
  private static final String RENDERER_BOUNDS = "clicked renderer";
  private static final int MAX_DEEPNESS_TO_DISCOVER_FIELD_NAME = 8;
  private UiInspector myInspector;

  public UiInspectorAction() {
    if (Boolean.getBoolean("idea.ui.debug.mode")) {
      ApplicationManager.getApplication().invokeLater(() -> setSelected(true));
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myInspector != null;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setSelected(state);
  }

  void setSelected(boolean state) {
    if (state) {
      if (myInspector == null) {
        myInspector = new UiInspector();
      }

      UiInspectorNotification[] existing =
        NotificationsManager.getNotificationsManager().getNotificationsOfType(UiInspectorNotification.class, null);
      if (existing.length == 0 && !Boolean.getBoolean("idea.ui.debug.mode")) {
        Notifications.Bus.notify(new UiInspectorNotification(), null);
      }
    }
    else {
      UiInspector inspector = myInspector;
      myInspector = null;
      if (inspector != null) {
        Disposer.dispose(inspector);
      }
    }
  }

  private static class UiInspectorNotification extends Notification {
    private UiInspectorNotification() {
      super(Notifications.SYSTEM_MESSAGES_GROUP_ID, "UI Inspector", "Control-Alt-Click to view component info!",
            NotificationType.INFORMATION);
    }
  }

  private static class InspectorWindow extends JDialog {
    private InspectorTable myInspectorTable;
    @NotNull private final List<Component> myComponents = new ArrayList<>();
    private List<? extends PropertyBean> myInfo;
    @NotNull private final Component myInitialComponent;
    @NotNull private final List<HighlightComponent> myHighlightComponents = new ArrayList<>();
    private boolean myIsHighlighted = true;
    @NotNull private final HierarchyTree myHierarchyTree;
    @NotNull private final Wrapper myWrapperPanel;

    private InspectorWindow(@NotNull Component component) throws HeadlessException {
      super(findWindow(component));
      Window window = findWindow(component);
      setModal(window instanceof JDialog && ((JDialog)window).isModal());
      myComponents.add(component);
      myInitialComponent = component;
      getRootPane().setBorder(JBUI.Borders.empty(5));

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      setLayout(new BorderLayout());
      setTitle(component.getClass().getName());
      Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey());
      Point location = DimensionService.getInstance().getLocation(getDimensionServiceKey());
      if (size != null) setSize(size);
      if (location != null) setLocation(location);

      DefaultActionGroup actions = new DefaultActionGroup();
      actions.addAction(new MyTextAction(IdeBundle.messagePointer("action.Anonymous.text.highlight")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          myIsHighlighted = !myIsHighlighted;
          updateHighlighting();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(myInfo != null || !myComponents.isEmpty());
        }

      });

      actions.addSeparator();

      actions.add(new MyTextAction(InternalActionsBundle.messagePointer("action.Anonymous.text.refresh")) {

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          getCurrentTable().refresh();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(!myComponents.isEmpty());
        }
      });

      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CONTEXT_TOOLBAR, actions, true);
      add(toolbar.getComponent(), BorderLayout.NORTH);

      myWrapperPanel = new Wrapper();

      myInspectorTable = new InspectorTable(component);
      myHierarchyTree = new HierarchyTree(component) {
        @Override
        public void onComponentsChanged(List<Component> components) {
          switchComponentsInfo(components);
          updateHighlighting();
        }

        @Override
        public void onClickInfoChanged(List<? extends PropertyBean> info) {
          switchClickInfo(info);
          updateHighlighting();
        }
      };

      myWrapperPanel.setContent(myInspectorTable);

      Splitter splitPane = new JBSplitter(false, "UiInspector.splitter.proportion", 0.5f);
      splitPane.setSecondComponent(myWrapperPanel);
      splitPane.setFirstComponent(new JBScrollPane(myHierarchyTree));
      add(splitPane, BorderLayout.CENTER);

      myHierarchyTree.expandPath();

      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          close();
        }
      });

      getRootPane().getActionMap().put("CLOSE", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          close();
        }
      });
      updateHighlighting();
      getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "CLOSE");
    }

    private static String getDimensionServiceKey() {
      return "UiInspectorWindow";
    }

    private static Window findWindow(Component component) {
      DialogWrapper dialogWrapper = DialogWrapper.findInstance(component);
      if (dialogWrapper != null) {
        return dialogWrapper.getPeer().getWindow();
      }
      return null;
    }

    private InspectorTable getCurrentTable() {
      return myInspectorTable;
    }

    private void switchComponentsInfo(@NotNull List<Component> components) {
      if (components.isEmpty()) return;
      myComponents.clear();
      myComponents.addAll(components);
      myInfo = null;
      setTitle(components.get(0).getClass().getName());
      myInspectorTable = new InspectorTable(components.get(0));
      myWrapperPanel.setContent(myInspectorTable);
    }

    private void switchClickInfo(@NotNull List<? extends PropertyBean> clickInfo) {
      myComponents.clear();
      myInfo = clickInfo;
      setTitle("Click Info");
      myInspectorTable = new InspectorTable(clickInfo);
      myWrapperPanel.setContent(myInspectorTable);
    }

    @Override
    public void dispose() {
      DimensionService.getInstance().setSize(getDimensionServiceKey(), getSize());
      DimensionService.getInstance().setLocation(getDimensionServiceKey(), getLocation());
      super.dispose();
      DialogWrapper.cleanupRootPane(rootPane);
      DialogWrapper.cleanupWindowListeners(this);
    }

    public void close() {
      if (myInitialComponent instanceof JComponent) {
        ((JComponent)myInitialComponent).putClientProperty(CLICK_INFO, null);
      }
      myIsHighlighted = false;
      myInfo = null;
      myComponents.clear();
      updateHighlighting();
      setVisible(false);
      dispose();
    }

    private void updateHighlighting() {
      for (HighlightComponent component : myHighlightComponents) {
        JComponent glassPane = getGlassPane(component);
        if (glassPane != null) {
          glassPane.remove(component);
          glassPane.revalidate();
          glassPane.repaint();
        }
      }
      myHighlightComponents.clear();

      if (myIsHighlighted) {
        for (Component component : myComponents) {
          ContainerUtil.addIfNotNull(myHighlightComponents, createHighlighter(component, null));
        }
        if (myInfo != null) {
          Rectangle bounds = null;
          for (PropertyBean bean : myInfo) {
            if (RENDERER_BOUNDS.equals(bean.propertyName)) {
              bounds = (Rectangle)bean.propertyValue;
              break;
            }
          }
          ContainerUtil.addIfNotNull(myHighlightComponents, createHighlighter(myInitialComponent, bounds));
        }
      }
    }

    @Nullable
    private static HighlightComponent createHighlighter(@NotNull Component component, @Nullable Rectangle bounds) {
      JComponent glassPane = getGlassPane(component);
      if (glassPane == null) return null;

      if (bounds != null) {
        bounds = SwingUtilities.convertRectangle(component, bounds, glassPane);
      }
      else {
        Point pt = SwingUtilities.convertPoint(component, new Point(0, 0), glassPane);
        bounds = new Rectangle(pt.x, pt.y, component.getWidth(), component.getHeight());
      }

      JBColor color = new JBColor(JBColor.GREEN, JBColor.RED);
      if (bounds.width == 0 || bounds.height == 0) {
        bounds.width = Math.max(bounds.width, 1);
        bounds.height = Math.max(bounds.height, 1);
        color = JBColor.BLUE;
      }

      Insets insets = component instanceof JComponent ? ((JComponent)component).getInsets() : JBUI.emptyInsets();
      HighlightComponent highlightComponent = new HighlightComponent(color, insets);
      highlightComponent.setBounds(bounds);

      glassPane.add(highlightComponent);
      glassPane.revalidate();
      glassPane.repaint();

      return highlightComponent;
    }

    @Nullable
    private static JComponent getGlassPane(@NotNull Component component) {
      JRootPane rootPane = SwingUtilities.getRootPane(component);
      return rootPane == null ? null : (JComponent)rootPane.getGlassPane();
    }

    private abstract static class MyTextAction extends IconWithTextAction implements DumbAware {
      private MyTextAction(Supplier<String> text) {
        super(text);
      }
    }
  }

  private static class ComponentTreeCellRenderer extends ColoredTreeCellRenderer {
    private final Component myInitialSelection;

    ComponentTreeCellRenderer(Component initialSelection) {
      myInitialSelection = initialSelection;
      setFont(JBUI.Fonts.label(11));
      setBorder(JBUI.Borders.empty(0, 3));
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      Color foreground = UIUtil.getTreeForeground(selected, hasFocus);
      Color background = selected ? UIUtil.getTreeSelectionBackground(hasFocus) : null;
      if (value instanceof HierarchyTree.ComponentNode) {
        HierarchyTree.ComponentNode componentNode = (HierarchyTree.ComponentNode)value;
        Component component = componentNode.getComponent();

        if (!selected) {
          if (!component.isVisible()) {
            foreground = JBColor.GRAY;
          }
          else if (component.getWidth() == 0 || component.getHeight() == 0) {
            foreground = new JBColor(new Color(128, 10, 0), JBColor.BLUE);
          }
          else if (component.getPreferredSize() != null &&
                   (component.getSize().width < component.getPreferredSize().width
                    || component.getSize().height < component.getPreferredSize().height)) {
            foreground = PlatformColors.BLUE;
          }

          if (myInitialSelection == componentNode.getComponent()) {
            background = new Color(31, 128, 8, 58);
          }
        }
        append(getComponentName(component));
        Pair<Class, String> class2field = getClassAndFieldName(component);
        if (class2field!= null) {
          append("(" + class2field.second + "@" + class2field.first.getSimpleName() + ")");
        }

        append(": " + RectangleRenderer.toString(component.getBounds()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        if (component.isOpaque()) {
          append(", opaque", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        if (component.isDoubleBuffered()) {
          append(", double-buffered", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        if (DataManagerImpl.getDataProviderEx(component) != null) {
          append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          append("data-provider", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        componentNode.setText(toString());
        setIcon(createColorIcon(component.getBackground(), component.getForeground()));
      }
      if (value instanceof HierarchyTree.ClickInfoNode) {
        append(value.toString());
        setIcon(AllIcons.Ide.Rating);
      }

      setForeground(foreground);
      setBackground(background);

      SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, false, selected);
    }
  }

  @NotNull
  private static String getComponentName(Component component) {
    String name = getClassName(component);

    String componentName = component.getName();
    if (StringUtil.isNotEmpty(componentName)) {
      name += " \"" + componentName + "\"";
    }
    return name;
  }

  @Nullable
  private static Pair<Class, String> getClassAndFieldName(Component component) {
    Container parent = component.getParent();
    int deepness = 1;
    while(parent != null && deepness <= MAX_DEEPNESS_TO_DISCOVER_FIELD_NAME) {
      Class<? extends Container> aClass = parent.getClass();
      Field[] fields = aClass.getDeclaredFields();
      for (Field field : fields) {
        try {
          field.setAccessible(true);
          if (field.get(parent) == component) {
            return Pair.create(parent.getClass(), field.getName());
          }
        }
        catch (IllegalAccessException e) {
          //skip
        }
      }
      parent = parent.getParent();
      deepness++;
    }
    return null;
  }

  private static TreeModel buildModel(Component c) {
    Component parent = c.getParent();
    while (parent != null) {
      c = parent;
      parent = c.getParent();//Find root window
    }
    return new DefaultTreeModel(new UiInspectorAction.HierarchyTree.ComponentNode(c));
  }


  private abstract static class HierarchyTree extends JTree implements TreeSelectionListener {
    final Component myComponent;

    private HierarchyTree(Component c) {
      myComponent = c;
      setModel(buildModel(c));
      setCellRenderer(new ComponentTreeCellRenderer(c));
      getSelectionModel().addTreeSelectionListener(this);
      new TreeSpeedSearch(this);
      if (c instanceof JComponent && ((JComponent)c).getClientProperty(CLICK_INFO) != null) {
        SwingUtilities.invokeLater(() -> getSelectionModel().setSelectionPath(getPathForRow(getLeadSelectionRow() + 1)));
      }
    }

    @Override
    public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof ComponentNode) {
        Pair<Class, String> pair = getClassAndFieldName(((HierarchyTree.ComponentNode)value).myComponent);
        if (pair != null) {
          return pair.first.getSimpleName() + '.' + pair.second;
        } else {
          return myComponent.getClass().getName();
        }
      }
      return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);//todo
    }

    public void expandPath() {
      TreeUtil.expandAll(this);
      int count = getRowCount();
      ComponentNode node = new ComponentNode(myComponent);

      for (int i = 0; i < count; i++) {
        TreePath row = getPathForRow(i);
        if (row.getLastPathComponent().equals(node)) {
          setSelectionPath(row);
          scrollPathToVisible(getSelectionPath());
          break;
        }
      }
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
      TreePath[] paths = getSelectionPaths();
      if (paths == null) {
        onComponentsChanged(Collections.emptyList());
        return;
      }

      List<List<PropertyBean>> clickInfos = ContainerUtil.mapNotNull(paths, path -> {
        Object node = path.getLastPathComponent();
        if (node instanceof ClickInfoNode) return ((ClickInfoNode)node).getInfo();
        return null;
      });
      if (!clickInfos.isEmpty()) {
        onClickInfoChanged(clickInfos.get(0));
        return;
      }

      List<Component> components = ContainerUtil.mapNotNull(paths, path -> {
        Object node = path.getLastPathComponent();
        if (node instanceof ComponentNode) return ((ComponentNode)node).getComponent();
        return null;
      });
      onComponentsChanged(components);
    }

    public abstract void onClickInfoChanged(List<? extends PropertyBean> info);

    public abstract void onComponentsChanged(List<Component> components);

    private static class ComponentNode extends DefaultMutableTreeNode  {
      private final Component myComponent;
      String myText;

      private ComponentNode(@NotNull Component component) {
        super(component);
        myComponent = component;
        children = prepareChildren(myComponent);
      }

      Component getComponent() {
        return myComponent;
      }

      @Override
      public String toString() {
        return myText != null ? myText : myComponent.getClass().getName();
      }

      public void setText(String value) {
        myText = value;
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof ComponentNode && ((ComponentNode)obj).getComponent() == getComponent();
      }

      @SuppressWarnings("UseOfObsoleteCollectionType")
      private static Vector prepareChildren(Component parent) {
        Vector<DefaultMutableTreeNode> result = new Vector<>();
        if (parent instanceof JComponent) {
          Object o = ((JComponent)parent).getClientProperty(CLICK_INFO);
          if (o instanceof List) {
            //noinspection unchecked
            result.add(new ClickInfoNode((List<PropertyBean>)o));
          }
        }
        if (parent instanceof Container) {
          for (Component component : ((Container)parent).getComponents()) {
            result.add(new ComponentNode(component));
          }
        }
        if (parent instanceof Window) {
          Window[] children = ((Window)parent).getOwnedWindows();
          for (Window child : children) {
            if (child instanceof InspectorWindow) continue;
            result.add(new ComponentNode(child));
          }
        }

        return result;
      }
    }

    private static class ClickInfoNode extends DefaultMutableTreeNode {
      private final List<PropertyBean> myInfo;

      ClickInfoNode(List<PropertyBean> info) {
        myInfo = info;
      }

      @Override
      public String toString() {
        return "Clicked Info";
      }

      public List<PropertyBean> getInfo() {
        return myInfo;
      }

      @Override
      public boolean isLeaf() {
        return true;
      }
    }
  }

  private static class HighlightComponent extends JComponent {
    @NotNull private final Color myColor;
    @NotNull private final Insets myInsets;

    private HighlightComponent(@NotNull Color c, @NotNull Insets insets) {
      myColor = c;
      myInsets = insets;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;

      Color oldColor = g2d.getColor();
      Composite old = g2d.getComposite();
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

      Rectangle r = getBounds();
      RectanglePainter.paint(g2d, 0, 0, r.width, r.height, 0, myColor, null);

      ((Graphics2D)g).setPaint(myColor.darker());
      for (int i = 0; i < myInsets.left; i++) {
        LinePainter2D.paint(g2d, i, myInsets.top, i, r.height - myInsets.bottom - 1);
      }
      for (int i = 0; i < myInsets.right; i++) {
        LinePainter2D.paint(g2d, r.width - i - 1, myInsets.top, r.width - i - 1, r.height - myInsets.bottom - 1);
      }
      for (int i = 0; i < myInsets.top; i++) {
        LinePainter2D.paint(g2d, 0, i, r.width, i);
      }
      for (int i = 0; i < myInsets.bottom; i++) {
        LinePainter2D.paint(g2d, 0, r.height - i - 1, r.width, r.height - i - 1);
      }

      g2d.setComposite(old);
      g2d.setColor(oldColor);
    }
  }

  private static class InspectorTable extends JPanel {
    InspectorTableModel myModel;
    DimensionsComponent myDimensionComponent;

    private InspectorTable(@NotNull final List<? extends PropertyBean> clickInfo) {
       myModel = new InspectorTableModel(clickInfo);
       init(null);
    }
    private InspectorTable(@NotNull final Component component) {

      myModel = new InspectorTableModel(component);
      init(component);
    }

    private void init(@Nullable Component component) {
      setLayout(new BorderLayout());
      StripeTable table = new StripeTable(myModel);
      new TableSpeedSearch(table);

      TableColumnModel columnModel = table.getColumnModel();
      TableColumn propertyColumn = columnModel.getColumn(0);
      propertyColumn.setMinWidth(JBUIScale.scale(220));
      propertyColumn.setMaxWidth(JBUIScale.scale(220));
      propertyColumn.setResizable(false);
      propertyColumn.setCellRenderer(new PropertyNameRenderer());

      TableColumn valueColumn = columnModel.getColumn(1);
      valueColumn.setMinWidth(JBUIScale.scale(200));
      valueColumn.setResizable(false);
      valueColumn.setCellRenderer(new ValueCellRenderer());
      valueColumn.setCellEditor(new DefaultCellEditor(new JBTextField()) {
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          Component comp = table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, false, false, row, column);
          if (comp instanceof JLabel) {
            value = ((JLabel)comp).getText();
          }
          Component result = super.getTableCellEditorComponent(table, value, isSelected, row, column);
          ((JComponent)result).setBorder(BorderFactory.createLineBorder(JBColor.GRAY, 1));
          return result;
        }
      });
      new DoubleClickListener(){
        @Override
        protected boolean onDoubleClick(@NotNull MouseEvent event) {
          int row = table.rowAtPoint(event.getPoint());
          int column = table.columnAtPoint(event.getPoint());
          if (row >=0 && row < table.getRowCount() && column >=0 && column < table.getColumnCount()) {
            Component renderer = table.getCellRenderer(row, column)
                                        .getTableCellRendererComponent(table, myModel.getValueAt(row, column), false, false, row, column);
            if (renderer instanceof JLabel) {
              //noinspection UseOfSystemOutOrSystemErr
              System.out.println((component != null ? getComponentName(component)+ " " : "" )
                                 + ((JLabel)renderer).getText().replace("\r", "").replace("\tat", "\n\tat"));
              return true;
            }
          }
          return false;
        }
      }.installOn(table);

      table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

      add(new JBScrollPane(table), BorderLayout.CENTER);
      if (component != null) {
        myDimensionComponent = new DimensionsComponent(component);
        add(myDimensionComponent, BorderLayout.SOUTH);
      }
    }

    public void refresh() {
      myModel.refresh();
      myDimensionComponent.update();
      myDimensionComponent.repaint();
    }

    private static class PropertyNameRenderer extends DefaultTableCellRenderer {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        final TableModel model = table.getModel();
        boolean changed = false;
        if (model instanceof InspectorTableModel) {
          changed = ((InspectorTableModel)model).myProperties.get(row).changed;
        }

        final Color fg = isSelected ? table.getSelectionForeground() : changed ? JBUI.CurrentTheme.Link.linkColor() : table.getForeground();
        final JBFont font = JBFont.label();
        setFont(changed ? font.asBold() : font);
        setForeground(fg);
        return this;
      }
    }
  }

  private static class DimensionsComponent extends JComponent {
    Component myComponent;
    int myWidth;
    int myHeight;
    Border myBorder;
    Insets myInsets;

    private DimensionsComponent(@NotNull final Component component) {
      myComponent = component;
      setOpaque(true);
      setBackground(JBColor.WHITE);
      setBorder(JBUI.Borders.empty(5, 0));

      setFont(JBUI.Fonts.label(9));

      update();
    }

    public void update() {
      myWidth = myComponent.getWidth();
      myHeight = myComponent.getHeight();
      if (myComponent instanceof JComponent) {
        myBorder = ((JComponent)myComponent).getBorder();
        myInsets = ((JComponent)myComponent).getInsets();
      }
    }

    @Override
    protected void paintComponent(final Graphics g) {
      Graphics2D g2d = (Graphics2D)g;
      GraphicsConfig config = new GraphicsConfig(g).setAntialiasing(UISettings.getShadowInstance().getIdeAAType() != AntialiasingType.OFF);
      Rectangle bounds = getBounds();

      g2d.setColor(getBackground());
      Insets insets = getInsets();
      g2d.fillRect(insets.left, insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);

      final String sizeString = String.format("%d x %d", myWidth, myHeight);

      FontMetrics fm = g2d.getFontMetrics();
      int sizeWidth = fm.stringWidth(sizeString);
      int fontHeight = fm.getHeight();

      int innerBoxWidthGap = JBUIScale.scale(20);
      int innerBoxHeightGap = JBUIScale.scale(5);
      int boxSize = JBUIScale.scale(15);

      int centerX = bounds.width / 2;
      int centerY = bounds.height / 2;
      int innerX = centerX - sizeWidth / 2 - innerBoxWidthGap;
      int innerY = centerY - fontHeight / 2 - innerBoxHeightGap;
      int innerWidth = sizeWidth + innerBoxWidthGap * 2;
      int innerHeight = fontHeight + innerBoxHeightGap * 2;

      g2d.setColor(getForeground());
      drawCenteredString(g2d, fm, fontHeight, sizeString, centerX, centerY);

      g2d.setColor(JBColor.GRAY);
      g2d.drawRect(innerX, innerY, innerWidth, innerHeight);

      Insets borderInsets = null;
      if (myBorder != null) borderInsets = myBorder.getBorderInsets(myComponent);
      UIUtil.drawDottedRectangle(g2d, innerX - boxSize, innerY - boxSize, innerX + innerWidth + boxSize, innerY + innerHeight + boxSize);
      drawInsets(g2d, fm, "border", borderInsets, boxSize, fontHeight, innerX, innerY, innerWidth, innerHeight);

      g2d.drawRect(innerX - boxSize * 2, innerY - boxSize * 2, innerWidth + boxSize * 4, innerHeight + boxSize * 4);
      drawInsets(g2d, fm, "insets", myInsets, boxSize * 2, fontHeight, innerX, innerY, innerWidth, innerHeight);

      config.restore();
    }

    private static void drawInsets(Graphics2D g2d, FontMetrics fm, String name, Insets insets, int offset, int fontHeight, int innerX, int innerY, int innerWidth, int innerHeight) {
      g2d.setColor(JBColor.BLACK);
      g2d.drawString(name, innerX - offset + JBUIScale.scale(5), innerY - offset + fontHeight);

      g2d.setColor(JBColor.GRAY);

      int outerX = innerX - offset;
      int outerWidth = innerWidth + offset * 2;
      int outerY = innerY - offset;
      int outerHeight = innerHeight + offset * 2;

      final String top = insets != null ? Integer.toString(insets.top) : "-";
      final String bottom = insets != null ? Integer.toString(insets.bottom) : "-";
      final String left = insets != null ? Integer.toString(insets.left) : "-";
      final String right = insets != null ? Integer.toString(insets.right) : "-";

      int shift = JBUIScale.scale(7);
      drawCenteredString(g2d, fm, fontHeight, top,
                         outerX + outerWidth / 2,
                         outerY + shift);
      drawCenteredString(g2d, fm, fontHeight, bottom,
                         outerX + outerWidth / 2,
                         outerY + outerHeight - shift);
      drawCenteredString(g2d, fm, fontHeight, left,
                         outerX + shift,
                         outerY + outerHeight / 2);
      drawCenteredString(g2d, fm, fontHeight, right,
                         outerX + outerWidth - shift,
                         outerY + outerHeight / 2);
    }

    @Override
    public Dimension getMinimumSize() {
      return JBUI.size(120);
    }

    @Override
    public Dimension getPreferredSize() {
      return JBUI.size(150);
    }
  }

  private static void drawCenteredString(Graphics2D g2d, FontMetrics fm, int fontHeight, String text, int x, int y) {
    int width = fm.stringWidth(text);
    UIUtil.drawCenteredString(g2d, new Rectangle(x - width / 2, y - fontHeight / 2, width, fontHeight), text);
  }

  private static class ValueCellRenderer implements TableCellRenderer {
    private static final Map<Class, Renderer> RENDERERS = new HashMap<>();

    static {
      RENDERERS.put(Point.class, new PointRenderer());
      RENDERERS.put(Dimension.class, new DimensionRenderer());
      RENDERERS.put(Insets.class, new InsetsRenderer());
      RENDERERS.put(Rectangle.class, new RectangleRenderer());
      RENDERERS.put(Color.class, new ColorRenderer());
      RENDERERS.put(Font.class, new FontRenderer());
      RENDERERS.put(Boolean.class, new BooleanRenderer());
      RENDERERS.put(Icon.class, new IconRenderer());
      RENDERERS.put(Border.class, new BorderRenderer());
    }

    private static final Renderer<Object> DEFAULT_RENDERER = new ObjectRenderer();

    private static final JLabel NULL_RENDERER = new JLabel("-");

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value == null) {
        NULL_RENDERER.setOpaque(isSelected);
        NULL_RENDERER.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        NULL_RENDERER.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return NULL_RENDERER;
      }

      Renderer<Object> renderer = ObjectUtils.notNull(getRenderer(value.getClass()), DEFAULT_RENDERER);

      renderer.setOpaque(isSelected);
      renderer.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
      renderer.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      renderer.setValue(value);
      return renderer;
    }

    @Nullable
    private static Renderer<Object> getRenderer(Class clazz) {
      if (clazz == null) return null;

      Renderer<Object> renderer = (Renderer<Object>)RENDERERS.get(clazz);
      if (renderer != null) return renderer;

      Class[] interfaces = clazz.getInterfaces();
      for (Class aClass : interfaces) {
        renderer = getRenderer(aClass);
        if (renderer != null) {
          return renderer;
        }
      }
      clazz = clazz.getSuperclass();
      if (clazz != null) {
        return getRenderer(clazz);
      }
      return null;
    }
  }

  private static abstract class Renderer<T> extends JLabel{
    abstract void setValue(@NotNull T value);
  }

  private static class PointRenderer extends Renderer<Point> {
    @Override
    public void setValue(@NotNull final Point value) {
      setText(String.valueOf(value.x) + ':' + value.y);
    }
  }

  private static class DimensionRenderer extends Renderer<Dimension> {
    @Override
    public void setValue(@NotNull final Dimension value) {
      setText(value.width + "x" + value.height);
    }
  }

  private static class InsetsRenderer extends Renderer<Insets> {
    @Override
    public void setValue(@NotNull final Insets value) {
      setText("top: " + value.top + " left:" + value.left + " bottom:" + value.bottom + " right:" + value.right);
    }
  }

  private static class RectangleRenderer extends Renderer<Rectangle> {
    @Override
    public void setValue(@NotNull final Rectangle value) {
      setText(toString(value));
    }

    @NotNull
    static String toString(@NotNull Rectangle r) {
      return r.width + "x" + r.height + " @ " + r.x + ":" + r.y;
    }
  }

  private static class ColorRenderer extends Renderer<Color> {
    @Override
    public void setValue(@NotNull final Color value) {
      StringBuilder sb = new StringBuilder();
      sb.append(" r:").append(value.getRed());
      sb.append(" g:").append(value.getGreen());
      sb.append(" b:").append(value.getBlue());
      sb.append(" a:").append(value.getAlpha());

      sb.append(" argb:0x");
      String hex = Integer.toHexString(value.getRGB());
      for (int i = hex.length(); i < 8; i++) sb.append('0');
      sb.append(StringUtil.toUpperCase(hex));

      if (value instanceof UIResource) sb.append(" UIResource");
      setText(sb.toString());
      setIcon(createColorIcon(value));
    }
  }

  private static class FontRenderer extends Renderer<Font> {
    @Override
    public void setValue(@NotNull final Font value) {
      StringBuilder sb = new StringBuilder();
      sb.append(value.getFontName()).append(" (").append(value.getFamily()).append("), ").append(value.getSize()).append("px");
      if (Font.BOLD == (Font.BOLD & value.getStyle())) sb.append(" bold");
      if (Font.ITALIC == (Font.ITALIC & value.getStyle())) sb.append(" italic");
      if (value instanceof UIResource) sb.append(" UIResource");

      Map<TextAttribute, ?> attributes = value.getAttributes();
      StringBuilder attrs = new StringBuilder();
      for (Map.Entry<TextAttribute, ?> entry : attributes.entrySet()) {
        if (entry.getKey() == TextAttribute.FAMILY || entry.getKey() == TextAttribute.SIZE || entry.getValue() == null) continue;
        if (attrs.length() > 0) attrs.append(",");
        String name = ReflectionUtil.getField(TextAttribute.class, entry.getKey(), String.class, "name");
        attrs.append(name != null ? name : entry.getKey()).append("=").append(entry.getValue());
      }
      if (attrs.length() > 0) {
        sb.append(" {").append(attrs).append("}");
      }

      setText(sb.toString());
    }
  }

  private static class BooleanRenderer extends Renderer<Boolean> {
    @Override
    public void setValue(@NotNull final Boolean value) {
      setText(value ? "Yes" : "No");
    }
  }

  private static class IconRenderer extends Renderer<Icon> {
    @Override
    public void setValue(@NotNull final Icon value) {
      setIcon(value);
      setText(getPathToIcon(value));
    }

    private static String getPathToIcon(@NotNull Icon value) {
      if (value instanceof RetrievableIcon) {
        Icon icon = ((RetrievableIcon)value).retrieveIcon();
        if (icon != value) {
          return getPathToIcon(icon);
        }
      }
      String text = getToStringValue(value);
      if (text.startsWith("jar:") && text.contains("!")) {
        int index = text.lastIndexOf("!");
        String jarFile = text.substring(4, index);
        String path = text.substring(index + 1);

        return path + " in " + jarFile;
      }
      return text;
    }
  }

  private static class BorderRenderer extends Renderer<Border> {
    @Override
    public void setValue(@NotNull final Border value) {
      setText(getTextDescription(value));

      if (value instanceof CompoundBorder) {
        Color insideColor = getBorderColor(((CompoundBorder)value).getInsideBorder());
        Color outsideColor = getBorderColor(((CompoundBorder)value).getOutsideBorder());
        if (insideColor != null && outsideColor != null) {
          setIcon(createColorIcon(insideColor, outsideColor));
        }
        else if (insideColor != null) {
          setIcon(createColorIcon(insideColor));
        }
        else if (outsideColor != null) {
          setIcon(createColorIcon(outsideColor));
        }
        else {
          setIcon(null);
        }
      }
      else {
        Color color = getBorderColor(value);
        setIcon(color != null ? createColorIcon(color) : null);
      }
    }

    @Nullable
    private static Color getBorderColor(@NotNull Border value) {
      if (value instanceof LineBorder) {
        return ((LineBorder)value).getLineColor();
      }
      else if (value instanceof CustomLineBorder) {
        try {
          return (Color)ReflectionUtil.findField(CustomLineBorder.class, Color.class, "myColor").get(value);
        }
        catch (Exception ignore) {
        }
      }

      return null;
    }

    @NotNull
    private static String getTextDescription(@Nullable Border value) {
      if (value == null) {
        return "null";
      }

      StringBuilder sb = new StringBuilder();
      sb.append(getClassName(value));

      Color color = getBorderColor(value);
      if (color != null) sb.append(" color=").append(color.toString());

      if (value instanceof LineBorder) {
        if (((LineBorder)value).getRoundedCorners()) sb.append(" roundedCorners=true");
      }
      if (value instanceof TitledBorder) {
        sb.append(" title='").append(((TitledBorder)value).getTitle()).append("'");
      }
      if (value instanceof CompoundBorder) {
        sb.append(" inside={").append(getTextDescription(((CompoundBorder)value).getInsideBorder())).append("}");
        sb.append(" outside={").append(getTextDescription(((CompoundBorder)value).getOutsideBorder())).append("}");
      }
      if (value instanceof EmptyBorder) {
        Insets insets = ((EmptyBorder)value).getBorderInsets();
        sb.append(" insets={top=").append(insets.top)
          .append(" left=").append(insets.left)
          .append(" bottom=").append(insets.bottom)
          .append(" right=").append(insets.right)
          .append("}");
      }

      if (value instanceof UIResource) sb.append(" UIResource");
      sb.append(" (").append(getToStringValue(value)).append(")");

      return sb.toString();
    }
  }

  private static class ObjectRenderer extends Renderer<Object> {
    {
      putClientProperty("html.disable", Boolean.TRUE);
    }
    @Override
    public void setValue(@NotNull final Object value) {
      setText(getToStringValue(value));
      setIcon(getText().contains("$$$setupUI$$$") ? AllIcons.FileTypes.UiForm : null);
      if (!getText().equals(getText().trim())) {
        setForeground(JBColor.RED);
      }
    }
  }

  @SuppressWarnings("rawtypes")
  @NotNull
  private static String getToStringValue(@NotNull Object value) {
    StringBuilder sb = new StringBuilder();
    if (value.getClass().getName().equals("javax.swing.ArrayTable")) {
      Object table = ReflectionUtil.getField(value.getClass(), value, Object.class, "table");
      if (table != null) {
        try {
          if (table instanceof Object[]) {
            Object[] arr = (Object[])table;
            for (int i = 0; i < arr.length; i += 2) {
              if (arr[i].equals("uiInspector.addedAt")) continue;
              if (sb.length() > 0) sb.append(",");
              sb.append('[').append(arr[i]).append("->").append(arr[i + 1]).append(']');
            }
          }
          else if (table instanceof Map) {
            Map map = (Map)table;
            Set<Map.Entry> set = map.entrySet();
            for (Map.Entry entry : set) {
              if (entry.getKey().equals("uiInspector.addedAt")) continue;
              if (sb.length() > 0) sb.append(",");
              sb.append('[').append(entry.getKey()).append("->").append(entry.getValue()).append(']');
            }
          }
        }
        catch (Exception e) {
          //ignore
        }
      }
      if (sb.length() == 0) sb.append("-");
      value = sb;
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int index = 0; index < length; index++) {
        if (sb.length() > 0) sb.append(", ");
        Object obj = Array.get(value, index);
        if (obj != null) {
          sb.append(obj.getClass().getName());
        }
      }
      value = sb.length() == 0 ? "-" : sb;
    }
    String toString = StringUtil.notNullize(String.valueOf(value), "toString()==null");
    return toString.replace('\n', ' ');
  }

  @NotNull
  private static String getClassName(Object value) {
    Class<?> clazz0 = value.getClass();
    Class<?> clazz = clazz0.isAnonymousClass() ? clazz0.getSuperclass() : clazz0;
    return clazz.getSimpleName();
  }

  private static ColorIcon createColorIcon(Color color) {
    return JBUIScale.scaleIcon(new ColorIcon(13, 11, color, true));
  }

  private static Icon createColorIcon(Color color1, Color color2) {
    return JBUIScale.scaleIcon(new ColorsIcon(11, color1, color2));
  }


  private static class InspectorTableModel extends AbstractTableModel {

    final List<String> PROPERTIES = Arrays.asList(
      "ui", "getLocation", "getLocationOnScreen",
      "getSize", "isOpaque", "getBorder",
      "getForeground", "getBackground", "getFont",
      "getCellRenderer", "getCellEditor",
      "getMinimumSize", "getMaximumSize", "getPreferredSize",
      "getPreferredScrollableViewportSize",
      "getText", "isEditable", "getIcon",
      "getVisibleRect", "getLayout",
      "getAlignmentX", "getAlignmentY",
      "getTooltipText", "getToolTipText", "cursor",
      "isShowing", "isEnabled", "isVisible", "isDoubleBuffered",
      "isFocusable", "isFocusCycleRoot", "isFocusOwner",
      "isValid", "isDisplayable", "isLightweight", "getClientProperties", "getMouseListeners"
    );

    final List<String> CHECKERS = Arrays.asList(
      "isForegroundSet", "isBackgroundSet", "isFontSet",
      "isMinimumSizeSet", "isMaximumSizeSet", "isPreferredSizeSet"
    );

    final List<String> ACCESSIBLE_CONTEXT_PROPERTIES = Arrays.asList(
      "getAccessibleRole", "getAccessibleName", "getAccessibleDescription",
      "getAccessibleAction", "getAccessibleChildrenCount",
      "getAccessibleIndexInParent", "getAccessibleRelationSet",
      "getAccessibleStateSet", "getAccessibleEditableText",
      "getAccessibleTable", "getAccessibleText",
      "getAccessibleValue", "accessibleChangeSupport"
    );

    final List<String> MIGLAYOUT_CC_PROPERTIES = Arrays.asList(
      "getHorizontal", "getVertical"
    );

    final Component myComponent;
    final List<PropertyBean> myProperties = new ArrayList<>();

    InspectorTableModel(@NotNull List<? extends PropertyBean> clickInfo) {
      myComponent = null;
      myProperties.addAll(clickInfo);
    }

    InspectorTableModel(@NotNull Component c) {
      myComponent = c;

      fillTable();
    }

    void fillTable() {
      addProperties("", myComponent, PROPERTIES);
      Object addedAt = myComponent instanceof JComponent ? ((JComponent)myComponent).getClientProperty("uiInspector.addedAt") : null;
      myProperties.add(new PropertyBean("added-at", addedAt));

      // Add properties related to Accessibility support. This is useful for manually
      // inspecting what kind (if any) of accessibility support components expose.
      boolean isAccessible = myComponent instanceof Accessible;
      myProperties.add(new PropertyBean("accessible", isAccessible));
      AccessibleContext context = myComponent.getAccessibleContext();
      myProperties.add(new PropertyBean("accessibleContext", context));
      if (isAccessible) {
        addProperties("  ", myComponent.getAccessibleContext(), ACCESSIBLE_CONTEXT_PROPERTIES);
      }

      if (myComponent instanceof Container) {
        addLayoutProperties((Container)myComponent);
      }
      if (myComponent.getParent() != null) {
        LayoutManager layout = myComponent.getParent().getLayout();
        if (layout instanceof com.intellij.ui.layout.migLayout.patched.MigLayout) {
          CC cc = ((com.intellij.ui.layout.migLayout.patched.MigLayout)layout).getComponentConstraints().get(myComponent);
          if (cc != null) {
            addMigLayoutComponentConstraints(cc);
          }
        }
      }
    }

    private void addProperties(@NotNull String prefix, @NotNull Object component, @NotNull List<String> methodNames) {
      Class<?> clazz = component.getClass();
      myProperties.add(new PropertyBean(prefix + "class", clazz.getName()));

      if (clazz.isAnonymousClass()) {
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
          myProperties.add(new PropertyBean(prefix + "superclass", superClass.getName(), true));
        }
      }

      Class<?> declaringClass = clazz.getDeclaringClass();
      if (declaringClass != null) {
        myProperties.add(new PropertyBean(prefix + "declaringClass", declaringClass.getName()));
      }

      if (component instanceof com.intellij.ui.treeStructure.Tree) {
        TreeModel model = ((Tree)component).getModel();
        if (model != null) {
          myProperties.add(new PropertyBean(prefix + "treeModelClass", model.getClass().getName(), true));
        }
      }

      addActionInfo(component);
      addToolbarInfo(component);
      addGutterInfo(component);

      UiInspectorContextProvider contextProvider = UiInspectorUtil.getProvider(component);
      if (contextProvider != null) {
        myProperties.addAll(contextProvider.getUiInspectorContext());
      }

      StringBuilder classHierarchy = new StringBuilder();
      for (Class<?> cl = clazz.getSuperclass(); cl != null; cl = cl.getSuperclass()) {
        if (classHierarchy.length() > 0) classHierarchy.append(" ").append(UIUtil.rightArrow()).append(" ");
        classHierarchy.append(cl.getName());
        if (JComponent.class.getName().equals(cl.getName())) break;
      }
      myProperties.add(new PropertyBean(prefix + "hierarchy", classHierarchy.toString()));

      if (component instanceof Component) {
        DialogWrapper dialog = DialogWrapper.findInstance((Component)component);
        if (dialog != null) {
          myProperties.add(new PropertyBean(prefix + "dialogWrapperClass", dialog.getClass().getName(), true));
        }
      }

      addPropertiesFromMethodNames(prefix, component, methodNames);
    }

    private static List<Method> collectAllMethodsRecursively(Class<?> clazz) {
      ArrayList<Method> list = new ArrayList<>();
      for(Class<?> cl = clazz; cl != null; cl = cl.getSuperclass()) {
        list.addAll(Arrays.asList(cl.getDeclaredMethods()));
      }
      return list;
    }

    private void addPropertiesFromMethodNames(@NotNull String prefix,
                                              @NotNull Object component,
                                              @NotNull List<String> methodNames) {
      Class<?> clazz0 = component.getClass();
      Class<?> clazz = clazz0.isAnonymousClass() ? clazz0.getSuperclass() : clazz0;
      for (String name: methodNames) {
        String propertyName = ObjectUtils.notNull(StringUtil.getPropertyName(name), name);
        Object propertyValue;
        try {
          try {
            //noinspection ConstantConditions
            propertyValue = ReflectionUtil.findMethod(collectAllMethodsRecursively(clazz), name).invoke(component);
          }
          catch (Exception e) {
            propertyValue = ReflectionUtil.findField(clazz, null, name).get(component);
          }
          boolean changed = false;
          try {
            final String checkerMethodName = "is" + StringUtil.capitalize(propertyName) + "Set";
            if (CHECKERS.contains(checkerMethodName)) {
              final Object value = ReflectionUtil.findMethod(Arrays.asList(clazz.getMethods()), checkerMethodName).invoke(component);
              if (value instanceof Boolean) {
                changed = ((Boolean)value).booleanValue();
              }
            }
          } catch (Exception e) {changed = false;}
          myProperties.add(new PropertyBean(prefix + propertyName, propertyValue, changed));
        }
        catch (Exception ignored) {
        }
      }
    }

    private void addGutterInfo(Object component) {
      if (component instanceof EditorGutterComponentEx && ((JComponent)component).getClientProperty(CLICK_INFO_POINT) instanceof Point) {
        Point clickPoint = (Point)((JComponent)component).getClientProperty(CLICK_INFO_POINT);
        GutterMark renderer = ((EditorGutterComponentEx)component).getGutterRenderer(clickPoint);
        if (renderer != null) {
          myProperties.add(new PropertyBean("gutter renderer", renderer.getClass().getName(), true));
        }
      }
    }

    private void addActionInfo(Object component) {
      AnAction action = null;
      if (component instanceof ActionButton) {
        action = ((ActionButton)component).getAction();
      } else if (component instanceof JComponent) {
        if (component instanceof ActionMenuItem) {
          action = ((ActionMenuItem)component).getAnAction();
        }
        else if (component instanceof ActionMenu) {
          action = ((ActionMenu)component).getAnAction();
        }
        else {
          action = getAction(
            ComponentUtil.findParentByCondition((Component)component, c -> getAction(c) != null)
          );
        }
      }

      if (action != null) {
        myProperties.addAll(collectAnActionInfo(action));
      }
    }

    private void addToolbarInfo(Object component) {
      if (component instanceof ActionToolbarImpl) {
        ActionToolbarImpl toolbar = (ActionToolbarImpl)component;
        myProperties.addAll(UiInspectorUtil.collectActionGroupInfo("Toolbar", toolbar.getActionGroup(), toolbar.getPlace()));
      }
    }

    private void addLayoutProperties(@NotNull Container component) {
      String prefix = "  ";

      LayoutManager layout = component.getLayout();
      if (layout instanceof GridBagLayout) {
        GridBagLayout bagLayout = (GridBagLayout)layout;
        GridBagConstraints defaultConstraints = ReflectionUtil.getField(GridBagLayout.class, bagLayout, GridBagConstraints.class, "defaultConstraints");

        myProperties.add(new PropertyBean("GridBagLayout constraints",
                                          String.format("defaultConstraints - %s", toString(defaultConstraints))));
        if (bagLayout.columnWidths != null) myProperties.add(new PropertyBean(prefix + "columnWidths", Arrays.toString(bagLayout.columnWidths)));
        if (bagLayout.rowHeights != null) myProperties.add(new PropertyBean(prefix + "rowHeights", Arrays.toString(bagLayout.rowHeights)));
        if (bagLayout.columnWeights != null) myProperties.add(new PropertyBean(prefix + "columnWeights", Arrays.toString(bagLayout.columnWeights)));
        if (bagLayout.rowWeights != null) myProperties.add(new PropertyBean(prefix + "rowWeights", Arrays.toString(bagLayout.rowWeights)));

        for (Component child : component.getComponents()) {
          myProperties.add(new PropertyBean(prefix + getComponentName(child), toString(bagLayout.getConstraints(child))));
        }
      }
      else if (layout instanceof BorderLayout) {
        BorderLayout borderLayout = (BorderLayout)layout;

        myProperties.add(new PropertyBean("BorderLayout constraints",
                                          String.format("hgap - %s, vgap - %s", borderLayout.getHgap(), borderLayout.getVgap())));

        for (Component child : component.getComponents()) {
          myProperties.add(new PropertyBean(prefix + getComponentName(child), borderLayout.getConstraints(child)));
        }
      }
      else if (layout instanceof CardLayout) {
        CardLayout cardLayout = (CardLayout)layout;
        Integer currentCard = ReflectionUtil.getField(CardLayout.class, cardLayout, null, "currentCard");
        //noinspection UseOfObsoleteCollectionType
        Vector vector = ReflectionUtil.getField(CardLayout.class, cardLayout, Vector.class, "vector");
        String cardDescription = "???";
        if (vector != null && currentCard != null) {
          Object card = vector.get(currentCard);
          cardDescription = ReflectionUtil.getField(card.getClass(), card, String.class, "name");
        }

        myProperties.add(new PropertyBean("CardLayout constraints",
                                          String.format("card - %s, hgap - %s, vgap - %s",
                                                        cardDescription, cardLayout.getHgap(), cardLayout.getVgap())));

        if (vector != null) {
          for (Object card : vector) {
            String cardName = ReflectionUtil.getField(card.getClass(), card, String.class, "name");
            Component child = ReflectionUtil.getField(card.getClass(), card, Component.class, "comp");
            myProperties.add(new PropertyBean(prefix + getComponentName(child), cardName));
          }
        }
      }
      else if (layout instanceof MigLayout) {
        MigLayout migLayout = (MigLayout)layout;

        Object constraints = migLayout.getLayoutConstraints();
        if (constraints instanceof LC) {
          addMigLayoutLayoutConstraints((LC)constraints);
        }
        else {
          myProperties.add(new PropertyBean("MigLayout layout constraints", constraints));
        }

        constraints = migLayout.getColumnConstraints();
        if (constraints instanceof AC) {
          addMigLayoutAxisConstraints("MigLayout column constraints", (AC)constraints);
        }
        else {
          myProperties.add(new PropertyBean("MigLayout column constraints", constraints));
        }

        constraints = migLayout.getRowConstraints();
        if (constraints instanceof AC) {
          addMigLayoutAxisConstraints("MigLayout row constraints", (AC)constraints);
        }
        else {
          myProperties.add(new PropertyBean("MigLayout row constraints", constraints));
        }

        for (Component child : component.getComponents()) {
          myProperties.add(new PropertyBean(prefix + getComponentName(child), migLayout.getComponentConstraints(child)));
        }
      }
      else if (layout instanceof com.intellij.ui.layout.migLayout.patched.MigLayout) {
        com.intellij.ui.layout.migLayout.patched.MigLayout migLayout = (com.intellij.ui.layout.migLayout.patched.MigLayout)layout;

        addMigLayoutLayoutConstraints(migLayout.getLayoutConstraints());
        addMigLayoutAxisConstraints("MigLayout column constraints", migLayout.getColumnConstraints());
        addMigLayoutAxisConstraints("MigLayout row constraints", migLayout.getRowConstraints());
      }
    }

    private void addMigLayoutLayoutConstraints(LC lc) {
      myProperties.add(new PropertyBean("MigLayout layout constraints", lcConstraintToString(lc)));
      UnitValue[] insets = lc.getInsets();
      if (insets != null) {
        myProperties.add(new PropertyBean("  lc.insets", Arrays.toString(insets)));
      }
      UnitValue alignX = lc.getAlignX();
      UnitValue alignY = lc.getAlignY();
      if (alignX != null || alignY != null) {
        myProperties.add(new PropertyBean("  lc.align", "x: " + alignX + "; y: " + alignY));
      }
      BoundSize width = lc.getWidth();
      BoundSize height = lc.getHeight();
      if (width != BoundSize.NULL_SIZE || height != BoundSize.NULL_SIZE) {
        myProperties.add(new PropertyBean("  lc.size", "width: " + width + "; height: " + height));
      }
      BoundSize gridX = lc.getGridGapX();
      BoundSize gridY = lc.getGridGapY();
      if (gridX != null || gridY != null) {
        myProperties.add(new PropertyBean("  lc.gridGap", "x: " + gridX + "; y: " + gridY));
      }
      boolean fillX = lc.isFillX();
      boolean fillY = lc.isFillY();
      if (fillX || fillY) {
        myProperties.add(new PropertyBean("  lc.fill", "x: " + fillX + "; y: " + fillY));
      }
      BoundSize packWidth = lc.getPackWidth();
      BoundSize packHeight = lc.getPackHeight();
      if (packWidth != BoundSize.NULL_SIZE || packHeight != BoundSize.NULL_SIZE) {
        myProperties.add(new PropertyBean("  lc.pack", "width: " + packWidth + "; height: " + packHeight +
                                                       "; widthAlign: " + lc.getPackWidthAlign() +
                                                       "; heightAlign: " + lc.getPackHeightAlign()));
      }
    }

    private static String lcConstraintToString(LC constraint) {
      return "isFlowX=" + constraint.isFlowX() +
             " leftToRight=" + constraint.getLeftToRight() +
             " noGrid=" + constraint.isNoGrid() +
             " hideMode=" + constraint.getHideMode() +
             " visualPadding=" + constraint.isVisualPadding() +
             " topToBottom=" + constraint.isTopToBottom() +
             " noCache=" + constraint.isNoCache();
    }

    private void addMigLayoutAxisConstraints(String title, AC ac) {
      myProperties.add(new PropertyBean(title, ac));
      DimConstraint[] constraints = ac.getConstaints();
      for (int i = 0; i < constraints.length; i++) {
        addDimConstraintProperties("  [" + i + "]", constraints[i]);
      }
    }

    private void addMigLayoutComponentConstraints(CC cc) {
      myProperties.add(new PropertyBean("MigLayout component constraints", componentConstraintsToString(cc)));
      DimConstraint horizontal = cc.getHorizontal();
      addDimConstraintProperties("  cc.horizontal", horizontal);
      DimConstraint vertical = cc.getVertical();
      addDimConstraintProperties("  cc.vertical", vertical);
    }

    private void addDimConstraintProperties(String name, DimConstraint constraint) {
      myProperties.add(new PropertyBean(name, dimConstraintToString(constraint)));
      BoundSize size = constraint.getSize();
      if (size != null) {
        myProperties.add(new PropertyBean("  " + name + ".size", size.toString()));
      }
      UnitValue align = constraint.getAlign();
      if (align != null) {
        myProperties.add(new PropertyBean("  " + name + ".align", align.toString()));
      }
      BoundSize gapBefore = constraint.getGapBefore();
      if (gapBefore != null && !gapBefore.isUnset()) {
        myProperties.add(new PropertyBean("  " + name + ".gapBefore", gapBefore.toString()));
      }
      BoundSize gapAfter = constraint.getGapAfter();
      if (gapAfter != null && !gapAfter.isUnset()) {
        myProperties.add(new PropertyBean("  " + name + ".gapAfter", gapAfter.toString()));
      }
    }

    private static String componentConstraintsToString(CC cc) {
      CC newCC = new CC();
      StringBuilder stringBuilder = new StringBuilder();
      if (cc.getSkip() != newCC.getSkip()) {
        stringBuilder.append(" skip=").append(cc.getSkip());
      }
      if (cc.getSpanX() != newCC.getSpanX()) {
        stringBuilder.append(" spanX=").append(cc.getSpanX() == LayoutUtil.INF ? "INF" : cc.getSpanX());
      }
      if (cc.getSpanY() != newCC.getSpanY()) {
        stringBuilder.append(" spanY=").append(cc.getSpanY() == LayoutUtil.INF ? "INF" : cc.getSpanY());
      }
      if (cc.getPushX() != null) {
        stringBuilder.append(" pushX=").append(cc.getPushX());
      }
      if (cc.getPushY() != null) {
        stringBuilder.append(" pushY=").append(cc.getPushY());
      }
      if (cc.getSplit() != newCC.getSplit()) {
        stringBuilder.append(" split=").append(cc.getSplit());
      }
      if (cc.isWrap()) {
        stringBuilder.append(" wrap=");
        if (cc.getWrapGapSize() != null) {
          stringBuilder.append(cc.getWrapGapSize());
        }
        else {
          stringBuilder.append("true");
        }
      }
      if (cc.isNewline()) {
        stringBuilder.append(" newline=");
        if (cc.getNewlineGapSize() != null) {
          stringBuilder.append(cc.getNewlineGapSize());
        }
        else {
          stringBuilder.append("true");
        }
      }
      return stringBuilder.toString().trim();
    }

    private static String dimConstraintToString(DimConstraint constraint) {
      StringBuilder stringBuilder = new StringBuilder();
      DimConstraint newConstraint = new DimConstraint();
      if (!Comparing.equal(constraint.getGrow(), newConstraint.getGrow())) {
        stringBuilder.append(" grow=").append(constraint.getGrow());
      }
      if (constraint.getGrowPriority() != newConstraint.getGrowPriority()) {
        stringBuilder.append(" growPrio=").append(constraint.getGrowPriority());
      }
      if (!Comparing.equal(constraint.getShrink(), newConstraint.getShrink())) {
        stringBuilder.append(" shrink=").append(constraint.getShrink());
      }
      if (constraint.getShrinkPriority() != newConstraint.getShrinkPriority()) {
        stringBuilder.append(" shrinkPrio=").append(constraint.getShrinkPriority());
      }
      if (constraint.isFill() != newConstraint.isFill()) {
        stringBuilder.append(" fill=").append(constraint.isFill());
      }
      if (constraint.isNoGrid() != newConstraint.isNoGrid()) {
        stringBuilder.append(" noGrid=").append(constraint.isNoGrid());
      }
      if (!Objects.equals(constraint.getSizeGroup(), newConstraint.getSizeGroup())) {
        stringBuilder.append(" sizeGroup=").append(constraint.getSizeGroup());
      }
      if (!Objects.equals(constraint.getEndGroup(), newConstraint.getEndGroup())) {
        stringBuilder.append(" endGroup=").append(constraint.getEndGroup());
      }
      return stringBuilder.toString();
    }

    @NotNull
    private static String toString(@Nullable GridBagConstraints constraints) {
      if (constraints == null) return "null";

      MoreObjects.ToStringHelper h = MoreObjects.toStringHelper("");
      appendFieldValue(h, constraints, "gridx");
      appendFieldValue(h, constraints, "gridy");
      appendFieldValue(h, constraints, "gridwidth");
      appendFieldValue(h, constraints, "gridheight");
      appendFieldValue(h, constraints, "weightx");
      appendFieldValue(h, constraints, "weighty");
      appendFieldValue(h, constraints, "anchor");
      appendFieldValue(h, constraints, "fill");
      appendFieldValue(h, constraints, "insets");
      appendFieldValue(h, constraints, "ipadx");
      appendFieldValue(h, constraints, "ipady");
      return h.toString();
    }

    private static void appendFieldValue(@NotNull MoreObjects.ToStringHelper h,
                                         @NotNull GridBagConstraints constraints,
                                         @NotNull String field) {
      Object value = ReflectionUtil.getField(GridBagConstraints.class, constraints, null, field);
      Object defaultValue = ReflectionUtil.getField(GridBagConstraints.class, new GridBagConstraints(), null, field);
      if (!Comparing.equal(value, defaultValue)) h.add(field, value);
    }

    @Override
    @Nullable
    public Object getValueAt(int row, int column) {
      final PropertyBean bean = myProperties.get(row);
      if (bean != null) {
        return column == 0 ? bean.propertyName : bean.propertyValue;
      }

      return null;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return col == 1 && updater(myProperties.get(row)) != null;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
      PropertyBean bean = myProperties.get(row);
      try {
        myProperties.set(row, new PropertyBean(bean.propertyName, Objects.requireNonNull(updater(bean)).fun(value)));
      }
      catch (Exception ignored) {
      }
    }

    @Nullable
    public Function<Object, Object> updater(PropertyBean bean) {
      if (myComponent == null) return null;

      String name = bean.propertyName.trim();
      try {
        try {
          Method getter;
          try {
            getter = myComponent.getClass().getMethod("get" + StringUtil.capitalize(name));
          }
          catch (Exception e) {
            getter = myComponent.getClass().getMethod("is" + StringUtil.capitalize(name));
          }
          final Method finalGetter = getter;
          final Method setter = myComponent.getClass().getMethod("set" + StringUtil.capitalize(name), getter.getReturnType());
          setter.setAccessible(true);
          return o -> {
            try {
              setter.invoke(myComponent, fromObject(o, finalGetter.getReturnType()));
              return finalGetter.invoke(myComponent);
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          };
        }
        catch (Exception e) {
          final Field field = ReflectionUtil.findField(myComponent.getClass(), null, name);
          if (Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
            return null;
          }
          return o -> {
            try {
              field.set(myComponent, fromObject(o, field.getType()));
              return field.get(myComponent);
            }
            catch (Exception e1) {
              throw new RuntimeException(e1);
            }
          };
        }
      }
      catch (Exception ignored) {
      }
      return null;
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public int getRowCount() {
      return myProperties.size();
    }

    @Override
    public String getColumnName(int columnIndex) {
      return columnIndex == 0 ? "Property" : "Value";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 0 ? String.class : Object.class;
    }

    public void refresh() {
      myProperties.clear();
      fillTable();
      fireTableDataChanged();
    }
  }

  @Nullable
  private static AnAction getAction(Component c) {
    return UIUtil.getClientProperty(c, ACTION_KEY);
  }

  private static class UiInspector implements AWTEventListener, Disposable {

    UiInspector() {
      Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    }

    @Override
    public void dispose() {
      Toolkit.getDefaultToolkit().removeAWTEventListener(this);
      for (Window window : Window.getWindows()) {
        if (window instanceof InspectorWindow) {
          ((InspectorWindow)window).close();
        }
      }
    }

    public void showInspector(@NotNull Component c) {
      Window window = new InspectorWindow(c);
      if (DimensionService.getInstance().getSize(InspectorWindow.getDimensionServiceKey()) == null) {
        window.pack();
      }
      window.setVisible(true);
      window.toFront();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
      if (event instanceof MouseEvent) {
        processMouseEvent((MouseEvent)event);
      }
      else if (event instanceof ContainerEvent) {
        processContainerEvent((ContainerEvent)event);
      }
    }

    private void processMouseEvent(MouseEvent me) {
      if (!me.isAltDown() || !me.isControlDown()) return;
      if (me.getClickCount() != 1 || me.isPopupTrigger()) return;
      me.consume();
      if (me.getID() != MouseEvent.MOUSE_RELEASED) return;
      Component component = me.getComponent();

      if (component instanceof Container) {
        component = ((Container)component).findComponentAt(me.getPoint());
      }
      else if (component == null) {
        component = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      }
      if (component != null) {
        if (component instanceof JComponent) {
          ((JComponent)component).putClientProperty(CLICK_INFO, getClickInfo(me, component));
          ((JComponent)component).putClientProperty(CLICK_INFO_POINT, me.getPoint());
        }
        showInspector(component);
      }
    }

    private static List<PropertyBean> getClickInfo(MouseEvent me, Component component) {
      if (me.getComponent() == null) return null;
      me = SwingUtilities.convertMouseEvent(me.getComponent(), me, component);
      List<PropertyBean> clickInfo = new ArrayList<>();
      //clickInfo.add(new PropertyBean("Click point", me.getPoint()));
      if (component instanceof JList) {
        JList list = (JList)component;
        int row = list.getUI().locationToIndex(list, me.getPoint());
        if (row != -1) {
          Component rendererComponent = list.getCellRenderer()
            .getListCellRendererComponent(list, list.getModel().getElementAt(row), row, list.getSelectionModel().isSelectedIndex(row),
                                          list.hasFocus());
          clickInfo.addAll(findActionsFor(list.getModel().getElementAt(row)));
          clickInfo.add(new PropertyBean(RENDERER_BOUNDS, list.getUI().getCellBounds(list, row, row)));
          clickInfo.addAll(new InspectorTableModel(rendererComponent).myProperties);
          return clickInfo;
        }
      }
      if (component instanceof JTable) {
        JTable table = (JTable)component;
        int row = table.rowAtPoint(me.getPoint());
        int column = table.columnAtPoint(me.getPoint());
        if (row != -1 && column != -1) {
          Component rendererComponent = table.getCellRenderer(row, column)
            .getTableCellRendererComponent(table, table.getValueAt(row, column), table.getSelectionModel().isSelectedIndex(row),
                                           table.hasFocus(), row, column);
          clickInfo.add(new PropertyBean(RENDERER_BOUNDS, table.getCellRect(row, column, true)));
          clickInfo.addAll(new InspectorTableModel(rendererComponent).myProperties);
          return clickInfo;
        }
      }
      if (component instanceof JTree) {
        JTree tree = (JTree)component;
        TreePath path = tree.getClosestPathForLocation(me.getX(), me.getY());
        if (path != null) {
          Object object = path.getLastPathComponent();
          Component rendererComponent = tree.getCellRenderer().getTreeCellRendererComponent(
              tree, object, tree.getSelectionModel().isPathSelected(path),
              tree.isExpanded(path),
              tree.getModel().isLeaf(object),
              tree.getRowForPath(path), tree.hasFocus());
          clickInfo.add(new PropertyBean(RENDERER_BOUNDS, tree.getPathBounds(path)));
          clickInfo.addAll(new InspectorTableModel(rendererComponent).myProperties);
          return clickInfo;
        }
      }
      return null;
    }

    private static List<PropertyBean> findActionsFor(Object object) {
      if (object instanceof PopupFactoryImpl.ActionItem) {
        AnAction action = ((PopupFactoryImpl.ActionItem)object).getAction();
        return collectAnActionInfo(action);
      }
      if (object instanceof QuickFixWrapper) {
        return findActionsFor(((QuickFixWrapper)object).getFix());
      } else if (object instanceof IntentionActionDelegate) {
        IntentionAction delegate = ((IntentionActionDelegate)object).getDelegate();
        if (delegate != object) {
          return findActionsFor(delegate);
        }
      } else if (object instanceof IntentionAction) {
        return Collections.singletonList(new PropertyBean("intention action", object.getClass().getName(), true));
      } else if (object instanceof QuickFix) {
        return Collections.singletonList(new PropertyBean("quick fix", object.getClass().getName(), true));
      }

      return Collections.emptyList();
    }

    private static void processContainerEvent(ContainerEvent event) {
      Component child = event.getID() == ContainerEvent.COMPONENT_ADDED ? event.getChild() : null;
      if (child instanceof JComponent && !(event.getSource() instanceof CellRendererPane)) {
        String text = ExceptionUtil.getThrowableText(new Throwable());
        int first = text.indexOf("at com.intellij", text.indexOf("at java.awt"));
        int last = text.indexOf("at java.awt.EventQueue");
        if (last == -1) last = text.length();
        String val = last > first && first > 0 ?  text.substring(first, last).trim(): null;
        ((JComponent)child).putClientProperty("uiInspector.addedAt", val);
      }
    }
  }

  /** @noinspection UseJBColor*/
  private static Object fromObject(Object o, Class<?> type) {
    if (o == null) return null;
    if (type.isAssignableFrom(o.getClass())) return o;
    if ("null".equals(o)) return null;

    String value = String.valueOf(o).trim();
    if (type == int.class) return Integer.parseInt(value);
    if (type == boolean.class) return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    if (type == byte.class) return Byte.parseByte(value);
    if (type == short.class) return Short.parseShort(value);
    if (type == double.class) return Double.parseDouble(value);
    if (type == float.class) return Float.parseFloat(value);

    String[] s = value.split("(?i)\\s*(?:[x@:]|[a-z]+:)\\s*", 6);
    if (type == Dimension.class) {
      if (s.length == 2) return new Dimension(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
    }
    else if (type == Point.class) {
      if (s.length == 2) return new Point(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
    }
    else if (type == Rectangle.class) {
      if (s.length >= 5) {
        return new Rectangle(Integer.parseInt(s[3]), Integer.parseInt(s[4]),
                             Integer.parseInt(s[1]), Integer.parseInt(s[2]));
      }
    }
    else if (type == Insets.class) {
      if (s.length >= 5) {
        return new Insets(Integer.parseInt(s[1]), Integer.parseInt(s[2]),
                          Integer.parseInt(s[4]), Integer.parseInt(s[4]));
      }
    }
    else if (type == Color.class) {
      if (s.length >= 5) {
        return new ColorUIResource(
          new Color(Integer.parseInt(s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3]), Integer.parseInt(s[4])));
      }
    }
    else if (type.getSimpleName().contains("ArrayTable")) {
      return "ArrayTable!";
    }
    throw new UnsupportedOperationException(type.toString());
  }
}
