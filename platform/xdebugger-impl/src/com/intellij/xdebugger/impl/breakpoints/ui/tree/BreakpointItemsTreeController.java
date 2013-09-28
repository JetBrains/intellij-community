/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints.ui.tree;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author nik, zajac
 */
public class BreakpointItemsTreeController implements BreakpointsCheckboxTree.Delegate {
  private final TreeNodeComparator myComparator = new TreeNodeComparator();
  private final CheckedTreeNode myRoot;
  private final Map<BreakpointItem, BreakpointItemNode> myNodes = new HashMap<BreakpointItem, BreakpointItemNode>();
  private List<XBreakpointGroupingRule> myGroupingRules;
  private final Map<XBreakpointGroup, BreakpointsGroupNode> myGroupNodes = new HashMap<XBreakpointGroup, BreakpointsGroupNode>();

  private final MultiValuesMap<XBreakpointGroupingRule, XBreakpointGroup> myGroups = new MultiValuesMap<XBreakpointGroupingRule, XBreakpointGroup>();

  private JTree myTreeView;
  protected boolean myInBuild;

  public BreakpointItemsTreeController(Collection<XBreakpointGroupingRule> groupingRules) {
    myRoot = new CheckedTreeNode("root");
    setGroupingRulesInternal(groupingRules);
  }

  public JTree getTreeView() {
    return myTreeView;
  }

