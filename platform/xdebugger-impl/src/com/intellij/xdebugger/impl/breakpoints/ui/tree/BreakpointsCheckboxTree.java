// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.tree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;

public class BreakpointsCheckboxTree extends CheckboxTree {

  @Override
  protected void nodeStateWillChange(CheckedTreeNode node) {
    super.nodeStateWillChange(node);
    if (myDelegate != null) {
      myDelegate.nodeStateWillChange(node);
    }
  }

  @Override
  protected void onNodeStateChanged(CheckedTreeNode node) {
    super.onNodeStateChanged(node);
    if (myDelegate != null) {
      myDelegate.nodeStateDidChange(node);
    }
  }

  interface Delegate {
    void nodeStateDidChange(CheckedTreeNode node);

    void nodeStateWillChange(CheckedTreeNode node);
  }

  void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }

  private Delegate myDelegate = null;

  public BreakpointsCheckboxTree(Project project, BreakpointItemsTreeController model) {
    super(new BreakpointsTreeCellRenderer.BreakpointsCheckboxTreeCellRenderer(project), model.getRoot());
    getAccessibleContext().setAccessibleName(XDebuggerBundle.message("breakpoints.tree.accessible.name"));
  }

  @Override
  protected void installSpeedSearch() {
    TreeSpeedSearch.installOn(this, true, path -> {
      Object node = path.getLastPathComponent();
      if (node instanceof BreakpointItemNode) {
        return ((BreakpointItemNode)node).getBreakpointItem().speedSearchText();
      }
      else if (node instanceof BreakpointsGroupNode) {
        return ((BreakpointsGroupNode<?>)node).getGroup().getName();
      }
      return "";
    });
  }

  @Override
  public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof BreakpointItemNode) {
      final BreakpointItem breakpointItem = ((BreakpointItemNode)value).getBreakpointItem();
      final String displayText = breakpointItem != null? breakpointItem.getDisplayText() : null;
      if (!StringUtil.isEmptyOrSpaces(displayText)) {
        return displayText;
      }
    }
    return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
  }
}
