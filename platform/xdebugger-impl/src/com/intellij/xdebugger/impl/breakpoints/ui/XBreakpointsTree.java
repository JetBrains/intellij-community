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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author nik
 */
public class XBreakpointsTree<B extends XBreakpoint<?>> extends CheckboxTree {
  private final TreeNodeComparator myComparator;
  private final CheckedTreeNode myRoot;
  private final Map<B, BreakpointNode<B>> myNodes = new HashMap<B, BreakpointNode<B>>();
  private List<XBreakpointGroupingRule<B, ?>> myGroupingRules;
  private final Map<XBreakpointGroup, BreakpointsGroupNode> myGroupNodes = new HashMap<XBreakpointGroup, BreakpointsGroupNode>();
  private final MultiValuesMap<XBreakpointGroupingRule<B, ?>, XBreakpointGroup> myGroups = new MultiValuesMap<XBreakpointGroupingRule<B,?>, XBreakpointGroup>();

  private XBreakpointsTree(final XBreakpointType<B, ?> type, final CheckedTreeNode root,
                           Collection<XBreakpointGroupingRule<B, ?>> groupingRules) {
    super(new BreakpointsTreeCellRenderer(), root);
    myRoot = root;
    myComparator = new TreeNodeComparator<B>(type);
    setGroupingRulesInternal(groupingRules);
  }

  private void setGroupingRulesInternal(final Collection<XBreakpointGroupingRule<B, ?>> groupingRules) {
    myGroupingRules = new ArrayList<XBreakpointGroupingRule<B,?>>(groupingRules);
    setShowsRootHandles(!groupingRules.isEmpty());
  }

  public static <B extends XBreakpoint<?>> XBreakpointsTree<B> createTree(final XBreakpointType<B, ?> type, final Collection<XBreakpointGroupingRule<B, ?>> groupingRules) {
    return new XBreakpointsTree<B>(type, new CheckedTreeNode("root"), groupingRules);
  }

  public void buildTree(@NotNull Collection<? extends B> breakpoints) {
    final TreeState state = TreeState.createOn(this, myRoot);
    myRoot.removeAllChildren();
    myNodes.clear();
    myGroupNodes.clear();
    myGroups.clear();
    for (B breakpoint : breakpoints) {
      BreakpointNode<B> node = new BreakpointNode<B>(breakpoint);
      CheckedTreeNode parent = getParentNode(breakpoint);
      parent.add(node);
      myNodes.put(breakpoint, node);
    }
    TreeUtil.sort(myRoot, myComparator);
    ((DefaultTreeModel)getModel()).nodeStructureChanged(myRoot);
    expandPath(new TreePath(myRoot));
    state.applyTo(this, myRoot);
  }


  @NotNull
  private CheckedTreeNode getParentNode(final B breakpoint) {
    CheckedTreeNode parent = myRoot;
    for (int i = 0; i < myGroupingRules.size(); i++) {
      XBreakpointGroup group = getGroup(breakpoint, myGroupingRules.get(i));
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

  @Nullable
  private <G extends XBreakpointGroup> XBreakpointGroup getGroup(final B breakpoint, final XBreakpointGroupingRule<B, G> groupingRule) {
    //noinspection unchecked
    Collection<G> groups = (Collection<G>)myGroups.get(groupingRule);
    if (groups == null) {
      groups = Collections.emptyList();
    }
    G group = groupingRule.getGroup(breakpoint, groups);
    if (group != null) {
      myGroups.put(groupingRule, group);
    }
    return group;
  }

  @Override
  protected void onNodeStateChanged(final CheckedTreeNode node) {
    if (node instanceof BreakpointNode) {
      ((BreakpointNode)node).getBreakpoint().setEnabled(node.isChecked());
    }
  }

  public void setGroupingRules(List<XBreakpointGroupingRule<B, ?>> groupingRules) {
    List<B> selectedBreakpoints = getSelectedBreakpoints();
    List<B> allBreakpoints = new ArrayList<B>(myNodes.keySet());

    setGroupingRulesInternal(groupingRules);
    buildTree(allBreakpoints);

    if (selectedBreakpoints.size() > 0) {
      selectBreakpoint(selectedBreakpoints.get(0));
    }
  }

  public List<B> getSelectedBreakpoints() {
    final ArrayList<B> list = new ArrayList<B>();
    TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return list;

    for (TreePath selectionPath : selectionPaths) {
      TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), new TreeUtil.Traverse() {
        public boolean accept(final Object node) {
          if (node instanceof BreakpointNode) {
            list.add(((BreakpointNode<B>)node).getBreakpoint());
          }
          return true;
        }
      });
    }

    return list;
  }

  public void selectBreakpoint(final B breakpoint) {
    BreakpointNode<B> node = myNodes.get(breakpoint);
    if (node != null) {
      TreeUtil.selectNode(this, node);
    }
  }

  private static class BreakpointsTreeCellRenderer extends CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof BreakpointNode) {
        BreakpointNode node = (BreakpointNode)value;
        XBreakpoint breakpoint = node.getBreakpoint();
        String text = XBreakpointUtil.getDisplayText(breakpoint);
        getTextRenderer().setIcon(node.getIcon());
        getTextRenderer().append(text, node.getTextAttributes());
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
  
  private static class BreakpointNode<B extends XBreakpoint<?>> extends CheckedTreeNode {
    private final B myBreakpoint;

    private BreakpointNode(final B breakpoint) {
      super(breakpoint);
      myBreakpoint = breakpoint;
      setChecked(breakpoint.isEnabled());
    }

    public B getBreakpoint() {
      return myBreakpoint;
    }

    public Icon getIcon() {
      XBreakpointType type = myBreakpoint.getType();
      return isChecked() ? type.getEnabledIcon() : type.getDisabledIcon();
    }

    public SimpleTextAttributes getTextAttributes() {
      return isChecked() ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
    }
  }

  private static class TreeNodeComparator<B extends XBreakpoint<?>> implements Comparator<TreeNode> {
    private final Comparator<B> myBreakpointComparator;

    public TreeNodeComparator(final XBreakpointType<B, ?> type) {
      myBreakpointComparator = type.getBreakpointComparator();
    }

    public int compare(final TreeNode o1, final TreeNode o2) {
      if (o1 instanceof BreakpointNode && o2 instanceof BreakpointNode) {
        B b1 = (B)((BreakpointNode)o1).getBreakpoint();
        B b2 = (B)((BreakpointNode)o2).getBreakpoint();
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

}
