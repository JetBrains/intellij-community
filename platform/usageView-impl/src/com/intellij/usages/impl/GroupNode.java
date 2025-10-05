// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.pom.Navigatable;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageNodePresentation;
import com.intellij.usages.rules.MergeableUsage;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.*;
import java.util.List;

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

  @Override
  public String toString() {
    String result = getGroup() == null ? "" : getGroup().getPresentableGroupText();
    synchronized (this) {
      return result + ContainerUtil.getFirstItems(myChildren, 10);
    }
  }

  /**
   * Important: Access to the children list should be synchronized on this GroupNode
   */
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
                          @NotNull Consumer<? super UsageViewImpl.NodeChange> edtModelToSwingNodeChangesQueue) {
    synchronized (this) {
      return insertGroupNode(group, ruleIndex, edtModelToSwingNodeChangesQueue);
    }
  }


  private @NotNull GroupNode insertGroupNode(@NotNull UsageGroup group,
                                             int ruleIndex,
                                             @NotNull Consumer<? super UsageViewImpl.NodeChange> edtModelToSwingNodeChangesQueue) {
    synchronized (this) {
      GroupNode newNode = new GroupNode(this, group, ruleIndex);
      int i = getNodeIndex(newNode, this.myChildren);
      if (i >= 0) {
        return (GroupNode)this.myChildren.get(i);
      }
      int insertionIndex = -i - 1;
      this.myChildren.add(insertionIndex, newNode);
      edtModelToSwingNodeChangesQueue.consume(new UsageViewImpl.NodeChange(UsageViewImpl.NodeChangeType.ADDED, this,
                                                                           newNode));
      return newNode;
    }
  }


  // >= 0 if found, < 0 if not found
  private static int getNodeIndex(@NotNull Node newNode, @NotNull List<? extends Node> children) {
    return Collections.binarySearch(children, newNode, COMPARATOR);
  }

  void addTargetsNode(@NotNull Node node, @NotNull DefaultTreeModel treeModel) {
    ThreadingAssertions.assertEventDispatchThread();
    int index;
    synchronized (this) {
      index = getNodeIndex(node, getSwingChildren());
      if(index >= 0) {
        return;
      }
      else index = -index - 1;
      myChildren.add(index, node);
    }
    treeModel.insertNodeInto(node, this, index);
  }

  @Override
  public void removeAllChildren() {
    ThreadingAssertions.assertEventDispatchThread();
    super.removeAllChildren();
    synchronized (this) {
      myChildren.clear();
    }
    myRecursiveUsageCount = 0;
  }

  private @Nullable UsageNode tryMerge(@NotNull Usage usage) {
    if (!(usage instanceof MergeableUsage mergeableUsage)) return null;
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
    ThreadingAssertions.assertEventDispatchThread();
    int removed = 0;
    List<MutableTreeNode> removedNodes = new SmartList<>();
    synchronized (this) {
      int o = 0;
      List<Node> children = myChildren;
      for (int i = 0; i < children.size(); i++) {
        Node child = children.get(i);
        if (usages.remove(child)) {
          removedNodes.add(child);
          removed++;
        }
        else if (child instanceof GroupNode groupNode) {
          int delta = groupNode.removeUsagesBulk(usages, treeModel);
          if (delta > 0) {
            if (groupNode.getRecursiveUsageCount() == 0) {
              removedNodes.add(groupNode);
              if (i != o) {
                children.set(o, child);
              }
              o++;
            }
            removed += delta;
            if (removed == usages.size()) {
              break;
            }
          }
        }
        else {
          if (i != o) {
            children.set(o, child);
          }
          o++;
        }
      }
      children.subList(o, children.size()).clear();
      if (!children.isEmpty()) {
        removeNodesFromParent(treeModel, this, removedNodes);
      }
    }

    if (removed > 0 && (myRecursiveUsageCount -= removed) != 0) {
      treeModel.nodeChanged(this);
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
  @Contract(mutates = "param3")
  private static void removeNodesFromParent(@NotNull DefaultTreeModel treeModel,
                                            @NotNull GroupNode parent,
                                            @NotNull List<MutableTreeNode> nodes) {
    int count = nodes.size();
    if (count == 0) {
      return;
    }
    Object2IntMap<MutableTreeNode> ordering = new Object2IntOpenHashMap<>(count);
    ordering.defaultReturnValue(-1);
    for (MutableTreeNode node : nodes) {
      ordering.put(node, parent.getIndex(node));
    }
    nodes.sort(Comparator.comparingInt(key -> ordering.getInt(key))); // need ascending order
    int[] indices = ordering.values().toIntArray();
    Arrays.sort(indices);
    for (int i = count - 1; i >= 0; i--) {
      parent.remove(indices[i]);
    }
    treeModel.nodesWereRemoved(parent, indices, nodes.toArray());
  }

  @NotNull
  UsageNode addOrGetUsage(@NotNull Usage usage,
                          boolean filterDuplicateLines,
                          @NotNull Consumer<? super UsageViewImpl.NodeChange> edtModelToSwingNodeChangesQueue) {
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
    edtModelToSwingNodeChangesQueue.consume(new UsageViewImpl.NodeChange(UsageViewImpl.NodeChangeType.ADDED, this, newNode));
    return newNode;
  }

  void incrementUsageCount(int i) {
    ThreadingAssertions.assertEventDispatchThread();
    GroupNode groupNode = this;
    while (true) {
      groupNode.myRecursiveUsageCount += i;
      TreeNode parent = groupNode.getParent();
      if (!(parent instanceof GroupNode)) return;
      groupNode = (GroupNode)parent;
    }
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

    private static ClassIndex getClassIndex(@NotNull DefaultMutableTreeNode node) {
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
    ThreadingAssertions.assertEventDispatchThread();
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

  @Override
  protected @NotNull String getNodeText() {
    return getGroup().getPresentableGroupText();
  }

  public synchronized @NotNull Collection<GroupNode> getSubGroups() {
    List<GroupNode> list = new ArrayList<>();
    for (Node n : myChildren) {
      if (n instanceof GroupNode) {
        list.add((GroupNode)n);
      }
    }
    return list;
  }

  public synchronized @NotNull Collection<UsageNode> getUsageNodes() {
    List<UsageNode> list = new ArrayList<>();
    for (Node n : myChildren) {
      if (n instanceof UsageNode) {
        list.add((UsageNode)n);
      }
    }
    return list;
  }

  private volatile UsageNodePresentation myCachedPresentation;

  @ApiStatus.Internal
  @Override
  public @Nullable UsageNodePresentation getCachedPresentation() {
    return myCachedPresentation;
  }

  @Override
  protected void updateCachedPresentation() {
    UsageGroup group = getGroup();
    if (group == null || !group.isValid()) {
      return;
    }
    FileStatus fileStatus = group.getFileStatus();
    Color foregroundColor = fileStatus != null ? fileStatus.getColor() : null;
    Icon icon = group.getIcon();
    List<TextChunk> chunks = new ArrayList<>();
    TextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes();
    attributes.setForegroundColor(foregroundColor);
    chunks.add(new TextChunk(attributes, group.getPresentableGroupText()));
    myCachedPresentation = new UsageNodePresentation(icon, chunks.toArray(TextChunk.EMPTY_ARRAY), null);
  }

  static @NotNull Root createRoot() {
    return new Root();
  }

  static class Root extends GroupNode {
    @Override
    public @NonNls String toString() {
      return "Root " + super.toString();
    }

    @Override
    protected @NotNull String getNodeText() {
      return "";
    }
  }
}
