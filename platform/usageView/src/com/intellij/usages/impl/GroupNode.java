// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.usages.CompactGroup;
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
import org.jetbrains.annotations.TestOnly;

import javax.swing.tree.*;
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
  GroupNode addOrGetGroup(@NotNull UsageGroup group,
                          int ruleIndex,
                          @NotNull Consumer<? super UsageViewImpl.NodeChange> edtNodeChangeQueue,
                          @NotNull Consumer<? super Usage> invalidatedUsagesConsumer) {
    synchronized (this) {
      //if a new group is a CompactGroup - try to merge it with the current group if it is also one,
      // otherwise try to merge it with it's children
      if (group instanceof CompactGroup) {
        GroupNode node = makeCompact((CompactGroup)group, ruleIndex, edtNodeChangeQueue, invalidatedUsagesConsumer);
        //was not possible to make compact
        if (node == null) {
          return insertGroupNode(group, ruleIndex, edtNodeChangeQueue);
        }
        return node;
      }
      else {
        return insertGroupNode(group, ruleIndex, edtNodeChangeQueue);
      }
    }
  }

  private boolean isNodeTreePathValid() {
    boolean isValid = true;
    if (!this.isTreePathValid()) {
      isValid = false;
    }
    else {
      if (getParent() != null) {
        isValid = ((Node)getParent()).isTreePathValid();
      }
    }
    return isValid;
  }


  @NotNull
  private GroupNode insertGroupNode(@NotNull UsageGroup group,
                                    int ruleIndex,
                                    @NotNull Consumer<? super UsageViewImpl.NodeChange> edtNodeChangeQueue) {
    synchronized (this) {
      GroupNode newNode = new GroupNode(this, group, ruleIndex);
      int i = getNodeIndex(newNode, this.myChildren);
      if (i >= 0) {
        return (GroupNode)this.myChildren.get(i);
      }
      int insertionIndex = -i - 1;
      this.myChildren.add(insertionIndex, newNode);
      edtNodeChangeQueue.consume(new UsageViewImpl.NodeChange(UsageViewImpl.NodeChangeType.ADDED, this,
                                                              newNode));
      return newNode;
    }
  }


  /**
   * Adds the {@code newGroup} as a new node(containing the group) to the tree, following the logic of compact representation of the group:
   * if it is possible to merge with an existing group - returns a new node containing a new merged group (deletes previously existing node and it's descendants)
   * if the group splits the existing one, then the old one id deleted (with descendants) and the new node and the splitted old one are created
   * if the group is a child of the current node's group - apply makeCompact recursively to the child elements
   *
   * @return the last node to be used to add the next node
   */
  private GroupNode makeCompact(@NotNull CompactGroup newGroup,
                                int ruleIndex,
                                @NotNull Consumer<? super UsageViewImpl.NodeChange> edtNodeChangeQueue,
                                @NotNull Consumer<? super Usage> invalidatedUsagesConsumer) {
    synchronized (this) {
      GroupNode newNode;
      UsageGroup existingGroup = getGroup();
      if (!(existingGroup instanceof CompactGroup)) {
        List<Node> myChildrenCopy = new ArrayList<>(myChildren);
        for (Node n : myChildrenCopy) {
          if (n instanceof GroupNode) {
            existingGroup = ((GroupNode)n).getGroup();
            if (existingGroup instanceof CompactGroup) {
              newNode = ((GroupNode)n).makeCompact(newGroup, ruleIndex, edtNodeChangeQueue, invalidatedUsagesConsumer);
              if (newNode != null) {
                return newNode;
              }
            }
          }
        }
        //not possible to make compact
        return null;
      }

      if (existingGroup.equals(newGroup)) {
        return this;
      }
      if (!newGroup.hasCommonParent((CompactGroup)existingGroup)) {
        return null;
      }

      boolean isNewGroupParentOfExisting = newGroup.isParentOf((CompactGroup)existingGroup);

      //try splitting first
      List<CompactGroup> splitted = ((CompactGroup)existingGroup).split(newGroup, myChildren.isEmpty());

      if (splitted.isEmpty()) {
        //if splitting did not work then merge
        GroupNode newParentNode = this;
        CompactGroup mergedGroup = ((CompactGroup)existingGroup).merge(newGroup);
        if (!mergedGroup.equals(existingGroup)) {
          newParentNode = replaceNode(ruleIndex, (UsageGroup)mergedGroup, edtNodeChangeQueue, invalidatedUsagesConsumer);
        }
        return newParentNode;
      }
      else {
        GroupNode newChildNode2 = null;
        GroupNode newParentNode = this;
        if (splitted.size() == 1) {
          return this;
        }
        if (splitted.size() == 3 || isNewGroupParentOfExisting) {
          newParentNode = replaceNode(ruleIndex, (UsageGroup)splitted.get(0), edtNodeChangeQueue, invalidatedUsagesConsumer);

          newParentNode.insertGroupNode((UsageGroup)splitted.get(1), ruleIndex, edtNodeChangeQueue);
          if (splitted.size() == 3) {
            newChildNode2 = newParentNode.insertGroupNode((UsageGroup)splitted.get(2), ruleIndex, edtNodeChangeQueue);
          }
        }
        else {
          List<Node> children = new ArrayList<>(myChildren);
          for (Node childNode : children) {
            if (childNode instanceof GroupNode) {
              newChildNode2 = ((GroupNode)childNode).makeCompact(splitted.get(1), ruleIndex, edtNodeChangeQueue, invalidatedUsagesConsumer);
              if (newChildNode2 != null) {
                break;
              }
            }
          }
          if (newChildNode2 == null) {
            newChildNode2 = insertGroupNode((UsageGroup)splitted.get(1), ruleIndex, edtNodeChangeQueue);
          }
        }
        if (isNewGroupParentOfExisting) {
          return newParentNode;
        }
        else {
          return newChildNode2;
        }
      }
    }
  }

  @NotNull
  private GroupNode replaceNode(int ruleIndex,
                                @NotNull UsageGroup newGroup,
                                @NotNull Consumer<? super UsageViewImpl.NodeChange> edtNodeChangeQueue,
                                @NotNull Consumer<? super Usage> invalidatedUsagesConsumer) {
    GroupNode newNode;
    GroupNode parentNode = (GroupNode)getParent();

    synchronized (parentNode) {
      invalidateAllChildren(invalidatedUsagesConsumer);
      removeAllNodesRecursively(edtNodeChangeQueue);

      parentNode.getChildren().remove(this);
      edtNodeChangeQueue.consume(new UsageViewImpl.NodeChange(UsageViewImpl.NodeChangeType.REMOVED, this, null));

      newNode = parentNode.insertGroupNode(newGroup, ruleIndex, edtNodeChangeQueue);
    }
    return newNode;
  }

  private void invalidateAllChildren(@NotNull Consumer<? super Usage> usagesToAddAgain) {
    setTreePathValid(false);
    ArrayList<Node> myChildrenCopy;
    synchronized (this) {
      myChildrenCopy = new ArrayList<>(this.myChildren);
    }
    for (Node n : myChildrenCopy) {
      if (n instanceof GroupNode) {
        ((GroupNode)n).invalidateAllChildren(usagesToAddAgain);
      }
      else if (n instanceof UsageNode) {
        n.setTreePathValid(false);
        usagesToAddAgain.consume(((UsageNode)n).getUsage());
      }
    }
  }

  private void removeAllNodesRecursively(@NotNull Consumer<? super UsageViewImpl.NodeChange> edtNodeChangeQueue) {
    ArrayList<Node> myChildrenCopy;
    synchronized (this) {
      myChildrenCopy = new ArrayList<>(this.myChildren);
    }
    for (Node n : myChildrenCopy) {
      if (n instanceof GroupNode) {
        ((GroupNode)n).removeAllNodesRecursively(edtNodeChangeQueue);
      }
      this.myChildren.remove(n);
      edtNodeChangeQueue.consume(new UsageViewImpl.NodeChange(UsageViewImpl.NodeChangeType.REMOVED, n, null));
    }
  }

  // >= 0 if found, < 0 if not found
  private static int getNodeIndex(@NotNull Node newNode, @NotNull List<? extends Node> children) {
    return Collections.binarySearch(children, newNode, COMPARATOR);
  }

  // always >= 0
  private static int getNodeInsertionIndex(@NotNull Node node, @NotNull List<? extends Node> children) {
    int i = getNodeIndex(node, children);
    return i >= 0 ? i : -i - 1;
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


  int removeUsagesBulk(@NotNull Set<? extends UsageNode> usages, @NotNull DefaultTreeModel treeModel) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int removed = 0;
    synchronized (this) {
      Set<UsageNode> usagesToPassFurther = new LinkedHashSet<>();
      List<MutableTreeNode> removedNodes = new SmartList<>();
      for (UsageNode usageNode : usages) {
       if (myChildren.remove(usageNode)) {
          removedNodes.add(usageNode);
          removed++;
        }else{
          usagesToPassFurther.add(usageNode);
        }
      }
      myChildren.removeAll(removedNodes);

      if (!usagesToPassFurther.isEmpty()) {
        for (GroupNode groupNode : getSubGroups()) {
          int delta = groupNode.removeUsagesBulk(usagesToPassFurther, treeModel);
          if (delta > 0) {
            if (groupNode.getRecursiveUsageCount() == 0) {
              myChildren.remove(groupNode);
              removedNodes.add(groupNode);
            }
            removed += delta;
            if (removed == usagesToPassFurther.size()) break;
          }
        }
      }
      removeNodesFromParent(treeModel, this, removedNodes);
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
   *
   * @param treeModel to fire the treeNodesRemoved event on
   * @param parent    the parent
   * @param nodes     must all be children of parent
   */
  public static void removeNodesFromParent(@NotNull DefaultTreeModel treeModel, @NotNull GroupNode parent,
                                           @NotNull List<? extends MutableTreeNode> nodes) {
    ApplicationManager.getApplication().assertIsDispatchThread();

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
    treeModel.nodeStructureChanged(parent);
  }

  @NotNull
  UsageNode addOrGetUsage(@NotNull Usage usage,
                          boolean filterDuplicateLines,
                          @NotNull Consumer<? super UsageViewImpl.NodeChange> edtNodeChangeQueue,
                          @NotNull Consumer<? super Usage> invalidatedUsagesConsumer) {
    if (!isNodeTreePathValid()) {
      invalidatedUsagesConsumer.consume(usage);
      return new UsageNode(this, usage);
    }
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
        int insertionIndex = -i - 1;
        myChildren.add(insertionIndex, newNode);
      }
    }
    edtNodeChangeQueue.consume(new UsageViewImpl.NodeChange(UsageViewImpl.NodeChangeType.ADDED, this, newNode));
    return newNode;
  }

  void incrementUsageCount(int i) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    GroupNode groupNode = this;
    while (true) {
      groupNode.myRecursiveUsageCount += i;
      TreeNode parent = groupNode.getParent();
      if (!(parent instanceof GroupNode)) return;
      groupNode = (GroupNode)parent;
    }
  }

  @Override
  @TestOnly
  public String tree2string(int indent, @NotNull String lineSeparator) {
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
    Enumeration<?> enumeration = children();
    while (enumeration.hasMoreElements()) {
      Object element = enumeration.nextElement();
      if (element instanceof Node && ((Node)element).isReadOnly()) return true;
    }
    return false;
  }

  static class NodeComparator implements Comparator<DefaultMutableTreeNode> {
    enum ClassIndex {UNKNOWN, USAGE_TARGET, GROUP, USAGE}

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
      return "Root " + super.toString();
    }

    @NotNull
    @Override
    protected String getText(@NotNull UsageView view) {
      return "";
    }
  }

}
