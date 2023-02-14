// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.inspector.components.HierarchyTree;
import com.intellij.internal.inspector.components.InspectorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ExpandedItemListCellRendererWrapper;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
@IntellijInternalApi
public final class UiInspectorAction extends UiMouseAction implements LightEditCompatible, ActionPromoter {
  private static final String ACTION_ID = "UiInspector";
  public static final String RENDERER_BOUNDS = "clicked renderer";

  public static final Key<DefaultMutableTreeNode> CLICK_INFO = Key.create("CLICK_INFO");
  public static final Key<Point> CLICK_INFO_POINT = Key.create("CLICK_INFO_POINT");
  public static final Key<Throwable> ADDED_AT_STACKTRACE = Key.create("uiInspector.addedAt");

  private static boolean ourGlobalInstanceInitialized = false;

  public static synchronized void initGlobalInspector() {
    if (!ourGlobalInstanceInitialized) {
      ourGlobalInstanceInitialized = true;
      AppUIUtil.invokeOnEdt(() -> {
        new UiInspector(null);
      });
    }
  }

  public UiInspectorAction() {
    super(ACTION_ID);
  }

  @Override
  protected void handleClick(@NotNull Component component, @Nullable MouseEvent event) {
    IdeFrame frame = UIUtil.getParentOfType(IdeFrame.class, component);
    Project project = frame != null ? frame.getProject() : null;
    closeAllInspectorWindows();

    if (event != null) {
      new UiInspector(project).processMouseEvent(project, event);
    }
    else {
      new UiInspector(project).showInspector(project, component);
    }
  }

