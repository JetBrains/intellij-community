/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.actions.XWatchesTreeActionBase;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.actions.XWatchTransferable;
import com.intellij.xdebugger.impl.ui.tree.nodes.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class XWatchesViewImpl extends XVariablesView implements DnDNativeTarget, XWatchesView {
  private WatchesRootNode myRootNode;

  private final CompositeDisposable myDisposables = new CompositeDisposable();
  private final boolean myWatchesInVariables;

  public XWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables) {
    super(session);
    myWatchesInVariables = watchesInVariables;

    ActionManager actionManager = ActionManager.getInstance();

    XDebuggerTree tree = getTree();
    createNewRootNode(null);

    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XNEW_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XREMOVE_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XCOPY_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XEDIT_WATCH, tree, myDisposables);

    DnDManager.getInstance().registerTarget(this, tree);

    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Object contents = CopyPasteManager.getInstance().getContents(XWatchTransferable.EXPRESSIONS_FLAVOR);
        if (contents instanceof List) {
          for (Object item : ((List)contents)){
            if (item instanceof XExpression) {
              addWatchExpression(((XExpression)item), -1, true);
            }
          }
        }
      }
    }.registerCustomShortcutSet(CommonShortcuts.getPaste(), tree, myDisposables);

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(getTree()).disableUpDownActions();

    decorator.setAddAction(button -> executeAction(XDebuggerActions.XNEW_WATCH));
    decorator.setAddActionName(actionManager.getAction(XDebuggerActions.XNEW_WATCH).getTemplatePresentation().getText());

    AnAction removeWatchAction = actionManager.getAction(XDebuggerActions.XREMOVE_WATCH);
    decorator.setRemoveAction(button -> executeAction(XDebuggerActions.XREMOVE_WATCH));
    decorator.setRemoveActionName(removeWatchAction.getTemplatePresentation().getText());

    decorator.setRemoveActionUpdater(e -> {
      removeWatchAction.update(e);
      return e.getPresentation().isEnabled();
    });
    decorator.addExtraAction(AnActionButton.fromAction(actionManager.getAction(XDebuggerActions.XCOPY_WATCH)));
    decorator.addExtraAction(
      new ToggleActionButton(XDebuggerBundle.message("debugger.session.tab.show.watches.in.variables"), AllIcons.Debugger.Watches) {
        @Override
        public boolean isSelected(AnActionEvent e) {
          XDebugSessionTab tab = session.getSessionTab();
          return tab == null || tab.isWatchesInVariables();
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
          XDebugSessionTab tab = session.getSessionTab();
          if (tab != null) {
            tab.setWatchesInVariables(!tab.isWatchesInVariables());
          }
        }
      });
    decorator.setMoveUpAction(button -> {
      List<? extends WatchNode> nodes = XWatchesTreeActionBase.getSelectedNodes(getTree(), WatchNode.class);
      assert nodes.size() == 1;
      myRootNode.moveUp(nodes.get(0));
      updateSessionData();
    });
    decorator.setMoveUpActionUpdater(e -> {
      List<? extends WatchNode> nodes = XWatchesTreeActionBase.getSelectedNodes(getTree(), WatchNode.class);
      if (nodes.size() != 1) return false;
      return myRootNode.getIndex(nodes.get(0)) > 0;
    });
    decorator.setMoveDownAction(button -> {
      List<? extends WatchNode> nodes = XWatchesTreeActionBase.getSelectedNodes(getTree(), WatchNode.class);
      assert nodes.size() == 1;
      myRootNode.moveDown(nodes.get(0));
      updateSessionData();
    });
    decorator.setMoveDownActionUpdater(e -> {
      List<? extends WatchNode> nodes = XWatchesTreeActionBase.getSelectedNodes(getTree(), WatchNode.class);
      if (nodes.size() != 1) return false;
      return myRootNode.getIndex(nodes.get(0)) < myRootNode.getWatchChildren().size() - 1;
    });
    CustomLineBorder border = new CustomLineBorder(CaptionPanel.CNT_ACTIVE_BORDER_COLOR,
                                                   SystemInfo.isMac ? 1 : 0, 0,
                                                   SystemInfo.isMac ? 0 : 1, 0);
    decorator.setToolbarBorder(border);
    decorator.setPanelBorder(BorderFactory.createEmptyBorder());
    getPanel().removeAll();
    if (myWatchesInVariables) {
      decorator.setToolbarPosition(ActionToolbarPosition.LEFT);
    }
    else {
      getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.no.watches"));
    }
    getPanel().add(decorator.createPanel());

    installEditListeners();
  }

  private void installEditListeners() {
    final XDebuggerTree watchTree = getTree();
    final Alarm quitePeriod = new Alarm();
    final Alarm editAlarm = new Alarm();
    final ClickListener mouseListener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (!SwingUtilities.isLeftMouseButton(event) ||
            ((event.getModifiers() & (InputEvent.SHIFT_MASK | InputEvent.ALT_MASK | InputEvent.CTRL_MASK | InputEvent.META_MASK)) !=0) ) {
          return false;
        }
        boolean sameRow = isAboveSelectedItem(event, watchTree);
        if (!sameRow || clickCount > 1) {
          editAlarm.cancelAllRequests();
          return false;
        }
        final AnAction editWatchAction = ActionManager.getInstance().getAction(XDebuggerActions.XEDIT_WATCH);
        Presentation presentation = editWatchAction.getTemplatePresentation().clone();
        DataContext context = DataManager.getInstance().getDataContext(watchTree);
        final AnActionEvent actionEvent = new AnActionEvent(null, context, "WATCH_TREE", presentation, ActionManager.getInstance(), 0);
        Runnable runnable = () -> editWatchAction.actionPerformed(actionEvent);
        if (editAlarm.isEmpty() && quitePeriod.isEmpty()) {
          editAlarm.addRequest(runnable, UIUtil.getMultiClickInterval());
        } else {
          editAlarm.cancelAllRequests();
        }
        return false;
      }
    };
    final ClickListener mouseEmptySpaceListener = new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        if (!isAboveSelectedItem(event, watchTree)) {
          myRootNode.addNewWatch();
          return true;
        }
        return false;
      }
    };
    ListenerUtil.addClickListener(watchTree, mouseListener);
    ListenerUtil.addClickListener(watchTree, mouseEmptySpaceListener);

    final FocusListener focusListener = new FocusListener() {
      @Override
      public void focusGained(@NotNull FocusEvent e) {
        quitePeriod.addRequest(EmptyRunnable.getInstance(), UIUtil.getMultiClickInterval());
      }

      @Override
      public void focusLost(@NotNull FocusEvent e) {
        editAlarm.cancelAllRequests();
      }
    };
    ListenerUtil.addFocusListener(watchTree, focusListener);

    final TreeSelectionListener selectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(@NotNull TreeSelectionEvent e) {
        quitePeriod.addRequest(EmptyRunnable.getInstance(), UIUtil.getMultiClickInterval());
      }
    };
    watchTree.addTreeSelectionListener(selectionListener);
    myDisposables.add(new Disposable() {
      @Override
      public void dispose() {
        ListenerUtil.removeClickListener(watchTree, mouseListener);
        ListenerUtil.removeClickListener(watchTree, mouseEmptySpaceListener);
        ListenerUtil.removeFocusListener(watchTree, focusListener);
        watchTree.removeTreeSelectionListener(selectionListener);
      }
    });
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDisposables);
    DnDManager.getInstance().unregisterTarget(this, getTree());
    super.dispose();
  }

  private static boolean isAboveSelectedItem(MouseEvent event, XDebuggerTree watchTree) {
    Rectangle bounds = watchTree.getRowBounds(watchTree.getLeadSelectionRow());
    if (bounds != null) {
      bounds.width = watchTree.getWidth();
      if (bounds.contains(event.getPoint())) {
        return true;
      }
    }
    return false;
  }

  private void executeAction(@NotNull String watch) {
    AnAction action = ActionManager.getInstance().getAction(watch);
    Presentation presentation = action.getTemplatePresentation().clone();
    DataContext context = DataManager.getInstance().getDataContext(getTree());

    AnActionEvent actionEvent =
      new AnActionEvent(null, context, ActionPlaces.DEBUGGER_TOOLBAR, presentation, ActionManager.getInstance(), 0);
    action.actionPerformed(actionEvent);
  }

  @Override
  public void addWatchExpression(@NotNull XExpression expression, int index, final boolean navigateToWatchNode) {
    XDebugSession session = getSession(getTree());
    myRootNode.addWatchExpression(session != null ? session.getCurrentStackFrame() : null, expression, index, navigateToWatchNode);
    updateSessionData();
    if (navigateToWatchNode && session != null) {
      XDebugSessionTab.showWatchesView((XDebugSessionImpl)session);
    }
  }

  public void computeWatches() {
    myRootNode.computeWatches();
  }

  @Override
  protected XValueContainerNode createNewRootNode(@Nullable XStackFrame stackFrame) {
    WatchesRootNode node = new WatchesRootNode(getTree(), this, getExpressions(), stackFrame, myWatchesInVariables);
    myRootNode = node;
    getTree().setRoot(node, false);
    return node;
  }

  @Override
  protected void addEmptyMessage(XValueContainerNode root) {
    if (myWatchesInVariables) {
      super.addEmptyMessage(root);
    }
  }

  @NotNull
  private XExpression[] getExpressions() {
    XDebuggerTree tree = getTree();
    XDebugSession session = getSession(tree);
    XExpression[] expressions;
    if (session != null) {
      expressions = ((XDebugSessionImpl)session).getSessionData().getWatchExpressions();
    }
    else {
      XDebuggerTreeNode root = tree.getRoot();
      List<? extends WatchNode> current = root instanceof WatchesRootNode
                                          ? ((WatchesRootNode)tree.getRoot()).getWatchChildren() : Collections.emptyList();
      List<XExpression> list = ContainerUtil.newArrayList();
      for (WatchNode child : current) {
        list.add(child.getExpression());
      }
      expressions = list.toArray(new XExpression[list.size()]);
    }
    return expressions;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (XWatchesView.DATA_KEY.is(dataId)) {
      return this;
    }
    return super.getData(dataId);
  }

  @Override
  public void removeWatches(List<? extends XDebuggerTreeNode> nodes) {
    List<? extends WatchNode> children = myRootNode.getWatchChildren();
    int minIndex = Integer.MAX_VALUE;
    List<XDebuggerTreeNode> toRemove = new ArrayList<>();
    for (XDebuggerTreeNode node : nodes) {
      @SuppressWarnings("SuspiciousMethodCalls")
      int index = children.indexOf(node);
      if (index != -1) {
        toRemove.add(node);
        minIndex = Math.min(minIndex, index);
      }
    }
    myRootNode.removeChildren(toRemove);

    List<? extends WatchNode> newChildren = myRootNode.getWatchChildren();
    if (!newChildren.isEmpty()) {
      WatchNode node = newChildren.get(Math.min(minIndex, newChildren.size() - 1));
      TreeUtil.selectNode(getTree(), node);
    }
    updateSessionData();
  }

  @Override
  public void removeAllWatches() {
    myRootNode.removeAllChildren();
    updateSessionData();
  }

  public void updateSessionData() {
    List<XExpression> watchExpressions = ContainerUtil.newArrayList();
    List<? extends WatchNode> children = myRootNode.getWatchChildren();
    for (WatchNode child : children) {
      watchExpressions.add(child.getExpression());
    }
    XDebugSession session = getSession(getTree());
    XExpression[] expressions = watchExpressions.toArray(new XExpression[watchExpressions.size()]);
    if (session != null) {
      ((XDebugSessionImpl)session).setWatchExpressions(expressions);
    }
    else {
      XDebugSessionData data = getData(XDebugSessionData.DATA_KEY, getTree());
      if (data != null) {
        data.setWatchExpressions(expressions);
      }
    }
  }

  @Override
  public boolean update(final DnDEvent aEvent) {
    Object object = aEvent.getAttachedObject();
    boolean possible = false;
    if (object instanceof XValueNodeImpl[]) {
      possible = true;
      // do not add new watch if node is dragged to itself
      if (((XValueNodeImpl[])object).length == 1) {
        Point point = aEvent.getPoint();
        XDebuggerTree tree = getTree();
        TreePath path = tree.getClosestPathForLocation(point.x, point.y);
        if (path != null && path.getLastPathComponent() == ((XValueNodeImpl[])object)[0]) {
          // the same item is under pointer, filter out place below the tree
          Rectangle pathBounds = tree.getPathBounds(path);
          possible = pathBounds != null && pathBounds.y + pathBounds.height < point.y;
        }
      }
    }
    else if (object instanceof EventInfo) {
      possible = ((EventInfo)object).getTextForFlavor(DataFlavor.stringFlavor) != null;
    }

    aEvent.setDropPossible(possible, XDebuggerBundle.message("xdebugger.drop.text.add.to.watches"));

    return true;
  }

  @Override
  public void drop(DnDEvent aEvent) {
    Object object = aEvent.getAttachedObject();
    if (object instanceof XValueNodeImpl[]) {
      final XValueNodeImpl[] nodes = (XValueNodeImpl[])object;
      for (XValueNodeImpl node : nodes) {
        node.getValueContainer().calculateEvaluationExpression().done(expression -> {
          if (expression != null) {
            //noinspection ConstantConditions
            addWatchExpression(expression, -1, false);
          }
        });
      }
    }
    else if (object instanceof EventInfo) {
      String text = ((EventInfo)object).getTextForFlavor(DataFlavor.stringFlavor);
      if (text != null) {
        //noinspection ConstantConditions
        addWatchExpression(XExpressionImpl.fromText(text), -1, false);
      }
    }
  }

  @Override
  public void cleanUpOnLeave() {
  }

  @Override
  public void updateDraggedImage(final Image image, final Point dropPoint, final Point imageOffset) {
  }
}
