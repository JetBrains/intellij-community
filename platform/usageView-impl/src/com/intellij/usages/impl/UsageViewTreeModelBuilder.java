// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class UsageViewTreeModelBuilder extends DefaultTreeModel {
  private final GroupNode.Root myRootNode;

  private final @Nullable TargetsRootNode myTargetsNode;
  private final UsageTarget[] myTargets;
  private final List<UsageTargetNode> myTargetNodes = new CopyOnWriteArrayList<>();

  UsageViewTreeModelBuilder(@NotNull UsageViewPresentation presentation, UsageTarget @NotNull [] targets) {
    super(GroupNode.createRoot());
    myRootNode = (GroupNode.Root)root;

    String targetsNodeText = presentation.getTargetsNodeText();
    myTargetsNode = targetsNodeText == null ? null : new TargetsRootNode(targetsNodeText);
    myTargets = targets;

    UIUtil.invokeLaterIfNeeded(() -> {
      addTargetNodes();
      setRoot(myRootNode);
    });
  }

  static final class TargetsRootNode extends Node {
    private TargetsRootNode(@NotNull String name) {
      setUserObject(name);
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

    @Override
    protected boolean canDataNavigate() {
      return false;
    }

    @Override
    protected @NotNull String getNodeText() {
      return getUserObject().toString();
    }
  }

  private void addTargetNodes() {
    if (myTargetsNode == null || myTargets.length == 0) {
      return;
    }
    ThreadingAssertions.assertEventDispatchThread();
    myTargetNodes.clear();
    myTargetsNode.removeAllChildren();
    for (UsageTarget target : myTargets) {
      UsageTargetNode targetNode = new UsageTargetNode(target);
      myTargetsNode.add(targetNode);
      myTargetNodes.add(targetNode);
    }
    myRootNode.addTargetsNode(myTargetsNode, this);
    reload(myTargetsNode);
  }

  UsageNode getFirstUsageNode() {
    return getFirstUsageNode(myRootNode);
  }

  private static UsageNode getFirstUsageNode(@NotNull GroupNode parent) {
    Node found;
    synchronized (parent) {
      found = ContainerUtil.find(parent.getChildren(), c -> c instanceof UsageNode || c instanceof GroupNode);
    }
    return (found instanceof GroupNode groupNode) ? getFirstUsageNode(groupNode) : (UsageNode)found;
  }

  boolean areTargetsValid() {
    for (UsageTargetNode targetNode : myTargetNodes) {
      if (!targetNode.isValid()) return false;
    }
    return true;
  }

  @RequiresBackgroundThread
  void updateTargetNodes(@NotNull Consumer<Node> callback) {
    for (UsageTargetNode node : myTargetNodes) {
      node.update(callback);
    }
  }

  void reset() {
    myRootNode.removeAllChildren();
    addTargetNodes();
    reload(myRootNode);
  }

  @Override
  public @NotNull Object getRoot() {
    return myRootNode;
  }

  @Override
  public void nodeChanged(TreeNode node) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodeChanged(node);
  }

  @Override
  public void nodesWereInserted(TreeNode node, int[] childIndices) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodesWereInserted(node, childIndices);
  }

  @Override
  public void nodesWereRemoved(TreeNode node, int[] childIndices, Object[] removedChildren) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodesWereRemoved(node, childIndices, removedChildren);
  }

  @Override
  public void nodesChanged(TreeNode node, int[] childIndices) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodesChanged(node, childIndices);
  }

  @Override
  public void nodeStructureChanged(TreeNode node) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodeStructureChanged(node);
  }

  @Override
  protected void fireTreeNodesChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
    ThreadingAssertions.assertEventDispatchThread();
    super.fireTreeNodesChanged(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeNodesInserted(Object source, Object[] path, int[] childIndices, Object[] children) {
    ThreadingAssertions.assertEventDispatchThread();
    super.fireTreeNodesInserted(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeNodesRemoved(Object source, Object[] path, int[] childIndices, Object[] children) {
    ThreadingAssertions.assertEventDispatchThread();
    super.fireTreeNodesRemoved(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
    ThreadingAssertions.assertEventDispatchThread();
    super.fireTreeStructureChanged(source, path, childIndices, children);
  }
}
