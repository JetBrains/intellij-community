// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeListNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ChangesViewTreeStateStrategy implements ChangesTree.TreeStateStrategy<ChangesViewTreeStateStrategy.MyState> {
  @Override
  public @NotNull MyState saveState(@NotNull ChangesTree tree) {
    ChangesBrowserNode<?> oldRoot = tree.getRoot();
    TreeState state = TreeState.createOn(tree, oldRoot);
    state.setScrollToSelection(false);
    return new MyState(state, oldRoot.getFileCount());
  }

  @Override
  public void restoreState(@NotNull ChangesTree tree, @NotNull MyState state, boolean scrollToSelection) {
    ChangesBrowserNode<?> newRoot = tree.getRoot();
    state.treeState.applyTo(tree, newRoot);

    initTreeStateIfNeeded((ChangesListView)tree, newRoot, state.oldFileCount);
  }

  public record MyState(@NotNull TreeState treeState, int oldFileCount) {
  }

  private static void initTreeStateIfNeeded(@NotNull ChangesListView view,
                                            @NotNull ChangesBrowserNode<?> newRoot,
                                            int oldFileCount) {
    ChangesBrowserNode<?> defaultListNode = getDefaultChangelistNode(newRoot);
    if (defaultListNode == null) return;

    if (view.getSelectionCount() == 0) {
      TreeUtil.selectNode(view, defaultListNode);
    }

    if (oldFileCount == 0 && TreeUtil.collectExpandedPaths(view).isEmpty()) {
      view.expandSafe(defaultListNode);
    }
  }

  @Nullable
  private static ChangesBrowserNode<?> getDefaultChangelistNode(@NotNull ChangesBrowserNode<?> root) {
    return root.iterateNodeChildren()
      .filter(ChangesBrowserChangeListNode.class)
      .find(node -> {
        ChangeList list = node.getUserObject();
        return list instanceof LocalChangeList && ((LocalChangeList)list).isDefault();
      });
  }
}