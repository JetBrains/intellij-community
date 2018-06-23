// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints.ui.tree;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.project.Project;
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
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author nik, zajac
 */
public class BreakpointItemsTreeController implements BreakpointsCheckboxTree.Delegate {
  private static final TreeNodeComparator COMPARATOR = new TreeNodeComparator();
  private final CheckedTreeNode myRoot;
  private final Map<BreakpointItem, BreakpointItemNode> myNodes = new HashMap<>();
  private List<XBreakpointGroupingRule> myGroupingRules;

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
    myGroupingRules = new ArrayList<>(groupingRules);
  }

  public void buildTree(@NotNull Collection<? extends BreakpointItem> breakpoints) {
    final TreeState state = TreeState.createOn(myTreeView, myRoot);
    state.setScrollToSelection(false);
    myRoot.removeAllChildren();
    myNodes.clear();
    for (BreakpointItem breakpoint : breakpoints) {
      BreakpointItemNode node = new BreakpointItemNode(breakpoint);
      CheckedTreeNode parent = getParentNode(breakpoint);
      parent.add(node);
      myNodes.put(breakpoint, node);
    }
    TreeUtil.sortRecursively(myRoot, COMPARATOR);
    myInBuild = true;
    ((DefaultTreeModel)(myTreeView.getModel())).nodeStructureChanged(myRoot);
    state.applyTo(myTreeView, myRoot);
    myInBuild = false;
  }


  @NotNull
  private CheckedTreeNode getParentNode(final BreakpointItem breakpoint) {
    CheckedTreeNode parent = myRoot;
    for (int i = 0; i < myGroupingRules.size(); i++) {
      XBreakpointGroup group = myGroupingRules.get(i).getGroup(breakpoint.getBreakpoint(), Collections.emptyList());
      if (group != null) {
        parent = getOrCreateGroupNode(parent, group, i);
        if (breakpoint.isEnabled()) {
          parent.setChecked(true);
        }
      }
    }
    return parent;
  }

  private static Collection<XBreakpointGroup> getGroupNodes(CheckedTreeNode parent) {
    Collection<XBreakpointGroup> nodes = new ArrayList<>();
    Enumeration children = parent.children();
    while (children.hasMoreElements()) {
      Object element = children.nextElement();
      if (element instanceof BreakpointsGroupNode) {
        nodes.add(((BreakpointsGroupNode)element).getGroup());
      }
    }
    return nodes;
  }

  private static BreakpointsGroupNode getOrCreateGroupNode(CheckedTreeNode parent, final XBreakpointGroup group,
                                                                                       final int level) {
    Enumeration children = parent.children();
    while (children.hasMoreElements()) {
      Object element = children.nextElement();
      if (element instanceof BreakpointsGroupNode) {
        XBreakpointGroup groupFound = ((BreakpointsGroupNode)element).getGroup();
        if (groupFound.equals(group)) {
          return (BreakpointsGroupNode)element;
        }
      }
    }
    BreakpointsGroupNode groupNode = new BreakpointsGroupNode<>(group, level);
    parent.add(groupNode);
    return groupNode;
  }

  public void setGroupingRules(Collection<XBreakpointGroupingRule> groupingRules) {
    setGroupingRulesInternal(groupingRules);
    rebuildTree(new ArrayList<>(myNodes.keySet()));
  }

  public void rebuildTree(Collection<BreakpointItem> items) {
    List<BreakpointItem> selectedBreakpoints = getSelectedBreakpoints(false);
    TreePath path = myTreeView.getSelectionPath();
    buildTree(items);
    if (myTreeView.getRowForPath(path) == -1 && !selectedBreakpoints.isEmpty()) {
      selectBreakpointItem(selectedBreakpoints.get(0), path);
    }
    else {
      selectBreakpointItem(null, path);
    }
  }

  public List<BreakpointItem> getSelectedBreakpoints(boolean traverse) {
    TreePath[] selectionPaths = myTreeView.getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return Collections.emptyList();

    final ArrayList<BreakpointItem> list = new ArrayList<>();
    for (TreePath selectionPath : selectionPaths) {
      TreeNode startNode = (TreeNode)selectionPath.getLastPathComponent();
      if (traverse) {
        TreeUtil.traverseDepth(startNode, node -> {
          if (node instanceof BreakpointItemNode) {
            list.add(((BreakpointItemNode)node).getBreakpointItem());
          }
          return true;
        });
      }
      else {
        if (startNode instanceof BreakpointItemNode) {
          list.add(((BreakpointItemNode)startNode).getBreakpointItem());
        }
      }
    }

    return list;
  }

  public void selectBreakpointItem(@Nullable final BreakpointItem breakpoint, TreePath path) {
    BreakpointItemNode node = myNodes.get(breakpoint);
    if (node != null) {
      path = TreeUtil.getPathFromRoot(node);
    }
    TreeUtil.selectPath(myTreeView, path, false);
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
    final List<BreakpointItem> breakpoints = getSelectedBreakpoints(true);
    for (TreePath path : paths) {
      Object node = path.getLastPathComponent();
      if (node instanceof BreakpointItemNode) {
        final BreakpointItem item = ((BreakpointItemNode)node).getBreakpointItem();
        if (!item.allowedToRemove()) {
          TreeUtil.unselectPath(myTreeView, path);
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
