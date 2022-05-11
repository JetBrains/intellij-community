// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.InternalActionsBundle;
import com.intellij.internal.inspector.components.HierarchyTree;
import com.intellij.internal.inspector.components.ValueCellRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.keymap.impl.ui.MouseShortcutPanel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.StripeTable;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.ui.picker.ColorListener;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

public class UiInspectorAction extends DumbAwareAction implements LightEditCompatible, ActionPromoter {
  private static final String ACTION_ID = "UiInspector";
  private static final String RENDERER_BOUNDS = "clicked renderer";

  public static final Key<Pair<List<PropertyBean>, Component>> CLICK_INFO = Key.create("CLICK_INFO");
  public static final Key<Point> CLICK_INFO_POINT = Key.create("CLICK_INFO_POINT");
  public static final Key<Throwable> ADDED_AT_STACKTRACE = Key.create("uiInspector.addedAt");

  private final List<MouseShortcut> myMouseShortcuts = new ArrayList<>();

  private static boolean ourGlobalInstanceInitialized = false;
  public static synchronized void initGlobalInspector() {
    if (!ourGlobalInstanceInitialized) {
      ourGlobalInstanceInitialized = true;
      UIUtil.invokeLaterIfNeeded(() -> {
        new UiInspector(null);
      });
    }
  }