  public void setTreeView(JTree treeView) {
    myTreeView = treeView;
    myTreeView.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent event) {
        selectionChanged();
      }
    });
    if (treeView instanceof BreakpointsCheckboxTree) {
      ((BreakpointsCheckboxTree)treeView).setDelegate(this);
    }
    myTreeView.setShowsRootHandles(!myGroupingRules.isEmpty());
  }

  protected void selectionChanged() {
    if (myInBuild) return;
    selectionChangedImpl();
  }

  protected void selectionChangedImpl() {
  }

  @Override
  public void nodeStateDidChange(CheckedTreeNode node) {
    if (myInBuild) return;
    nodeStateDidChangeImpl(node);
  }

  protected void nodeStateDidChangeImpl(CheckedTreeNode node) {
    if (node instanceof BreakpointItemNode) {
      ((BreakpointItemNode)node).getBreakpointItem().setEnabled(node.isChecked());
    }
  }

  @Override
  public void nodeStateWillChange(CheckedTreeNode node) {
    if (myInBuild) return;
    nodeStateWillChangeImpl(node);
  }

  protected void nodeStateWillChangeImpl(CheckedTreeNode node) {
  }

  private void setGroupingRulesInternal(final Collection<XBreakpointGroupingRule> groupingRules) {
    myGroupingRules = new ArrayList<XBreakpointGroupingRule>(groupingRules);
  }

  public void buildTree(@NotNull Collection<? extends BreakpointItem> breakpoints) {
    final TreeState state = TreeState.createOn(myTreeView, myRoot);
    myRoot.removeAllChildren();
    myNodes.clear();
    myGroupNodes.clear();
    myGroups.clear();
    for (BreakpointItem breakpoint : breakpoints) {
      BreakpointItemNode node = new BreakpointItemNode(breakpoint);
      CheckedTreeNode parent = getParentNode(breakpoint);
      parent.add(node);
      myNodes.put(breakpoint, node);
    }
    TreeUtil.sort(myRoot, myComparator);
    myInBuild = true;
    ((DefaultTreeModel)(myTreeView.getModel())).nodeStructureChanged(myRoot);
    state.applyTo(myTreeView, myRoot);
    TreeUtil.expandAll(myTreeView);
    myInBuild = false;
  }


  @NotNull
  private CheckedTreeNode getParentNode(final BreakpointItem breakpoint) {
    CheckedTreeNode parent = myRoot;
    XBreakpointGroup parentGroup = null;
    for (int i = 0; i < myGroupingRules.size(); i++) {
      XBreakpointGroup group = getGroup(parentGroup, breakpoint, myGroupingRules.get(i));
      if (group != null) {
        parent = getOrCreateGroupNode(parent, group, i);
        parentGroup = group;
      }
    }
    return parent;
  }

  @Nullable
  private XBreakpointGroup getGroup(XBreakpointGroup parentGroup, final BreakpointItem breakpoint, final XBreakpointGroupingRule groupingRule) {
    //noinspection unchecked
    Collection<XBreakpointGroup> groups = myGroups.get(groupingRule);
    if (groups == null) {
      groups = Collections.emptyList();
    }

    XBreakpointGroup group = groupingRule.getGroup(breakpoint.getBreakpoint(), filterByParent(parentGroup, groups));
    if (group != null) {
      myGroups.put(groupingRule, group);
    }
    return group;
  }

  private Collection<XBreakpointGroup> filterByParent(XBreakpointGroup parentGroup, Collection<XBreakpointGroup> groups) {
    Collection<XBreakpointGroup> filtered = new ArrayList<XBreakpointGroup>();
    for (XBreakpointGroup group : groups) {
      TreeNode parentNode = myGroupNodes.get(group).getParent();
      BreakpointsGroupNode parent = parentNode instanceof BreakpointsGroupNode ? (BreakpointsGroupNode)parentNode : null;
      if ((parentGroup == null && parentNode == myRoot) || (parent != null && parent.getGroup() == parentGroup)) {
        filtered.add(group);
      }
    }
    return filtered;
  }

  private <G extends XBreakpointGroup> BreakpointsGroupNode<G> getOrCreateGroupNode(CheckedTreeNode parent, final G group,
                                                                                       final int level) {
    //noinspection unchecked
    BreakpointsGroupNode<G> groupNode = (BreakpointsGroupNode<G>)myGroupNodes.get(group);
    if (groupNode == null) {
      groupNode = new BreakpointsGroupNode<G>(group, level);
      myGroupNodes.put(group, groupNode);
      parent.add(groupNode);
    }
    return groupNode;
  }

  public void setGroupingRules(Collection<XBreakpointGroupingRule> groupingRules) {
    setGroupingRulesInternal(groupingRules);
    rebuildTree(new ArrayList<BreakpointItem>(myNodes.keySet()));
  }

  public void rebuildTree(Collection<BreakpointItem> items) {
    TreePath path = myTreeView.getSelectionPath();
    buildTree(items);
    selectBreakpointItem(null, path);
  }

  public List<BreakpointItem> getSelectedBreakpoints() {
    TreePath[] selectionPaths = myTreeView.getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return Collections.emptyList();

    final ArrayList<BreakpointItem> list = new ArrayList<BreakpointItem>();
    for (TreePath selectionPath : selectionPaths) {
      TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), new TreeUtil.Traverse() {
        public boolean accept(final Object node) {
          if (node instanceof BreakpointItemNode) {
            list.add(((BreakpointItemNode)node).getBreakpointItem());
          }
          return true;
        }
      });
    }

    return list;
  }

  public void selectBreakpointItem(@Nullable final BreakpointItem breakpoint, TreePath path) {
    BreakpointItemNode node = myNodes.get(breakpoint);
    if (node != null) {
      TreeUtil.selectNode(myTreeView, node);
    }
    else {
      TreeUtil.selectPath(myTreeView, path);
    }
  }

  public CheckedTreeNode getRoot() {
    return myRoot;
  }

  public void selectFirstBreakpointItem() {
    TreeUtil.selectPath(myTreeView, TreeUtil.getFirstLeafNodePath(myTreeView));

  }

  public void removeSelectedBreakpoints(Project project) {
    final TreePath[] paths = myTreeView.getSelectionPaths();
    if (paths == null) return;
    final List<BreakpointItem> breakpoints = getSelectedBreakpoints();
    for (TreePath path : paths) {
      final Object node = path.getLastPathComponent();
      if (node instanceof BreakpointItemNode) {
        final BreakpointItem item = ((BreakpointItemNode)node).getBreakpointItem();
        if (!item.allowedToRemove()) {
          TreeUtil.unselect(myTreeView, (DefaultMutableTreeNode)node);
          breakpoints.remove(item);
        }
      }
    }
    if (breakpoints.isEmpty()) return;
    TreeUtil.removeSelected(myTreeView);
    for (BreakpointItem breakpoint : breakpoints) {
      breakpoint.removed(project);
    }
  }

  private static class TreeNodeComparator implements Comparator<TreeNode> {
    public int compare(final TreeNode o1, final TreeNode o2) {
      if (o1 instanceof BreakpointItemNode && o2 instanceof BreakpointItemNode) {
        //noinspection unchecked
        BreakpointItem b1 = ((BreakpointItemNode)o1).getBreakpointItem();
        //noinspection unchecked
        BreakpointItem b2 = ((BreakpointItemNode)o2).getBreakpointItem();
        boolean default1 = b1.isDefaultBreakpoint();
        boolean default2 = b2.isDefaultBreakpoint();
        if (default1 && !default2) return -1;
        if (!default1 && default2) return 1;
        return b1.compareTo(b2);
      }
      if (o1 instanceof BreakpointsGroupNode && o2 instanceof BreakpointsGroupNode) {
        final BreakpointsGroupNode group1 = (BreakpointsGroupNode)o1;
        final BreakpointsGroupNode group2 = (BreakpointsGroupNode)o2;
        if (group1.getLevel() != group2.getLevel()) {
          return group1.getLevel() - group2.getLevel();
        }
        return group1.getGroup().compareTo(group2.getGroup());
      }
      return o1 instanceof BreakpointsGroupNode ? -1 : 1;
    }
  }
}
