/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.MergeableUsage;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * @author max
 */
public class GroupNode extends Node implements Navigatable, Comparable<GroupNode> {
  private static final NodeComparator COMPARATOR = new NodeComparator();
  private final Object lock = new Object();
  private final UsageGroup myGroup;
  private final int myRuleIndex;
  private final Map<UsageGroup, GroupNode> mySubgroupNodes = new THashMap<UsageGroup, GroupNode>();
  private final List<UsageNode> myUsageNodes = new SmartList<UsageNode>();
  @NotNull private final UsageViewTreeModelBuilder myUsageTreeModel;
  private volatile int myRecursiveUsageCount = 0;

  public GroupNode(@Nullable UsageGroup group, int ruleIndex, @NotNull UsageViewTreeModelBuilder treeModel) {
    super(treeModel);
    myUsageTreeModel = treeModel;
    setUserObject(group);
    myGroup = group;
    myRuleIndex = ruleIndex;
  }

  @Override
  protected void updateNotify() {
    if (myGroup != null) {
      myGroup.update();
    }
  }

  public String toString() {
    String result = "";
    if (myGroup != null) result = myGroup.getText(null);
    if (children == null) {
      return result;
    }
    return result + children.subList(0, Math.min(10, children.size())).toString();
  }

  public GroupNode addGroup(@NotNull UsageGroup group, int ruleIndex, @NotNull Consumer<Runnable> edtQueue) {
    synchronized (lock) {
      GroupNode node = mySubgroupNodes.get(group);
      if (node == null) {
        final GroupNode node1 = node = new GroupNode(group, ruleIndex, getBuilder());
        mySubgroupNodes.put(group, node);

        addNode(node1, edtQueue);
      }
      return node;
    }
  }

  void addNode(@NotNull final DefaultMutableTreeNode node, @NotNull Consumer<Runnable> edtQueue) {
    if (!getBuilder().isDetachedMode()) {
      edtQueue.consume(new Runnable() {
        @Override
        public void run() {
          myTreeModel.insertNodeInto(node, GroupNode.this, getNodeInsertionIndex(node));
        }
      });
    }
  }

  private UsageViewTreeModelBuilder getBuilder() {
    return (UsageViewTreeModelBuilder)myTreeModel;
  }

