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

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class XWatchesView extends XDebugViewBase implements DnDNativeTarget {
  private final XDebuggerTreePanel myTreePanel;
  private XDebuggerTreeState myTreeState;
  private XDebuggerTreeRestorer myTreeRestorer;
  private final WatchesRootNode myRootNode;
  private final XDebugSessionData mySessionData;

  public XWatchesView(final XDebugSession session, final Disposable parentDisposable, final XDebugSessionData sessionData) {
    super(session, parentDisposable);
    mySessionData = sessionData;
    myTreePanel = new XDebuggerTreePanel(session, session.getDebugProcess().getEditorsProvider(), this, null,
                                         XDebuggerActions.WATCHES_TREE_POPUP_GROUP);

    ActionManager actionManager = ActionManager.getInstance();

    XDebuggerTree tree = myTreePanel.getTree();
    actionManager.getAction(XDebuggerActions.XNEW_WATCH).registerCustomShortcutSet(CommonShortcuts.INSERT, tree);
    actionManager.getAction(XDebuggerActions.XREMOVE_WATCH).registerCustomShortcutSet(CommonShortcuts.DELETE, tree);

    CustomShortcutSet f2Shortcut = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
    actionManager.getAction(XDebuggerActions.XEDIT_WATCH).registerCustomShortcutSet(f2Shortcut, tree);

    DnDManager.getInstance().registerTarget(this, tree);
    myRootNode = new WatchesRootNode(tree, sessionData.getWatchExpressions());
    tree.setRoot(myRootNode, false);

    myTreePanel.getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.no.watches"));

  }

  public void addWatchExpression(@NotNull String expression, int index, final boolean navigateToWatchNode) {
    XDebuggerEvaluator evaluator = null;
    XStackFrame stackFrame = mySession.getCurrentStackFrame();
    if (stackFrame != null) {
      evaluator = stackFrame.getEvaluator();
    }
    myRootNode.addWatchExpression(evaluator, expression, index, navigateToWatchNode);
    updateSessionData();
  }

  protected void rebuildView(final SessionEvent event) {
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
      tree.setSourcePosition(stackFrame.getSourcePosition());
      myRootNode.updateWatches(stackFrame.getEvaluator());
      if (myTreeState != null) {
        myTreeRestorer = myTreeState.restoreState(tree);
      }
    }
    else {
      tree.setSourcePosition(null);
      myRootNode.updateWatches(null);
    }
  }

  @Override
  public void dispose() {
    DnDManager.getInstance().unregisterTarget(this, myTreePanel.getTree());
    super.dispose();
  }

  public XDebuggerTree getTree() {
    return myTreePanel.getTree();
  }

  public JPanel getMainPanel() {
    return myTreePanel.getMainPanel();
  }

  public void removeWatches(final List<? extends XDebuggerTreeNode> nodes) {
    List<? extends WatchNode> children = myRootNode.getAllChildren();
    int minIndex = Integer.MAX_VALUE;
    if (children != null) {
      for (XDebuggerTreeNode node : nodes) {
        int index = children.indexOf(node);
        if (index != -1) {
          minIndex = Math.min(minIndex, index);
        }
      }
    }
    myRootNode.removeChildren(nodes);

    List<? extends WatchNode> newChildren = myRootNode.getAllChildren();
    if (newChildren != null && newChildren.size() > 0) {
      WatchNode node = minIndex < newChildren.size() ? newChildren.get(minIndex) : newChildren.get(newChildren.size() - 1);
      TreeUtil.selectNode(myTreePanel.getTree(), node);
    }
    updateSessionData();
  }

  public void removeAllWatches() {
    myRootNode.removeAllChildren();
    updateSessionData();
  }

  private void updateSessionData() {
    List<String> watchExpressions = new ArrayList<String>();
    final List<? extends WatchNode> children = myRootNode.getAllChildren();
    if (children != null) {
      for (WatchNode child : children) {
        watchExpressions.add(child.getExpression());
      }
    }
    mySessionData.setWatchExpressions(ArrayUtil.toStringArray(watchExpressions));
  }

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

  public void drop(final DnDEvent aEvent) {
    Object object = aEvent.getAttachedObject();
    if (object instanceof XValueNodeImpl[]) {
      final XValueNodeImpl[] nodes = (XValueNodeImpl[])object;
      for (XValueNodeImpl node : nodes) {
        String expression = node.getValueContainer().getEvaluationExpression();
        if (expression != null) {
          addWatchExpression(expression, -1, false);
        }
      }
    }
    else if (object instanceof EventInfo) {
      String text = ((EventInfo)object).getTextForFlavor(DataFlavor.stringFlavor);
      if (text != null) {
        addWatchExpression(text, -1, false);
      }
    }
  }

  public void cleanUpOnLeave() {
  }

  public void updateDraggedImage(final Image image, final Point dropPoint, final Point imageOffset) {
  }
}
