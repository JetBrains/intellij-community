// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.merge.MergeConflictManager;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserConflictsNodeKt.CONFLICTS_NODE_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserResolvedConflictsNodeKt.RESOLVED_CONFLICTS_NODE_TAG;

@ApiStatus.Internal
public class ChangesViewTreeStateStrategy implements ChangesTree.TreeStateStrategy<ChangesViewTreeStateStrategy.MyState> {
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
    DefaultMutableTreeNode firstMergeNode = getFirstMergeConflictNode(view);
    if (firstMergeNode != null) {
      TreeUtil.selectNode(view, firstMergeNode);
    }

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

  private static @Nullable ChangesBrowserNode<?> getFirstMergeConflictNode(@NotNull ChangesListView view) {
    ChangesBrowserNode<?> conflictNode = getFirstConflictNode(view);
    if (conflictNode != null) return conflictNode;

    ChangesBrowserNode<?> resolvedConflictNode = getFirstResolvedConflictNode(view);
    if (resolvedConflictNode != null) return resolvedConflictNode;

    return null;
  }

  private static @Nullable ChangesBrowserNode<?> getFirstConflictNode(@NotNull ChangesListView view) {
    return VcsTreeModelData.allUnderTag(view, CONFLICTS_NODE_TAG).iterateRawNodes()
      .filter(ChangesBrowserChangeNode.class)
      .find(node -> {
        return MergeConflictManager.isMergeConflict(node.getUserObject().getFileStatus());
      });
  }

  private static @Nullable ChangesBrowserNode<?> getFirstResolvedConflictNode(@NotNull ChangesListView view) {
    return VcsTreeModelData.allUnderTag(view, RESOLVED_CONFLICTS_NODE_TAG).iterateRawNodes()
      .filter(ChangesBrowserChangeNode.class)
      .find(node -> {
        return MergeConflictManager.isMergeConflict(node.getUserObject().getFileStatus());
      });
  }
}