  @Override
  public void removeAllChildren() {
    synchronized (lock) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      super.removeAllChildren();
      mySubgroupNodes.clear();
      myRecursiveUsageCount = 0;
      myUsageNodes.clear();
    }
    myTreeModel.reload(this);
  }

  @Nullable UsageNode tryMerge(@NotNull Usage usage) {
    if (!(usage instanceof MergeableUsage)) return null;
    MergeableUsage mergeableUsage = (MergeableUsage)usage;
    for (UsageNode node : myUsageNodes) {
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

  public boolean removeUsage(@NotNull UsageNode usage) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Collection<GroupNode> groupNodes = mySubgroupNodes.values();
    for(Iterator<GroupNode> iterator = groupNodes.iterator();iterator.hasNext();) {
      final GroupNode groupNode = iterator.next();

      if(groupNode.removeUsage(usage)) {
        doUpdate();

        if (groupNode.getRecursiveUsageCount() == 0) {
          myTreeModel.removeNodeFromParent(groupNode);
          iterator.remove();
        }
        return true;
      }
    }

    boolean removed;
    synchronized (lock) {
      removed = myUsageNodes.remove(usage);
    }
    if (removed) {
      doUpdate();
      return true;
    }

    return false;
  }

  public boolean removeUsagesBulk(@NotNull Set<UsageNode> usages) {
    boolean removed;
    synchronized (lock) {
      removed = myUsageNodes.removeAll(usages);
    }

    Collection<GroupNode> groupNodes = mySubgroupNodes.values();

    for (Iterator<GroupNode> iterator = groupNodes.iterator(); iterator.hasNext(); ) {
      GroupNode groupNode = iterator.next();

      if (groupNode.removeUsagesBulk(usages)) {
        if (groupNode.getRecursiveUsageCount() == 0) {
          MutableTreeNode parent = (MutableTreeNode)groupNode.getParent();
          int childIndex = parent.getIndex(groupNode);
          if (childIndex != -1) {
            parent.remove(childIndex);
          }
          iterator.remove();
        }
        removed = true;
      }
    }
    if (removed) {
      --myRecursiveUsageCount;
    }
    return removed;
  }

  private void doUpdate() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    --myRecursiveUsageCount;
    myTreeModel.nodeChanged(this);
  }

  public UsageNode addUsage(@NotNull Usage usage, @NotNull Consumer<Runnable> edtQueue) {
    final UsageNode node;
    synchronized (lock) {
      if (myUsageTreeModel.isFilterDuplicatedLine()) {
        UsageNode mergedWith = tryMerge(usage);
        if (mergedWith != null) {
          return mergedWith;
        }
      }
      node = new UsageNode(usage, getBuilder());
      myUsageNodes.add(node);
    }

    if (!getBuilder().isDetachedMode()) {
      edtQueue.consume(new Runnable() {
        @Override
        public void run() {
          myTreeModel.insertNodeInto(node, GroupNode.this, getNodeIndex(node));
          incrementUsageCount();
        }
      });
    }
    return node;
  }

  private int getNodeIndex(@NotNull UsageNode node) {
    int index = indexedBinarySearch(node);
    return index >= 0 ? index : -index-1;
  }

  @SuppressWarnings("Duplicates")
  private int indexedBinarySearch(@NotNull UsageNode key) {
    int low = 0;
    int high = getChildCount() - 1;

    while (low <= high) {
      int mid = (low + high) / 2;
      TreeNode treeNode = getChildAt(mid);
      int cmp = treeNode instanceof UsageNode ? ((UsageNode)treeNode).compareTo(key) : -1;
      if (cmp < 0) low = mid + 1;
      else if (cmp > 0) high = mid - 1;
      else return mid;
    }

    return -(low + 1);
  }

  private void incrementUsageCount() {
    GroupNode groupNode = this;
    while (true) {
      groupNode.myRecursiveUsageCount++;
      final GroupNode node = groupNode;
      myTreeModel.nodeChanged(node);
      TreeNode parent = groupNode.getParent();
      if (!(parent instanceof GroupNode)) return;
      groupNode = (GroupNode)parent;
    }
  }

  @Override
  public String tree2string(int indent, String lineSeparator) {
    StringBuffer result = new StringBuffer();
    StringUtil.repeatSymbol(result, ' ', indent);

    if (myGroup != null) result.append(myGroup.toString());
    result.append("[");
    result.append(lineSeparator);

    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      Node node = (Node)enumeration.nextElement();
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
    return myGroup == null || myGroup.isValid();
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

  private int getNodeInsertionIndex(@NotNull DefaultMutableTreeNode node) {
    Enumeration children = children();
    int idx = 0;
    while (children.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)children.nextElement();
      if (COMPARATOR.compare(child, node) >= 0) break;
      idx++;
    }
    return idx;
  }

  private static class NodeComparator implements Comparator<DefaultMutableTreeNode> {
    private static int getClassIndex(DefaultMutableTreeNode node) {
      if (node instanceof UsageNode) return 3;
      if (node instanceof GroupNode) return 2;
      if (node instanceof UsageTargetNode) return 1;
      return 0;
    }

    @Override
    public int compare(DefaultMutableTreeNode n1, DefaultMutableTreeNode n2) {
      int classIdx1 = getClassIndex(n1);
      int classIdx2 = getClassIndex(n2);
      if (classIdx1 != classIdx2) return classIdx1 - classIdx2;
      if (classIdx1 == 2) return ((GroupNode)n1).compareTo((GroupNode)n2);

      return 0;
    }
  }

  @Override
  public int compareTo(@NotNull GroupNode groupNode) {
    if (myRuleIndex == groupNode.myRuleIndex) {
      return myGroup.compareTo(groupNode.myGroup);
    }

    return myRuleIndex - groupNode.myRuleIndex;
  }

  public UsageGroup getGroup() {
    return myGroup;
  }

  public int getRecursiveUsageCount() {
    return myRecursiveUsageCount;
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myGroup != null) {
      myGroup.navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    return myGroup != null && myGroup.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myGroup != null && myGroup.canNavigateToSource();
  }


  @Override
  protected boolean isDataExcluded() {
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      Node node = (Node)enumeration.nextElement();
      if (!node.isExcluded()) return false;
    }
    return true;
  }

  @Override
  protected String getText(@NotNull UsageView view) {
    return myGroup.getText(view);
  }

  @NotNull
  public Collection<GroupNode> getSubGroups() {
    return mySubgroupNodes.values();
  }

  @NotNull
  public Collection<UsageNode> getUsageNodes() {
    return myUsageNodes;
  }
}
