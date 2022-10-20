// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.Disposable;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;

public class FilteringTreeModel extends StructureTreeModel<FilteringTreeStructure> {

  public static FilteringTreeModel createModel(AbstractTreeStructure structure, @NotNull ElementFilter<?> filter, @NotNull Disposable parent) {
    FilteringTreeStructure filteringTreeStructure = new FilteringTreeStructure(filter, structure);
    return new FilteringTreeModel(filteringTreeStructure, parent);
  }

  private FilteringTreeModel(@NotNull FilteringTreeStructure structure,
                            @NotNull Disposable parent) {
    super(structure, parent);
  }

  public void updateTree(@NotNull JTree tree, boolean expand, @Nullable Object preferredSelection) {
    getInvoker().invoke(() -> {
      getTreeStructure().refilter();
      invalidateAsync().thenRun(() -> {
        if (expand) {
          TreeUtil.promiseExpandAll(tree).then(o -> adjustSelection(tree, preferredSelection));
        }
        else {
          adjustSelection(tree, preferredSelection);
        }
      });
    });
  }

  private boolean adjustSelection(JTree tree, @Nullable Object preferredSelection) {
    TreePath selectionPath = tree.getSelectionPath();
    if (selectionPath == null || preferredSelection != null) {
      TreeUtil.promiseSelect(tree, path -> {
        Object component = TreeUtil.getUserObject(path.getLastPathComponent());
        if (component == null) return TreeVisitor.Action.CONTINUE;
        if (component instanceof FilteringTreeStructure.FilteringNode &&
            ((FilteringTreeStructure.FilteringNode)component).getDelegate() == preferredSelection) {
          return TreeVisitor.Action.INTERRUPT;
        }
        if (preferredSelection != null) return TreeVisitor.Action.CONTINUE;
        Object @NotNull [] elements = getTreeStructure().getChildElements(component);
        return elements.length > 0 ? TreeVisitor.Action.CONTINUE : TreeVisitor.Action.INTERRUPT;
      });
    }
    else {
      TreeUtil.scrollToVisible(tree, selectionPath, false);
    }
    return true;
  }
}