  @Override
  public @NotNull List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    return ContainerUtil.findAll(actions, o -> o != this);
  }

  private static void closeAllInspectorWindows() {
    Arrays.stream(Window.getWindows())
      .filter(w -> w instanceof InspectorWindow)
      .forEach(w -> Disposer.dispose(((InspectorWindow)w).getInspector()));
  }

  public static class UiInspector implements AWTEventListener, Disposable {

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
        if (component instanceof JComponent jComp) {
          jComp.putClientProperty(CLICK_INFO, getClickInfoNode(me, jComp));
          jComp.putClientProperty(CLICK_INFO_POINT, me.getPoint());
        }

        showInspector(project, component);
      }
    }

    private static DefaultMutableTreeNode getClickInfoNode(MouseEvent me, JComponent component) {
      if (component instanceof UiInspectorPreciseContextProvider contextProvider) {
        Point targetPoint = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), component);
        MouseEvent componentEvent = MouseEventAdapter.convert(me, component);
        UiInspectorPreciseContextProvider.UiInspectorInfo inspectorInfo = contextProvider.getUiInspectorContext(componentEvent);
        if (inspectorInfo != null) {
          String name = ObjectUtils.chooseNotNull(inspectorInfo.name(), "Click Info");
          HierarchyTree.ComponentNode node = HierarchyTree.ComponentNode.createNamedNode(name, inspectorInfo.component());
          if (inspectorInfo.component() != null) inspectorInfo.component().doLayout();
          node.setUserObject(inspectorInfo.values());
          return node;
        }
      }

      Pair<List<PropertyBean>, @NotNull Component> clickInfo = getClickInfo(me, component);
      if (clickInfo != null) {
        //We present clicked renderer as ComponentNode instead of ClickInfoNode to see inner structure of renderer
        HierarchyTree.ComponentNode node = HierarchyTree.ComponentNode.createComponentNode(clickInfo.second);
        clickInfo.second.doLayout();
        node.setUserObject(clickInfo.first);
        return node;
      }
      return null;
    }

    private static Pair<List<PropertyBean>, @NotNull Component> getClickInfo(MouseEvent me, Component component) {
      if (me.getComponent() == null) return null;
      me = SwingUtilities.convertMouseEvent(me.getComponent(), me, component);
      List<PropertyBean> clickInfo = new ArrayList<>();
      //clickInfo.add(new PropertyBean("Click point", me.getPoint()));
      if (component instanceof JList) {
        @SuppressWarnings("unchecked")
        JList<Object> list = (JList<Object>)component;
        int row = list.getUI().locationToIndex(list, me.getPoint());
        if (row != -1) {
          Object value = list.getModel().getElementAt(row);
          ListCellRenderer<? super Object> renderer = ExpandedItemListCellRendererWrapper.unwrap(list.getCellRenderer());

          if (renderer instanceof UiInspectorListRendererContextProvider contextProvider) {
            clickInfo.addAll(contextProvider.getUiInspectorContext(list, value, row));
          }
          if (value instanceof UiInspectorContextProvider contextProvider) {
            clickInfo.addAll(contextProvider.getUiInspectorContext());
          }
          clickInfo.addAll(findActionsFor(value));

          Component rendererComponent = renderer
            .getListCellRendererComponent(list, value, row, list.getSelectionModel().isSelectedIndex(row),
                                          list.hasFocus());
          rendererComponent.setBounds(list.getCellBounds(row, row));
          clickInfo.add(new PropertyBean(RENDERER_BOUNDS, list.getUI().getCellBounds(list, row, row)));
          clickInfo.addAll(ComponentPropertiesCollector.collect(rendererComponent));
          return Pair.create(clickInfo, rendererComponent);
        }
      }
      if (component instanceof JTable table) {
        int row = table.rowAtPoint(me.getPoint());
        int column = table.columnAtPoint(me.getPoint());
        if (row != -1 && column != -1) {
          Object value = table.getValueAt(row, column);
          TableCellRenderer renderer = table.getCellRenderer(row, column);

          if (renderer instanceof UiInspectorTableRendererContextProvider contextProvider) {
            clickInfo.addAll(contextProvider.getUiInspectorContext(table, value, row, column));
          }
          if (value instanceof UiInspectorContextProvider contextProvider) {
            clickInfo.addAll(contextProvider.getUiInspectorContext());
          }

          if (component instanceof TreeTable treeTable) {
            TreeTableTree tree = treeTable.getTree();
            TreeCellRenderer treeRenderer = tree.getOriginalCellRenderer();

            int treeRow = treeTable.convertRowIndexToModel(row);
            Object treeValue = tree.getPathForRow(treeRow).getLastPathComponent();
            if (treeRenderer instanceof UiInspectorTreeRendererContextProvider contextProvider) {
              clickInfo.addAll(contextProvider.getUiInspectorContext(tree, treeValue, treeRow));
            }
            if (treeValue instanceof UiInspectorContextProvider contextProvider) {
              clickInfo.addAll(contextProvider.getUiInspectorContext());
            }
            if (treeValue instanceof DefaultMutableTreeNode mutableTreeNode &&
                mutableTreeNode.getUserObject() instanceof UiInspectorContextProvider contextProvider) {
              clickInfo.addAll(contextProvider.getUiInspectorContext());
            }
          }

          Component rendererComponent = renderer
            .getTableCellRendererComponent(table, value, table.getSelectionModel().isSelectedIndex(row),
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
          int row = tree.getRowForPath(path);
          Object value = path.getLastPathComponent();
          TreeCellRenderer renderer = tree.getCellRenderer();

          if (renderer instanceof UiInspectorTreeRendererContextProvider contextProvider) {
            clickInfo.addAll(contextProvider.getUiInspectorContext(tree, value, row));
          }
          if (value instanceof UiInspectorContextProvider contextProvider) {
            clickInfo.addAll(contextProvider.getUiInspectorContext());
          }
          if (value instanceof DefaultMutableTreeNode mutableTreeNode &&
              mutableTreeNode.getUserObject() instanceof UiInspectorContextProvider contextProvider) {
            clickInfo.addAll(contextProvider.getUiInspectorContext());
          }

          Component rendererComponent = renderer.getTreeCellRendererComponent(
            tree, value, tree.getSelectionModel().isPathSelected(path),
            tree.isExpanded(path),
            tree.getModel().isLeaf(value),
            row, tree.hasFocus());
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
      }
      else if (object instanceof IntentionActionDelegate) {
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
        ((JComponent)child).putClientProperty(ADDED_AT_STACKTRACE, new Throwable());
      }
    }
  }

  static class ToggleHierarchyTraceAction extends ToggleAction implements AWTEventListener {

    private boolean myEnabled = false;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText(isSelected(e) ?
                                  ActionsBundle.message("action.ToggleUiInspectorHierarchyTrace.text.disable") :
                                  ActionsBundle.message("action.ToggleUiInspectorHierarchyTrace.text.enable"));
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