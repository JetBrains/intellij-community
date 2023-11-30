// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

public class CheckboxTreeTable extends TreeTableView {
  private final EventDispatcher<CheckboxTreeListener> myEventDispatcher;

  public CheckboxTreeTable(CheckedTreeNode root, CheckboxTree.CheckboxTreeCellRenderer renderer, final ColumnInfo[] columns) {
    super(new ListTreeTableModelOnColumns(root, columns));
    final TreeTableTree tree = getTree();
    myEventDispatcher = EventDispatcher.create(CheckboxTreeListener.class);
    CheckboxTreeHelper helper = new CheckboxTreeHelper(CheckboxTreeHelper.DEFAULT_POLICY, myEventDispatcher);
    helper.initTree(tree, this, renderer);
    tree.setSelectionRow(0);
  }

  public void addCheckboxTreeListener(@NotNull CheckboxTreeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public <T> T[] getCheckedNodes(final Class<T> nodeType) {
    return CheckboxTreeHelper.getCheckedNodes(nodeType, null, getTree().getModel());
  }
}
