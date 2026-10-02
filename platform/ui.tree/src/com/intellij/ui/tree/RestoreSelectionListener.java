// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree;

import com.intellij.openapi.util.Key;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.LoadingNode;
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
  /**
   * If set on a tree to true as a client property, disables this listener entirely.
   * <p>
   * Intended to be used in cases when the listener is installed by 3rd party code,
   * and there are use cases when it's behavior is undesirable.
   * </p>
   */
  public static final @NotNull Key<Boolean> DISABLED = Key.create("RestoreSelectionListener.DISABLED");

  @Override
  public void valueChanged(TreeSelectionEvent event) {
    if (null == event.getNewLeadSelectionPath()) {
      TreePath path = event.getOldLeadSelectionPath();
      if (path != null && null != path.getParentPath()) {
        Object source = event.getSource();
        if (source instanceof JTree tree) {
          if (ClientProperty.isTrue(tree, DISABLED)) return;
          if (tree.getSelectionModel().isSelectionEmpty()) {
            invokeLater(() -> {
              // restore selection later, because nodes are removed before they are inserted
              if (tree.getSelectionModel().isSelectionEmpty()) {
                // restore a path selection only if nothing is selected now
                Reference<TreePath> reference = new Reference<>();
                reference.set(tree.getPathForRow(0)); // select first node if old path removed completely
                TreeVisitor visitor = new TreeVisitor.ByTreePath<>(path, o -> o) {
                  @Override
                  protected @NotNull Action visit(@NotNull TreePath path, Object component) {
                    Action action = super.visit(path, component);
                    if (action == Action.CONTINUE || action == Action.INTERRUPT) reference.set(path);
                    return action;
                  }

                  @Override
                  protected boolean matches(@NotNull Object pathComponent, @NotNull Object thisComponent) {
                    // If "loading..." was replaced by the real nodes, we should select the first new one.
                    if (thisComponent instanceof LoadingNode) return true;
                    return super.matches(pathComponent, thisComponent);
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
