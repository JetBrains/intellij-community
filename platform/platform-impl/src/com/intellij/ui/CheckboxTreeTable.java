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
package com.intellij.ui;

import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
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
