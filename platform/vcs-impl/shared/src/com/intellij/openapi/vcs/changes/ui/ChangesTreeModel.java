// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

@ApiStatus.Internal
public class ChangesTreeModel extends DefaultTreeModel {
  public ChangesTreeModel(@NotNull ChangesBrowserNode<?> root) {
    super(root);
  }

  /**
   * Do not override {@link ChangesBrowserNode#setUserObject(Object)} by {@link BasicTreeUI#completeEditing(boolean, boolean, boolean)}.
   */
  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    TreeNode node = (TreeNode)path.getLastPathComponent();
    nodeChanged(node);
  }
}
