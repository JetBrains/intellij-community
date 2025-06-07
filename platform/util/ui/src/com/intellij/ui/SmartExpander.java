// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * author: lesya
 */
public final class SmartExpander {
  private static boolean ourRecursiveCollapseEnabled = true;

  @ApiStatus.Internal
  public static void setRecursiveCollapseEnabled(boolean enabled) {
    ourRecursiveCollapseEnabled = enabled;
  }

  public static void installOn(final JTree tree){
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillCollapse(TreeExpansionEvent event) {
        if (!ourRecursiveCollapseEnabled) return;
        TreePath path = event.getPath();
        TreeModel model = tree.getModel();
        Object lastPathComponent = path.getLastPathComponent();
        int childCount = model.getChildCount(lastPathComponent);
        for (int i = 0; i < childCount; i++) {
          Object child = model.getChild(lastPathComponent, i);
          TreePath childPath = path.pathByAddingChild(child);
          if (tree.isExpanded(childPath)) {
            tree.collapsePath(childPath);
          }
        }
      }

      @Override
      public void treeWillExpand(TreeExpansionEvent event) {
      }
    });

    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();
        TreeModel model = tree.getModel();
        Object lastPathComponent = path.getLastPathComponent();
        if (model.getChildCount(lastPathComponent) == 1) {
          TreePath firstChildPath = path.pathByAddingChild(model.getChild(lastPathComponent, 0));
          tree.expandPath(firstChildPath);
        }
      }
    });
  }
}
