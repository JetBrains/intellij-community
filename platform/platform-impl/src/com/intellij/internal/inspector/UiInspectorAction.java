// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.InternalActionsBundle;
import com.intellij.internal.inspector.components.HierarchyTree;
import com.intellij.internal.inspector.components.InspectorTable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.extensions.PluginId;
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
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
              }
              else if (myInspectorTable.getTable().hasFocus()) {
                int row = myInspectorTable.getTable().getSelectedRow();
                Object at = myInspectorTable.getModel().getValueAt(row, 1);
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
          clickInfo.addAll(ComponentPropertiesCollector.collect(rendererComponent));
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
          clickInfo.addAll(ComponentPropertiesCollector.collect(rendererComponent));
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
          clickInfo.addAll(ComponentPropertiesCollector.collect(rendererComponent));
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