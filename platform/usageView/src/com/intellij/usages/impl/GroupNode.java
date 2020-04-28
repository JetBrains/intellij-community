// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.MergeableUsage;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

public class GroupNode extends Node implements Navigatable, Comparable<GroupNode> {
  private static final NodeComparator COMPARATOR = new NodeComparator();
  private final int myRuleIndex;
  private int myRecursiveUsageCount; // EDT only access
  private final List<Node> myChildren = new SmartList<>(); // guarded by this

  private GroupNode(@NotNull Node parent, @NotNull UsageGroup group, int ruleIndex) {
    setUserObject(group);
    setParent(parent);
    myRuleIndex = ruleIndex;
  }

  // only for root fake node
  private GroupNode() {
    myRuleIndex = 0;
  }

  @Override
  protected void updateNotify() {
    if (getGroup() != null) {
      getGroup().update();
    }
  }

  public String toString() {
    String result = getGroup() == null ? "" : getGroup().getText(null);
    synchronized (this) {
      return result + ContainerUtil.getFirstItems(myChildren, 10);
    }
  }

  @NotNull
  List<Node> getChildren() {
    return myChildren;
  }

  @NotNull
  List<Node> getSwingChildren() {
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Node> children = (List)this.children;
    return ObjectUtils.notNull(children, Collections.emptyList());
  }

  @NotNull
  GroupNode addOrGetGroup(@NotNull UsageGroup group, int ruleIndex, @NotNull Consumer<? super Node> edtInsertedUnderQueue) {
    GroupNode newNode;
    synchronized (this) {
      newNode = new GroupNode(this, group, ruleIndex);
      int i = getNodeIndex(newNode, myChildren);
      if (i >= 0) {
        return (GroupNode)myChildren.get(i);
      }
      int insertionIndex = -i - 1;
      myChildren.add(insertionIndex, newNode);
    }
    edtInsertedUnderQueue.consume(this);
    return newNode;
  }

  // >= 0 if found, < 0 if not found
  private static int getNodeIndex(@NotNull Node newNode, @NotNull List<? extends Node> children) {
    return Collections.binarySearch(children, newNode, COMPARATOR);
  }

  // always >= 0
  private static int getNodeInsertionIndex(@NotNull Node node, @NotNull List<? extends Node> children) {
    int i = getNodeIndex(node, children);
    return i >= 0 ? i : -i-1;
  }

