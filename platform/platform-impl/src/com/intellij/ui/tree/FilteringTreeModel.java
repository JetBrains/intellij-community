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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
          List<FilteringTreeStructure.FilteringNode> leaves = getTreeStructure().getVisibleLeaves();
          Set<FilteringTreeStructure.FilteringNode> set = new HashSet<>(leaves);
          for (FilteringTreeStructure.FilteringNode node : leaves) {
            do {
              node = node.getParentNode();
            }
            while (node != null && set.add(node));
          }
          TreeUtil.promiseMakeVisible(tree, path -> {
            Object object = TreeUtil.getLastUserObject(path);
            if (!(object instanceof FilteringTreeStructure.FilteringNode)) return TreeVisitor.Action.CONTINUE;
            return set.contains(object) ? TreeVisitor.Action.CONTINUE : TreeVisitor.Action.SKIP_CHILDREN;
          });
        }
        adjustSelection(tree, preferredSelection);
      });
    });
  }

  private void adjustSelection(JTree tree, @Nullable Object preferredSelection) {
    if (preferredSelection == null) {
      return;
    }
    FilteringTreeStructure.FilteringNode node = getTreeStructure().getVisibleNodeFor(preferredSelection);
    promiseVisitor(node).onSuccess(visitor -> TreeUtil.promiseMakeVisible(tree, visitor).onSuccess(path -> {
      tree.setSelectionPath(path);
      TreeUtil.scrollToVisible(tree, path, false);
    }));
  }
}
