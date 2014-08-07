/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class XWatchesViewImpl extends XDebugView implements DnDNativeTarget, XWatchesView {
  private final XDebuggerTreePanel myTreePanel;
  private XDebuggerTreeState myTreeState;
  private XDebuggerTreeRestorer myTreeRestorer;
  private final WatchesRootNode myRootNode;
  @NotNull private final XDebugSessionImpl mySession;
  private final JPanel myDecoratedPanel;
  private final CompositeDisposable myDisposables = new CompositeDisposable();
  private boolean myRebuildNeeded;

  public XWatchesViewImpl(@NotNull final XDebugSessionImpl session) {
    mySession = session;
    myTreePanel = new XDebuggerTreePanel(session.getProject(), session.getDebugProcess().getEditorsProvider(), this, null,
                                         XDebuggerActions.WATCHES_TREE_POPUP_GROUP, ((XDebugSessionImpl)session).getValueMarkers());

    ActionManager actionManager = ActionManager.getInstance();

    XDebuggerTree tree = myTreePanel.getTree();
    actionManager.getAction(XDebuggerActions.XNEW_WATCH).registerCustomShortcutSet(CommonShortcuts.INSERT, tree);
    actionManager.getAction(XDebuggerActions.XREMOVE_WATCH).registerCustomShortcutSet(CommonShortcuts.getDelete(), tree);

    CustomShortcutSet f2Shortcut = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
    actionManager.getAction(XDebuggerActions.XEDIT_WATCH).registerCustomShortcutSet(f2Shortcut, tree);

    DnDManager.getInstance().registerTarget(this, tree);
    myRootNode = new WatchesRootNode(tree, session, this, session.getSessionData().getWatchExpressions());
    tree.setRoot(myRootNode, false);

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTreePanel.getTree()).disableUpDownActions();
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        executeAction(XDebuggerActions.XNEW_WATCH);
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        executeAction(XDebuggerActions.XREMOVE_WATCH);
      }
    });
    CustomLineBorder border = new CustomLineBorder(CaptionPanel.CNT_ACTIVE_BORDER_COLOR,
                                                   SystemInfo.isMac ? 1 : 0, 0,
                                                   SystemInfo.isMac ? 0 : 1, 0);
    decorator.setToolbarBorder(border);
    myDecoratedPanel = new MyPanel(decorator.createPanel());
    myDecoratedPanel.setBorder(null);

    myTreePanel.getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.no.watches"));

    installEditListeners();
  }

  private void installEditListeners() {
    final XDebuggerTree watchTree = myTreePanel.getTree();
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
        final AnAction editWatchAction = ActionManager.getInstance().getAction(XDebuggerActions.XEDIT_WATCH);
        Presentation presentation = editWatchAction.getTemplatePresentation().clone();
        DataContext context = DataManager.getInstance().getDataContext(watchTree);
        final AnActionEvent actionEvent = new AnActionEvent(null, context, "WATCH_TREE", presentation, ActionManager.getInstance(), 0);
        Runnable runnable = new Runnable() {
          public void run() {
            editWatchAction.actionPerformed(actionEvent);
          }
        };
        if (sameRow && editAlarm.isEmpty() && quitePeriod.isEmpty()) {
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
      public void focusGained(FocusEvent e) {
        quitePeriod.addRequest(EmptyRunnable.getInstance(), UIUtil.getMultiClickInterval());
      }

      @Override
      public void focusLost(FocusEvent e) {
        editAlarm.cancelAllRequests();
      }
    };
    ListenerUtil.addFocusListener(watchTree, focusListener);

    final TreeSelectionListener selectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
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
    DnDManager.getInstance().unregisterTarget(this, myTreePanel.getTree());
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
    DataContext context = DataManager.getInstance().getDataContext(myTreePanel.getTree());

    AnActionEvent actionEvent =
      new AnActionEvent(null, context, ActionPlaces.DEBUGGER_TOOLBAR, presentation, ActionManager.getInstance(), 0);
    action.actionPerformed(actionEvent);
  }

  @Override
  public void addWatchExpression(@NotNull XExpression expression, int index, final boolean navigateToWatchNode) {
    myRootNode.addWatchExpression(mySession.getDebugProcess().getEvaluator(), expression, index, navigateToWatchNode);
    updateSessionData();
    if (navigateToWatchNode) {
      showWatchesTab();
    }
  }

  private void showWatchesTab() {
    XDebugSessionTab tab = mySession.getSessionTab();
    if (tab != null) {
      tab.toFront(false);
      // restore watches tab if minimized
      JComponent component = tab.getUi().getComponent();
      if (component instanceof DataProvider) {
        RunnerContentUi ui = RunnerContentUi.KEY.getData(((DataProvider)component));
        if (ui != null) {
          ui.restoreContent(DebuggerContentInfo.WATCHES_CONTENT);
        }
      }
    }
  }

  public boolean rebuildNeeded() {
    return myRebuildNeeded;
  }

  @Override
  public void processSessionEvent(@NotNull final SessionEvent event) {
    if (getMainPanel().isShowing() || ApplicationManager.getApplication().isUnitTestMode()) {
      myRebuildNeeded = false;
    }
    else {
      myRebuildNeeded = true;
      return;
    }

    XStackFrame stackFrame = mySession.getCurrentStackFrame();
    XDebuggerTree tree = myTreePanel.getTree();

    if (event == SessionEvent.BEFORE_RESUME || event == SessionEvent.SETTINGS_CHANGED) {
      if (myTreeRestorer != null) {
        myTreeRestorer.dispose();
      }
      myTreeState = XDebuggerTreeState.saveState(tree);
      if (event == SessionEvent.BEFORE_RESUME) {
        return;
      }
    }

    if (stackFrame != null) {
      cancelClear();
      tree.setSourcePosition(stackFrame.getSourcePosition());
      myRootNode.updateWatches(stackFrame.getEvaluator());
      if (myTreeState != null) {
        myTreeRestorer = myTreeState.restoreState(tree);
      }
    }
    else {
      requestClear();
    }
  }

  protected void clear() {
    getTree().setSourcePosition(null);
    myRootNode.updateWatches(null);
  }

  public XDebuggerTree getTree() {
    return myTreePanel.getTree();
  }

  public JPanel getMainPanel() {
    return myDecoratedPanel;
  }

  @Override
  public void removeWatches(final List<? extends XDebuggerTreeNode> nodes) {
    List<? extends WatchNode> children = myRootNode.getAllChildren();
    int minIndex = Integer.MAX_VALUE;
    List<XDebuggerTreeNode> toRemove = new ArrayList<XDebuggerTreeNode>();
    if (children != null) {
      for (XDebuggerTreeNode node : nodes) {
        @SuppressWarnings("SuspiciousMethodCalls")
        int index = children.indexOf(node);
        if (index != -1) {
          toRemove.add(node);
          minIndex = Math.min(minIndex, index);
        }
      }
    }
    myRootNode.removeChildren(toRemove);

    List<? extends WatchNode> newChildren = myRootNode.getAllChildren();
    if (newChildren != null && !newChildren.isEmpty()) {
      WatchNode node = minIndex < newChildren.size() ? newChildren.get(minIndex) : newChildren.get(newChildren.size() - 1);
      TreeUtil.selectNode(myTreePanel.getTree(), node);
    }
    updateSessionData();
  }

  @Override
  public void removeAllWatches() {
    myRootNode.removeAllChildren();
    updateSessionData();
  }

  private void updateSessionData() {
    List<XExpression> watchExpressions = new ArrayList<XExpression>();
    final List<? extends WatchNode> children = myRootNode.getAllChildren();
    if (children != null) {
      for (WatchNode child : children) {
        watchExpressions.add(child.getExpression());
      }
    }
    mySession.setWatchExpressions(watchExpressions.toArray(new XExpression[watchExpressions.size()]));
  }

  @Override
  public boolean update(final DnDEvent aEvent) {
    Object object = aEvent.getAttachedObject();
    boolean possible = false;
    if (object instanceof XValueNodeImpl[]) {
      possible = true;
    }
    else if (object instanceof EventInfo) {
      possible = ((EventInfo)object).getTextForFlavor(DataFlavor.stringFlavor) != null;
    }

    aEvent.setDropPossible(possible, XDebuggerBundle.message("xdebugger.drop.text.add.to.watches"));

    return true;
  }

  @Override
  public void drop(final DnDEvent aEvent) {
    Object object = aEvent.getAttachedObject();
    if (object instanceof XValueNodeImpl[]) {
      final XValueNodeImpl[] nodes = (XValueNodeImpl[])object;
      for (XValueNodeImpl node : nodes) {
        String expression = node.getValueContainer().getEvaluationExpression();
        if (expression != null) {
          addWatchExpression(XExpressionImpl.fromText(expression), -1, false);
        }
      }
    }
    else if (object instanceof EventInfo) {
      String text = ((EventInfo)object).getTextForFlavor(DataFlavor.stringFlavor);
      if (text != null) {
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

  private class MyPanel extends JPanel implements DataProvider {
    public MyPanel(JPanel panel) {
      setLayout(new BorderLayout());
      add(panel);
      panel.setBorder(null);
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (XWatchesView.DATA_KEY.is(dataId)) {
        return XWatchesViewImpl.this;
      }
      return null;
    }
  }
}