  void addTargetsNode(@NotNull Node node, @NotNull DefaultTreeModel treeModel) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int index;
    synchronized (this) {
      index = getNodeInsertionIndex(node, getSwingChildren());
      myChildren.add(index, node);
    }
    treeModel.insertNodeInto(node, this, index);
  }

  @Override
  public void removeAllChildren() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.removeAllChildren();
    synchronized (this) {
      myChildren.clear();
    }
    myRecursiveUsageCount = 0;
  }

  @Nullable
  private UsageNode tryMerge(@NotNull Usage usage) {
    if (!(usage instanceof MergeableUsage)) return null;
    MergeableUsage mergeableUsage = (MergeableUsage)usage;
    for (UsageNode node : getUsageNodes()) {
      Usage original = node.getUsage();
      if (original == mergeableUsage) {
        // search returned duplicate usage, ignore
        return node;
      }
      if (original instanceof MergeableUsage) {
        if (((MergeableUsage)original).merge(mergeableUsage)) return node;
      }
    }

    return null;
  }

  int removeUsagesBulk(@NotNull Set<UsageNode> usages, @NotNull DefaultTreeModel treeModel) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int removed = 0;
    synchronized (this) {
      List<MutableTreeNode> removedNodes = new SmartList<>();
      for (UsageNode usage : usages) {
        if (myChildren.remove(usage)) {
          removedNodes.add(usage);
          removed++;
        }
      }

      if (removed == 0) {
        for (GroupNode groupNode : getSubGroups()) {
          int delta = groupNode.removeUsagesBulk(usages, treeModel);
          if (delta > 0) {
            if (groupNode.getRecursiveUsageCount() == 0) {
              myChildren.remove(groupNode);
              removedNodes.add(groupNode);
            }
            removed += delta;
            if (removed == usages.size()) break;
          }
        }
      }
      if (!myChildren.isEmpty()) {
        removeNodesFromParent(treeModel, this, removedNodes);
      }
    }

    if (removed > 0) {
      myRecursiveUsageCount -= removed;
      if (myRecursiveUsageCount != 0) {
        treeModel.nodeChanged(this);
      }
    }

    return removed;
  }

  /**
   * Implementation of javax.swing.tree.DefaultTreeModel#removeNodeFromParent(javax.swing.tree.MutableTreeNode) for multiple nodes.
   * Fires a single event, or does nothing when nodes is empty.
   * @param treeModel  to fire the treeNodesRemoved event on
   * @param parent  the parent
   * @param nodes  must all be children of parent
   */
  private static void removeNodesFromParent(@NotNull DefaultTreeModel treeModel, @NotNull GroupNode parent,
                                            @NotNull List<? extends MutableTreeNode> nodes) {
    int count = nodes.size();
    if (count == 0) {
      return;
    }
    ObjectIntHashMap<MutableTreeNode> ordering = new ObjectIntHashMap<>(count);
    for (MutableTreeNode node : nodes) {
      ordering.put(node, parent.getIndex(node));
    }
    nodes.sort(Comparator.comparingInt(ordering::get)); // need ascending order
    int[] indices = ordering.getValues();
    Arrays.sort(indices);
    for (int i = count - 1; i >= 0; i--) {
      parent.remove(indices[i]);
    }
    treeModel.nodesWereRemoved(parent, indices, nodes.toArray());
  }

  @NotNull
  UsageNode addOrGetUsage(@NotNull Usage usage,
                          boolean filterDuplicateLines,
                          @NotNull Consumer<? super Node> edtInsertedUnderQueue) {
    UsageNode newNode;
    synchronized (this) {
      if (filterDuplicateLines) {
        UsageNode mergedWith = tryMerge(usage);
        if (mergedWith != null) {
          return mergedWith;
        }
      }
      newNode = new UsageNode(this, usage);
      int i = getNodeIndex(newNode, myChildren);
      // i>=0 means the usage already there (might happen when e.g. find usages was interrupted by typing and resumed with the same file)
      if (i >= 0) {
        newNode = (UsageNode)myChildren.get(i);
      }
      else {
        int insertionIndex = -i-1;
        myChildren.add(insertionIndex, newNode);
      }
    }
    edtInsertedUnderQueue.consume(this);
    return newNode;
  }

  void incrementUsageCount() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    GroupNode groupNode = this;
    while (true) {
      groupNode.myRecursiveUsageCount++;
      TreeNode parent = groupNode.getParent();
      if (!(parent instanceof GroupNode)) return;
      groupNode = (GroupNode)parent;
    }
  }

  @Override
  public String tree2string(int indent, String lineSeparator) {
    StringBuffer result = new StringBuffer();
    StringUtil.repeatSymbol(result, ' ', indent);

    if (getGroup() != null) result.append(getGroup());
    result.append("[");
    result.append(lineSeparator);

    for (Node node : myChildren) {
      result.append(node.tree2string(indent + 4, lineSeparator));
      result.append(lineSeparator);
    }

    StringUtil.repeatSymbol(result, ' ', indent);
    result.append("]");
    result.append(lineSeparator);

    return result.toString();
  }

  @Override
  protected boolean isDataValid() {
    UsageGroup group = getGroup();
    return group == null || group.isValid();
  }

  @Override
  protected boolean isDataReadOnly() {
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      Object element = enumeration.nextElement();
      if (element instanceof Node && ((Node)element).isReadOnly()) return true;
    }
    return false;
  }

  private static class NodeComparator implements Comparator<DefaultMutableTreeNode> {
    enum ClassIndex { UNKNOWN, USAGE_TARGET, GROUP, USAGE }
    private static ClassIndex getClassIndex(DefaultMutableTreeNode node) {
      if (node instanceof UsageNode) return ClassIndex.USAGE;
      if (node instanceof GroupNode) return ClassIndex.GROUP;
      if (node instanceof UsageTargetNode) return ClassIndex.USAGE_TARGET;
      return ClassIndex.UNKNOWN;
    }

    @Override
    public int compare(DefaultMutableTreeNode n1, DefaultMutableTreeNode n2) {
      ClassIndex classIdx1 = getClassIndex(n1);
      ClassIndex classIdx2 = getClassIndex(n2);
      if (classIdx1 != classIdx2) return classIdx1.compareTo(classIdx2);
      if (classIdx1 == ClassIndex.GROUP) {
        int c = ((GroupNode)n1).compareTo((GroupNode)n2);
        if (c != 0) return c;
      }
      else if (classIdx1 == ClassIndex.USAGE) {
        int c = ((UsageNode)n1).compareTo((UsageNode)n2);
        if (c != 0) return c;
      }

      // return 0 only for the same Usages inside
      // (e.g. when tried to insert the UsageNode for the same Usage when interrupted by write action and resumed)
      Object u1 = n1.getUserObject();
      Object u2 = n2.getUserObject();
      if (Comparing.equal(u1, u2)) return 0;
      return System.identityHashCode(u1) - System.identityHashCode(u2);
    }
  }

  @Override
  public int compareTo(@NotNull GroupNode groupNode) {
    if (myRuleIndex == groupNode.myRuleIndex) {
      return getGroup().compareTo(groupNode.getGroup());
    }

    return Integer.compare(myRuleIndex, groupNode.myRuleIndex);
  }

  public synchronized UsageGroup getGroup() {
    return (UsageGroup)getUserObject();
  }

  int getRecursiveUsageCount() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myRecursiveUsageCount;
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (getGroup() != null) {
      getGroup().navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    return getGroup() != null && getGroup().canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getGroup() != null && getGroup().canNavigateToSource();
  }


  @Override
  protected boolean isDataExcluded() {
    for (Node node : myChildren) {
      if (!node.isExcluded()) return false;
    }
    return true;
  }

  @NotNull
  @Override
  protected String getText(@NotNull UsageView view) {
    return getGroup().getText(view);
  }

  @NotNull
  public synchronized Collection<GroupNode> getSubGroups() {
    List<GroupNode> list = new ArrayList<>();
    for (Node n : myChildren) {
      if (n instanceof GroupNode) {
        list.add((GroupNode)n);
      }
    }
    return list;
  }

  @NotNull
  public synchronized Collection<UsageNode> getUsageNodes() {
    List<UsageNode> list = new ArrayList<>();
    for (Node n : myChildren) {
      if (n instanceof UsageNode) {
        list.add((UsageNode)n);
      }
    }
    return list;
  }

  @NotNull
  static Root createRoot() {
    return new Root();
  }

  static class Root extends GroupNode {
    @NonNls
    public String toString() {
      return "Root "+super.toString();
    }

    @NotNull
    @Override
    protected String getText(@NotNull UsageView view) {
      return "";
    }
  }
}
