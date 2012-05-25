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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author nik, zajac
 */
public class BreakpointItemsTree extends CheckboxTree {
  //private final TreeNodeComparator myComparator;
  private final CheckedTreeNode myRoot;
  private final Map<BreakpointItem, BreakpointItemNode> myNodes = new HashMap<BreakpointItem, BreakpointItemNode>();
  private List<XBreakpointGroupingRule> myGroupingRules;
  private final Map<XBreakpointGroup, BreakpointsGroupNode> myGroupNodes = new HashMap<XBreakpointGroup, BreakpointsGroupNode>();

  private BreakpointItemsTreeDelegate myDelegate;

  private final MultiValuesMap<XBreakpointGroupingRule, XBreakpointGroup> myGroups = new MultiValuesMap<XBreakpointGroupingRule, XBreakpointGroup>();

  private BreakpointItemsTree(final CheckedTreeNode root,
                              Collection<XBreakpointGroupingRule> groupingRules) {
    super(new BreakpointsTreeCellRenderer(), root);
    myRoot = root;
    //myComparator = new TreeNodeComparator<B>(type, breakpointManager);
    setGroupingRulesInternal(groupingRules);

    getEmptyText().setText("No Breakpoints");
  }

  public void setDelegate(BreakpointItemsTreeDelegate delegate) {
    myDelegate = delegate;
  }

  private void setGroupingRulesInternal(final Collection<XBreakpointGroupingRule> groupingRules) {
    myGroupingRules = new ArrayList<XBreakpointGroupingRule>(groupingRules);
    setShowsRootHandles(!groupingRules.isEmpty());
  }

  public static BreakpointItemsTree createTree(final Collection<XBreakpointGroupingRule> groupingRules) {
    return new BreakpointItemsTree(new CheckedTreeNode("root"), groupingRules);
  }

  public void buildTree(@NotNull Collection<? extends BreakpointItem> breakpoints) {
    final TreeState state = TreeState.createOn(this, myRoot);
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
    //TreeUtil.sort(myRoot, myComparator);
    ((DefaultTreeModel)getModel()).nodeStructureChanged(myRoot);
    expandPath(new TreePath(myRoot));
    state.applyTo(this, myRoot);
  }


  @NotNull
  private CheckedTreeNode getParentNode(final BreakpointItem breakpoint) {
    CheckedTreeNode parent = myRoot;
    for (int i = 0; i < myGroupingRules.size(); i++) {
      XBreakpointGroup group = getGroup(breakpoint.getBreakpoint(), myGroupingRules.get(i));
      if (group != null) {
        parent = getOrCreateGroupNode(parent, group, i);
      }
    }
    return parent;
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

  @Override
  protected void onDoubleClick(CheckedTreeNode node) {
    if (node instanceof BreakpointItemNode) {
      myDelegate.execute(((BreakpointItemNode)node).getBreakpointItem());
    }
  }

  @Nullable
  private XBreakpointGroup getGroup(final Object breakpoint, final XBreakpointGroupingRule groupingRule) {
    //noinspection unchecked
    Collection<XBreakpointGroup> groups = myGroups.get(groupingRule);
    if (groups == null) {
      groups = Collections.emptyList();
    }
    XBreakpointGroup group = groupingRule.getGroup(breakpoint, groups);
    if (group != null) {
      myGroups.put(groupingRule, group);
    }
    return group;
  }

  @Override
  protected void onNodeStateChanged(final CheckedTreeNode node) {
    if (node instanceof BreakpointItemNode) {
      ((BreakpointItemNode)node).getBreakpointItem().setEnabled(node.isChecked());
    }
  }

  public void setGroupingRules(List<XBreakpointGroupingRule> groupingRules) {
    List<BreakpointItem> selectedBreakpoints = getSelectedBreakpoints();
    List<BreakpointItem> allBreakpoints = new ArrayList<BreakpointItem>(myNodes.keySet());

    setGroupingRulesInternal(groupingRules);
    buildTree(allBreakpoints);

    if (selectedBreakpoints.size() > 0) {
      selectBreakpointItem(selectedBreakpoints.get(0));
    }
  }

  public List<BreakpointItem> getSelectedBreakpoints() {
    final ArrayList<BreakpointItem> list = new ArrayList<BreakpointItem>();
    TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return list;

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

  public void selectBreakpointItem(final BreakpointItem breakpoint) {
    BreakpointItemNode node = myNodes.get(breakpoint);
    if (node != null) {
      TreeUtil.selectNode(this, node);
    }
  }

  private static class BreakpointsTreeCellRenderer extends CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof BreakpointItemNode) {
        BreakpointItemNode node = (BreakpointItemNode)value;
        BreakpointItem breakpoint = node.getBreakpointItem();
        breakpoint.setupRenderer(getTextRenderer());
      }
      else if (value instanceof BreakpointsGroupNode) {
        XBreakpointGroup group = ((BreakpointsGroupNode)value).getGroup();
        getTextRenderer().setIcon(group.getIcon(expanded));
        getTextRenderer().append(group.getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      }
    }
  }

  private static class BreakpointsGroupNode<G extends XBreakpointGroup> extends CheckedTreeNode {
    private final G myGroup;
    private final int myLevel;

    private BreakpointsGroupNode(G group, int level) {
      super(group);
      myLevel = level;
      setChecked(false);
      myGroup = group;
    }

    public G getGroup() {
      return myGroup;
    }

    public int getLevel() {
      return myLevel;
    }
  }
  
  private static class BreakpointItemNode extends CheckedTreeNode {
    private final BreakpointItem myBreakpoint;

    private BreakpointItemNode(final BreakpointItem breakpoint) {
      super(breakpoint);
      myBreakpoint = breakpoint;
      setChecked(breakpoint.isEnabled());
    }

    public BreakpointItem getBreakpointItem() {
      return myBreakpoint;
    }
  }

  private static class TreeNodeComparator<B extends XBreakpoint<?>> implements Comparator<TreeNode> {
    private final Comparator<B> myBreakpointComparator;
    private final XBreakpointManager myBreakpointManager;

    public TreeNodeComparator(final XBreakpointType<B, ?> type, XBreakpointManager breakpointManager) {
      myBreakpointManager = breakpointManager;
      myBreakpointComparator = type.getBreakpointComparator();
    }

    public int compare(final TreeNode o1, final TreeNode o2) {
      if (o1 instanceof BreakpointItemNode && o2 instanceof BreakpointItemNode) {
        //noinspection unchecked
        B b1 = (B)((BreakpointItemNode)o1).getBreakpointItem();
        //noinspection unchecked
        B b2 = (B)((BreakpointItemNode)o2).getBreakpointItem();
        boolean default1 = myBreakpointManager.isDefaultBreakpoint(b1);
        boolean default2 = myBreakpointManager.isDefaultBreakpoint(b2);
        if (default1 && !default2) return -1;
        if (!default1 && default2) return 1;
        return myBreakpointComparator.compare(b1, b2);
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

  public interface BreakpointItemsTreeDelegate {
    void execute(BreakpointItem item);
  }
}
