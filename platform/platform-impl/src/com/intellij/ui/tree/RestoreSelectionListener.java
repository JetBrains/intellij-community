// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import static com.intellij.util.ui.tree.TreeUtil.visitVisibleRows;
import static java.awt.EventQueue.invokeLater;

/**
 * Temporary solution to restore selection in a tree.
 * It should be integrated into TreeUI to process path removing more accurately.
 */
public final class RestoreSelectionListener implements TreeSelectionListener {
  @Override
  public void valueChanged(TreeSelectionEvent event) {
    if (null == event.getNewLeadSelectionPath()) {
      TreePath path = event.getOldLeadSelectionPath();
      if (path != null && null != path.getParentPath()) {
        Object source = event.getSource();
        if (source instanceof JTree) {
          JTree tree = (JTree)source;
          if (tree.getSelectionModel().isSelectionEmpty()) {
            invokeLater(() -> {
              // restore selection later, because nodes are removed before they are inserted
              if (tree.getSelectionModel().isSelectionEmpty()) {
                // restore a path selection only if nothing is selected now
                Reference<TreePath> reference = new Reference<>();
                reference.set(tree.getPathForRow(0)); // select first node if old path removed completely
                TreeVisitor visitor = new TreeVisitor.ByTreePath<>(path, o -> o) {
                  @NotNull
                  @Override
                  protected Action visit(@NotNull TreePath path, Object component) {
                    Action action = super.visit(path, component);
                    if (action == Action.CONTINUE || action == Action.INTERRUPT) reference.set(path);
                    return action;
                  }
                };
                visitVisibleRows(tree, visitor);
                tree.setSelectionPath(reference.get());
              }
            });
          }
        }
      }
    }
  }
}
