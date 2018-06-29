// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InstancesTree extends XDebuggerTree {
  private final XValueNodeImpl myRoot;
  private final Runnable myOnRootExpandAction;
  private List<XValueChildrenList> myChildren;

  public InstancesTree(@NotNull Project project,
                       @NotNull XDebuggerEditorsProvider editorsProvider,
                       @Nullable XValueMarkers<?, ?> valueMarkers,
                       @NotNull Runnable onRootExpand) {
    super(project, editorsProvider, null, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, valueMarkers);
    myOnRootExpandAction = onRootExpand;
    myRoot = new XValueNodeImpl(this, null, "root", new MyRootValue());

    myRoot.children();
    setRoot(myRoot, false);
    myRoot.setLeaf(false);
    setSelectionRow(0);
    expandNodesOnLoad(node -> node == myRoot);
  }

  public void addChildren(@NotNull XValueChildrenList children, boolean last) {
    if (myChildren == null) {
      myChildren = new ArrayList<>();
    }

    myChildren.add(children);
    myRoot.addChildren(children, last);
  }

  void rebuildTree(@NotNull RebuildPolicy policy, @NotNull XDebuggerTreeState state) {
    if (policy == RebuildPolicy.RELOAD_INSTANCES) {
      myChildren = null;
    }

    rebuildAndRestore(state);
  }

  public void rebuildTree(@NotNull RebuildPolicy policy) {
    rebuildTree(policy, XDebuggerTreeState.saveState(this));
  }

  void setInfoMessage(@SuppressWarnings("SameParameterValue") @NotNull String text) {
    myChildren = null;
    myRoot.clearChildren();
    myRoot.setMessage(text, XDebuggerUIConstants.INFORMATION_MESSAGE_ICON, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
  }



  public enum RebuildPolicy {
    RELOAD_INSTANCES, ONLY_UPDATE_LABELS
  }

  private class MyRootValue extends XValue {
    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      if (myChildren == null) {
        myOnRootExpandAction.run();
      }
      else {
        for (XValueChildrenList children : myChildren) {
          myRoot.addChildren(children, false);
        }

        myRoot.addChildren(XValueChildrenList.EMPTY, true);
      }
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      node.setPresentation(null, "", "", true);
    }
  }
}
