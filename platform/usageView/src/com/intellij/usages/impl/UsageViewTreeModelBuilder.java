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
package com.intellij.usages.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class UsageViewTreeModelBuilder extends DefaultTreeModel {
  private final GroupNode.Root myRootNode;
  private final TargetsRootNode myTargetsNode;

  private final UsageTarget[] myTargets;
  private UsageTargetNode[] myTargetNodes;
  private final String myTargetsNodeText;

  public UsageViewTreeModelBuilder(@NotNull UsageViewPresentation presentation, @NotNull UsageTarget[] targets) {
    super(GroupNode.createRoot());
    myRootNode = (GroupNode.Root)root;
    myTargetsNodeText = presentation.getTargetsNodeText();
    myTargets = targets;
    myTargetsNode = myTargetsNodeText == null ? null : new TargetsRootNode(myTargetsNodeText);

    UIUtil.invokeLaterIfNeeded(()->{
      if (myTargetsNodeText != null) {
        addTargetNodes();
      }
      setRoot(myRootNode);
    });
  }

  static class TargetsRootNode extends Node {
    private TargetsRootNode(String name) {
      setUserObject(name);
    }

    @Override
    public String tree2string(int indent, String lineSeparator) {
      return null;
    }

    @Override
    protected boolean isDataValid() {
      return true;
    }

    @Override
    protected boolean isDataReadOnly() {
      return true;
    }

    @Override
    protected boolean isDataExcluded() {
      return false;
    }

    @NotNull
    @Override
    protected String getText(@NotNull UsageView view) {
      return getUserObject().toString();
    }
  }

  private void addTargetNodes() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myTargets.length == 0) return;
    myTargetNodes = new UsageTargetNode[myTargets.length];
    myTargetsNode.removeAllChildren();
    for (int i = 0; i < myTargets.length; i++) {
      UsageTarget target = myTargets[i];
      UsageTargetNode targetNode = new UsageTargetNode(target);
      myTargetsNode.add(targetNode);
      myTargetNodes[i] = targetNode;
    }
    myRootNode.addTargetsNode(myTargetsNode, this);
    reload(myTargetsNode);
  }

  UsageNode getFirstUsageNode() {
    return (UsageNode)getFirstChildOfType(myRootNode, UsageNode.class);
  }

  private static TreeNode getFirstChildOfType(TreeNode parent, final Class type) {
    final int childCount = parent.getChildCount();
    for (int idx = 0; idx < childCount; idx++) {
      final TreeNode child = parent.getChildAt(idx);
      if (type.isAssignableFrom(child.getClass())) {
        return child;
      }
      final TreeNode firstChildOfType = getFirstChildOfType(child, type);
      if (firstChildOfType != null) {
        return firstChildOfType;
      }
    }
    return null;
  }

  boolean areTargetsValid() {
    if (myTargetNodes == null) return true;
    for (UsageTargetNode targetNode : myTargetNodes) {
      if (!targetNode.isValid()) return false;
    }
    return true;
  }

  void reset() {
    myRootNode.removeAllChildren();
    if (myTargetsNodeText != null && myTargets.length > 0) {
      addTargetNodes();
    }
    reload(myRootNode);
  }

  @Override
  public Object getRoot() {
    return myRootNode;
  }

  @Override
  public void nodeChanged(TreeNode node) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.nodeChanged(node);
  }

  @Override
  public void nodesWereInserted(TreeNode node, int[] childIndices) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.nodesWereInserted(node, childIndices);
  }

  @Override
  public void nodesWereRemoved(TreeNode node, int[] childIndices, Object[] removedChildren) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.nodesWereRemoved(node, childIndices, removedChildren);
  }

  @Override
  public void nodesChanged(TreeNode node, int[] childIndices) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.nodesChanged(node, childIndices);
  }

  @Override
  public void nodeStructureChanged(TreeNode node) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.nodeStructureChanged(node);
  }

  @Override
  protected void fireTreeNodesChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.fireTreeNodesChanged(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeNodesInserted(Object source, Object[] path, int[] childIndices, Object[] children) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.fireTreeNodesInserted(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeNodesRemoved(Object source, Object[] path, int[] childIndices, Object[] children) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.fireTreeNodesRemoved(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.fireTreeStructureChanged(source, path, childIndices, children);
  }
}