  public UiInspectorAction() {
    setEnabledInModalContext(true);
    updateMouseShortcuts();
    KeymapManagerEx.getInstanceEx().addWeakListener(new KeymapManagerListener() {
      @Override
      public void activeKeymapChanged(@Nullable Keymap keymap) {
        updateMouseShortcuts();
      }

      @Override
      public void shortcutChanged(@NotNull Keymap keymap, @NotNull String actionId) {
        if (ACTION_ID.equals(actionId)) {
          updateMouseShortcuts();
        }
      }
    });
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        if (event instanceof MouseEvent && ((MouseEvent)event).getClickCount() > 0 && !myMouseShortcuts.isEmpty()) {
          MouseEvent me = (MouseEvent)event;
          MouseShortcut mouseShortcut = new MouseShortcut(me.getButton(), me.getModifiersEx(), me.getClickCount());
          if (myMouseShortcuts.contains(mouseShortcut) && !(me.getComponent() instanceof MouseShortcutPanel)) {
            me.consume();
          }
        }
      }
    }, AWTEvent.MOUSE_EVENT_MASK);
  }

  private void updateMouseShortcuts() {
    if (KeymapManagerImpl.isKeymapManagerInitialized()) {
      myMouseShortcuts.clear();
      Keymap keymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();
      for (Shortcut shortcut : keymap.getShortcuts(ACTION_ID)) {
        if (shortcut instanceof MouseShortcut) {
          myMouseShortcuts.add((MouseShortcut)shortcut);
        }
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    InputEvent event = e.getInputEvent();
    if (event != null) event.consume();
    Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);

    Project project = e.getProject();
    closeAllInspectorWindows();

    if (event instanceof MouseEvent && event.getComponent() != null) {
      new UiInspector(project).processMouseEvent(project, (MouseEvent)event);
      return;
    }
    if (component == null) {
      component = IdeFocusManager.getInstance(project).getFocusOwner();
    }

    assert component != null;
    new UiInspector(project).showInspector(project, component);
  }

  @Override
  public @Nullable List<AnAction> suppress(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    if (context.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) instanceof EditorComponentImpl) {
      return List.of(this);
    }
    return null;
  }

  private static void closeAllInspectorWindows() {
    Arrays.stream(Window.getWindows())
      .filter(w -> w instanceof InspectorWindow)
      .forEach(w -> Disposer.dispose(((InspectorWindow)w).myInspector));
  }

  public static final class InspectorWindow extends JDialog implements Disposable {
    private InspectorTable myInspectorTable;
    @NotNull private final List<Component> myComponents = new ArrayList<>();
    private List<? extends PropertyBean> myInfo;
    @NotNull private final Component myInitialComponent;
    @NotNull private final List<HighlightComponent> myHighlightComponents = new ArrayList<>();
    private boolean myIsHighlighted = true;
    @NotNull private final HierarchyTree myHierarchyTree;
    @NotNull private final Wrapper myWrapperPanel;
    @Nullable private final Project myProject;
    private final UiInspector myInspector;

    private InspectorWindow(@Nullable Project project,
                            @NotNull Component component,
                            UiInspector inspector) throws HeadlessException {
      super(findWindow(component));
      myProject = project;
      myInspector = inspector;
      Window window = findWindow(component);
      setModal(window instanceof JDialog && ((JDialog)window).isModal());
      myComponents.add(component);
      myInitialComponent = component;
      getRootPane().setBorder(JBUI.Borders.empty(5));

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      setLayout(new BorderLayout());
      setTitle(component.getClass().getName());
      Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey(), null);
      Point location = DimensionService.getInstance().getLocation(getDimensionServiceKey(), null);
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

      actions.addSeparator();

      actions.add(new MyTextAction(InternalActionsBundle.messagePointer("action.Anonymous.text.Accessible")) {
        private boolean isAccessibleEnable = false;

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          switchHierarchy();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setText(isAccessibleEnable ? InternalActionsBundle.message("action.Anonymous.text.Visible") : InternalActionsBundle.message("action.Anonymous.text.Accessible"));
        }

        private void switchHierarchy() {
          TreePath path = myHierarchyTree.getLeadSelectionPath();
          Object node = path == null ? null : path.getLastPathComponent();
          if (node == null) return;
          Component c = ((HierarchyTree.ComponentNode) node).getComponent();
          if (c != null) {
            isAccessibleEnable = !isAccessibleEnable;
            myHierarchyTree.resetModel(c, isAccessibleEnable);
            myHierarchyTree.expandPath(isAccessibleEnable);
          }
        }
      });

      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CONTEXT_TOOLBAR, actions, true);
      toolbar.setTargetComponent(getRootPane());
      add(toolbar.getComponent(), BorderLayout.NORTH);

      myWrapperPanel = new Wrapper();

      myInspectorTable = new InspectorTable(component);
      myHierarchyTree = new HierarchyTree(component) {
        @Override
        public void onComponentsChanged(List<? extends Component> components) {
          switchComponentsInfo(components);
          updateHighlighting();
        }

        @Override
        public void onClickInfoChanged(List<? extends PropertyBean> info) {
          switchClickInfo(info);
          updateHighlighting();
        }
      };
      DataProvider provider = dataId -> {
        if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
          return new Navigatable() {
            @Override
            public void navigate(boolean requestFocus) {
              if (myHierarchyTree.hasFocus()) {
                if (!myComponents.isEmpty()) {
                  openClass(myComponents.get(0).getClass().getName(), requestFocus);
                } else {
                  TreePath path = myHierarchyTree.getSelectionPath();
                  if (path != null) {
                    Object obj = path.getLastPathComponent();
                    if (obj instanceof HierarchyTree.ComponentNode) {
                      Component comp = ((HierarchyTree.ComponentNode)obj).getComponent();
                      if (comp != null) {
                        openClass(comp.getClass().getName(), requestFocus);
                      }
                    }
                  }
                }
              } else if (myInspectorTable.myTable.hasFocus()) {
                int row = myInspectorTable.myTable.getSelectedRow();
                Object at = myInspectorTable.myModel.getValueAt(row, 1);
                openClass(String.valueOf(at), requestFocus);
              }
            }

            @Override
            public boolean canNavigate() {
              return true;
            }

            @Override
            public boolean canNavigateToSource() {
              return true;
            }
          };
        }
        return null;
      };
      myWrapperPanel.setContent(myInspectorTable);

      Splitter splitPane = new JBSplitter(false, "UiInspector.splitter.proportion", 0.5f);
      splitPane.setSecondComponent(myWrapperPanel);
      splitPane.setFirstComponent(new JBScrollPane(myHierarchyTree));
      add(splitPane, BorderLayout.CENTER);
      DataManager.registerDataProvider(splitPane, provider);

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

    private void openClass(String fqn, boolean requestFocus) {
      if (myProject != null) {
        try {
          String javaPsiFacadeFqn = "com.intellij.psi.JavaPsiFacade";
          PluginId pluginId = PluginManager.getPluginByClassNameAsNoAccessToClass(javaPsiFacadeFqn);
          Class<?> facade = null;
          if (pluginId != null) {
            IdeaPluginDescriptor plugin = PluginManager.getInstance().findEnabledPlugin(pluginId);
            if (plugin != null) {
              facade = Class.forName(javaPsiFacadeFqn, false, plugin.getPluginClassLoader());
            }
          }
          else {
            facade = Class.forName(javaPsiFacadeFqn);
          }
          if (facade != null) {
            Method getInstance = facade.getDeclaredMethod("getInstance", Project.class);
            Method findClass = facade.getDeclaredMethod("findClass", String.class, GlobalSearchScope.class);
            Object result = findClass.invoke(getInstance.invoke(null, myProject), fqn, GlobalSearchScope.allScope(myProject));
            if (result instanceof PsiElement) {
              PsiNavigateUtil.navigate((PsiElement)result, requestFocus);
            }
          }
        }
        catch (Exception ignore) {
        }
      }
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

    private void switchComponentsInfo(@NotNull List<? extends Component> components) {
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
      DimensionService.getInstance().setSize(getDimensionServiceKey(), getSize(), null);
      DimensionService.getInstance().setLocation(getDimensionServiceKey(), getLocation(), null);
      super.dispose();
      DialogWrapper.cleanupRootPane(rootPane);
      DialogWrapper.cleanupWindowListeners(this);
      Disposer.dispose(this);
    }

    public void close() {
      if (myInitialComponent instanceof JComponent) {
        UIUtil.putClientProperty((JComponent)myInitialComponent, CLICK_INFO, null);
      }
      myIsHighlighted = false;
      myInfo = null;
      myComponents.clear();
      updateHighlighting();
      setVisible(false);
      Disposer.dispose(this);
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

      Insets insets = component instanceof JComponent ? ((JComponent)component).getInsets() : JBInsets.emptyInsets();
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

  private static final class HighlightComponent extends JComponent {
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

  private static final class InspectorTable extends JPanel implements DataProvider {
    InspectorTableModel myModel;
    DimensionsComponent myDimensionComponent;
    StripeTable myTable;

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
      myTable = new StripeTable(myModel);
      new TableSpeedSearch(myTable);

      TableColumnModel columnModel = myTable.getColumnModel();
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
          Object realValue = table.getModel().getValueAt(row, column);
          if (comp instanceof JLabel) {
            value = ((JLabel)comp).getText();
          }
          if (realValue instanceof Color) {
            Rectangle cellRect = table.getCellRect(row, column, true);
            ColorPicker.showColorPickerPopup(null, (Color)realValue, new ColorListener() {
              @Override
              public void colorChanged(Color color, Object source) {
                if (component != null) {
                  component.setBackground(color);
                  String name = myModel.myProperties.get(row).propertyName;
                  myModel.myProperties.set(row, new PropertyBean(name, color));
                }
              }
            }, new RelativePoint(table, new Point(cellRect.x + JBUI.scale(6), cellRect.y + cellRect.height)));
            return null;
          }
          Component result = super.getTableCellEditorComponent(table, value, isSelected, row, column);
          ((JComponent)result).setBorder(BorderFactory.createLineBorder(JBColor.GRAY, 1));
          return result;
        }
      });
      new DoubleClickListener(){
        @Override
        protected boolean onDoubleClick(@NotNull MouseEvent event) {
          int row = myTable.rowAtPoint(event.getPoint());
          int column = 1;
          if (row >=0 && row < myTable.getRowCount() && column < myTable.getColumnCount()) {
            Component renderer = myTable.getCellRenderer(row, column)
                                        .getTableCellRendererComponent(myTable, myModel.getValueAt(row, column), false, false, row, column);
            if (renderer instanceof JLabel) {
              StringBuilder sb = new StringBuilder();
              if (component != null) sb.append(UiInspectorUtil.getComponentName(component)).append(" ");
              String value = StringUtil.trimStart(((JLabel)renderer).getText().replace("\r", "").replace("\tat", "\n\tat"), "at ");
              sb.append("'").append(myModel.getValueAt(row, 0)).append("':");
              sb.append(value.contains("\n") || value.length() > 100 ? "\n" : " ");
              sb.append(value);
              //noinspection UseOfSystemOutOrSystemErr
              System.out.println(sb);
              return true;
            }
          }
          return false;
        }
      }.installOn(myTable);

      myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

      add(new JBScrollPane(myTable), BorderLayout.CENTER);
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

        Color fg = isSelected ? table.getSelectionForeground()
                              : changed ? JBUI.CurrentTheme.Link.Foreground.ENABLED
                                        : table.getForeground();
        final JBFont font = JBFont.label();
        setFont(changed ? font.asBold() : font);
        setForeground(fg);
        return this;
      }
    }

    @Override
    public Object getData(@NotNull String dataId) {
      if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
        return new MyInspectorTableCopyProvider();
      }
      return null;
    }

    private class MyInspectorTableCopyProvider implements CopyProvider {
      @Override
      public void performCopy(@NotNull DataContext dataContext) {
        int[] rows = myTable.getSelectedRows();

        StringBuilder builder = new StringBuilder();
        for (int row : rows) {
          if (builder.length() > 0) builder.append('\n');

          for (int col = 0; col < myTable.getColumnCount(); col++) {
            builder.append(getTextValue(row, col));
            if (col < myTable.getColumnCount() - 1) builder.append("\t");
          }
        }

        CopyPasteManager.getInstance().setContents(new TextTransferable(builder.toString()));
      }

      private String getTextValue(int row, int col) {
        Object value = myTable.getValueAt(row, col);
        if (value instanceof String) return (String)value;

        TableColumn tableColumn = myTable.getColumnModel().getColumn(col);
        Component component = tableColumn.getCellRenderer().getTableCellRendererComponent(myTable, value, false, false, row, col);
        if (component instanceof JLabel) { // see ValueCellRenderer
          return ((JLabel)component).getText();
        }
        return value.toString();
      }

      @Override
      public boolean isCopyEnabled(@NotNull DataContext dataContext) {
        return true;
      }

      @Override
      public boolean isCopyVisible(@NotNull DataContext dataContext) {
        return myTable.getSelectedRowCount() > 0;
      }
    }
  }

  private static final class DimensionsComponent extends JComponent {
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

  private static class InspectorTableModel extends AbstractTableModel {
    final Component myComponent;
    final List<PropertyBean> myProperties = new ArrayList<>();

    InspectorTableModel(@NotNull List<? extends PropertyBean> clickInfo) {
      myComponent = null;
      myProperties.addAll(clickInfo);
    }

    InspectorTableModel(@NotNull Component c) {
      myComponent = c;
      myProperties.addAll(ComponentPropertiesCollector.collect(c));
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
      myProperties.addAll(ComponentPropertiesCollector.collect(myComponent));
      fireTableDataChanged();
    }
  }

  private static class UiInspector implements AWTEventListener, Disposable {

    UiInspector(@Nullable Project project) {
      if (project != null) {
        Disposer.register(project, this);
      }
      Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.CONTAINER_EVENT_MASK);
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

    public void showInspector(@Nullable Project project, @NotNull Component c) {
      InspectorWindow window = new InspectorWindow(project, c, this);
      Disposer.register(window, this);
      if (DimensionService.getInstance().getSize(InspectorWindow.getDimensionServiceKey(), null) == null) {
        window.pack();
      }
      window.setVisible(true);
      window.toFront();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
      if (event instanceof ContainerEvent) {
        processContainerEvent((ContainerEvent)event);
      }
    }

    private void processMouseEvent(Project project, MouseEvent me) {
      me.consume();
      Component component = me.getComponent();

      if (component instanceof Container) {
        component = UIUtil.getDeepestComponentAt(component, me.getX(), me.getY());
      }
      else if (component == null) {
        component = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      }
      if (component != null) {
        if (component instanceof JComponent) {
          UIUtil.putClientProperty((JComponent)component, CLICK_INFO, getClickInfo(me, component));
          UIUtil.putClientProperty((JComponent)component, CLICK_INFO_POINT, me.getPoint());
        }

        showInspector(project, component);
      }
    }

    private static Pair<List<PropertyBean>, Component> getClickInfo(MouseEvent me, Component component) {
      if (me.getComponent() == null) return null;
      me = SwingUtilities.convertMouseEvent(me.getComponent(), me, component);
      List<PropertyBean> clickInfo = new ArrayList<>();
      //clickInfo.add(new PropertyBean("Click point", me.getPoint()));
      if (component instanceof JList) {
        @SuppressWarnings("unchecked") 
        JList<Object> list = (JList<Object>)component;
        int row = list.getUI().locationToIndex(list, me.getPoint());
        if (row != -1) {
          Component rendererComponent = list.getCellRenderer()
            .getListCellRendererComponent(list, list.getModel().getElementAt(row), row, list.getSelectionModel().isSelectedIndex(row),
                                          list.hasFocus());
          rendererComponent.setBounds(list.getCellBounds(row, row));
          clickInfo.addAll(findActionsFor(list.getModel().getElementAt(row)));
          clickInfo.add(new PropertyBean(RENDERER_BOUNDS, list.getUI().getCellBounds(list, row, row)));
          clickInfo.addAll(new InspectorTableModel(rendererComponent).myProperties);
          return Pair.create(clickInfo, rendererComponent);
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
          rendererComponent.setBounds(table.getCellRect(row, column, false));
          clickInfo.add(new PropertyBean(RENDERER_BOUNDS, table.getCellRect(row, column, true)));
          clickInfo.addAll(new InspectorTableModel(rendererComponent).myProperties);
          return Pair.create(clickInfo, rendererComponent);
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
          rendererComponent.setBounds(tree.getPathBounds(path));
          clickInfo.add(new PropertyBean(RENDERER_BOUNDS, tree.getPathBounds(path)));
          clickInfo.addAll(new InspectorTableModel(rendererComponent).myProperties);
          return Pair.create(clickInfo, rendererComponent);
        }
      }
      return null;
    }

    private static List<PropertyBean> findActionsFor(Object object) {
      if (object instanceof PopupFactoryImpl.ActionItem) {
        AnAction action = ((PopupFactoryImpl.ActionItem)object).getAction();
        return UiInspectorUtil.collectAnActionInfo(action);
      }
      if (object instanceof QuickFixWrapper) {
        return findActionsFor(((QuickFixWrapper)object).getFix());
      } else if (object instanceof IntentionActionDelegate) {
        IntentionAction delegate = ((IntentionActionDelegate)object).getDelegate();
        if (delegate != object) {
          return findActionsFor(delegate);
        }
      }
      else if (object instanceof IntentionAction) {
        return Collections.singletonList(new PropertyBean("intention action", object.getClass().getName(), true));
      }
      else if (object instanceof QuickFix) {
        return Collections.singletonList(new PropertyBean("quick fix", object.getClass().getName(), true));
      }

      return Collections.emptyList();
    }

    private static void processContainerEvent(ContainerEvent event) {
      Component child = event.getID() == ContainerEvent.COMPONENT_ADDED ? event.getChild() : null;
      if (child instanceof JComponent && !(event.getSource() instanceof CellRendererPane)) {
        UIUtil.putClientProperty((JComponent)child, ADDED_AT_STACKTRACE, new Throwable());
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
        //noinspection UseDPIAwareInsets
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

  public static class ToggleHierarchyTraceAction extends ToggleAction implements AWTEventListener {
    private boolean myEnabled = false;

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (isSelected(e)) {
        e.getPresentation().setText(ActionsBundle.message("action.ToggleUiInspectorHierarchyTrace.text.disable"));
      }
      else {
        e.getPresentation().setText(ActionsBundle.message("action.ToggleUiInspectorHierarchyTrace.text.enable"));
      }
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myEnabled;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state) {
        Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.CONTAINER_EVENT_MASK);
      }
      else {
        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
      }
      myEnabled = state;
    }

    @Override
    public void eventDispatched(AWTEvent event) {
      if (event instanceof ContainerEvent) {
        UiInspector.processContainerEvent((ContainerEvent)event);
      }
    }
  }
}