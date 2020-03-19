// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.ui.CaptionPanel;
import com.intellij.ui.ClickListener;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerSessionTabBase;
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
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XWatchesViewImpl extends XVariablesView implements DnDNativeTarget, XWatchesView {
  private WatchesRootNode myRootNode;

  private final CompositeDisposable myDisposables = new CompositeDisposable();
  private final boolean myWatchesInVariables;

  public XWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables) {
    this(session, watchesInVariables, watchesInVariables);

  }
  public XWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables, boolean vertical) {
    super(session);
    myWatchesInVariables = watchesInVariables;

    XDebuggerTree tree = getTree();
    createNewRootNode(null);

    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XNEW_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XREMOVE_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XCOPY_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XEDIT_WATCH, tree, myDisposables);

    EmptyAction.registerWithShortcutSet(XDebuggerActions.XNEW_WATCH, CommonShortcuts.getNew(), tree);
    EmptyAction.registerWithShortcutSet(XDebuggerActions.XREMOVE_WATCH, CommonShortcuts.getDelete(), tree);

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

    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(
      ActionPlaces.DEBUGGER_TOOLBAR,
      DebuggerSessionTabBase.getCustomizedActionGroup(XDebuggerActions.WATCHES_TREE_TOOLBAR_GROUP),
      !vertical);
    toolbar.setBorder(new CustomLineBorder(CaptionPanel.CNT_ACTIVE_BORDER_COLOR, 0, 0,
                                           vertical ? 0 : 1,
                                           vertical ? 1 : 0));
    toolbar.setTargetComponent(tree);

    if (!myWatchesInVariables) {
      getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.no.watches"));
    }
    getPanel().add(toolbar.getComponent(), vertical ? BorderLayout.WEST : BorderLayout.NORTH);

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
        boolean sameRow = isAboveSelectedItem(event, watchTree, false);
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
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        if (!isAboveSelectedItem(event, watchTree, true)) {
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

  private static boolean isAboveSelectedItem(MouseEvent event, XDebuggerTree watchTree, boolean fullWidth) {
    Rectangle bounds = watchTree.getRowBounds(watchTree.getLeadSelectionRow());
    if (bounds != null) {
      if (fullWidth) {
        bounds.x = 0;
      }
      bounds.width = watchTree.getWidth();
      if (bounds.contains(event.getPoint())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addWatchExpression(@NotNull XExpression expression, int index, final boolean navigateToWatchNode) {
    addWatchExpression(expression, index, navigateToWatchNode, false);
  }

  public void addWatchExpression(@NotNull XExpression expression, int index, final boolean navigateToWatchNode, boolean noDuplicates) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    XDebugSession session = getSession(getTree());
    boolean found = false;
    if (noDuplicates) {
      for (WatchNode child : myRootNode.getWatchChildren()) {
        if (child.getExpression().equals(expression)) {
          TreeUtil.selectNode(getTree(), child);
          found = true;
        }
      }
    }
    if (!found) {
      myRootNode.addWatchExpression(session != null ? session.getCurrentStackFrame() : null, expression, index, navigateToWatchNode);
      updateSessionData();
    }
    if (navigateToWatchNode && session != null) {
      XDebugSessionTab.showWatchesView((XDebugSessionImpl)session);
    }
  }

  public void computeWatches() {
    myRootNode.computeWatches();
  }

  @Override
  protected XValueContainerNode doCreateNewRootNode(@Nullable XStackFrame stackFrame) {
    WatchesRootNode node = new WatchesRootNode(getTree(), this, getExpressions(), stackFrame, myWatchesInVariables);
    myRootNode = node;
    return node;
  }

  @Override
  protected void addEmptyMessage(XValueContainerNode root) {
    if (myWatchesInVariables) {
      super.addEmptyMessage(root);
    }
  }

  @NotNull
  private List<XExpression> getExpressions() {
    XDebuggerTree tree = getTree();
    XDebugSession session = getSession(tree);
    List<XExpression> expressions;
    if (session != null) {
      expressions = ((XDebugSessionImpl)session).getSessionData().getWatchExpressions();
    }
    else {
      XDebuggerTreeNode root = tree.getRoot();
      List<? extends WatchNode> current = root instanceof WatchesRootNode
                                          ? ((WatchesRootNode)tree.getRoot()).getWatchChildren() : Collections.emptyList();
      List<XExpression> list = new SmartList<>();
      for (WatchNode child : current) {
        list.add(child.getExpression());
      }
      expressions = list;
    }
    return expressions;
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (XWatchesView.DATA_KEY.is(dataId)) {
      return this;
    }
    return super.getData(dataId);
  }

  @Override
  public void removeWatches(List<? extends XDebuggerTreeNode> nodes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    myRootNode.removeAllChildren();
    updateSessionData();
  }

  public void moveWatchUp(WatchNode node) {
    myRootNode.moveUp(node);
    updateSessionData();
  }

  public void moveWatchDown(WatchNode node) {
    myRootNode.moveDown(node);
    updateSessionData();
  }

  public void updateSessionData() {
    List<XExpression> expressions = new SmartList<>();
    List<? extends WatchNode> children = myRootNode.getWatchChildren();
    for (WatchNode child : children) {
      expressions.add(child.getExpression());
    }
    XDebugSession session = getSession(getTree());
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
      for (XValueNodeImpl node : (XValueNodeImpl[])object) {
        DebuggerUIUtil.addToWatches(this, node);
      }
    }
    else if (object instanceof EventInfo) {
      String text = ((EventInfo)object).getTextForFlavor(DataFlavor.stringFlavor);
      if (text != null) {
        addWatchExpression(XExpressionImpl.fromText(text), -1, false);
      }
    }
  }
}
