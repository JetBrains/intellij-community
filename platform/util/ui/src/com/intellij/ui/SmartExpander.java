// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Enumeration;

/**
 * author: lesya
 */
public final class SmartExpander {
  public static void installOn(final JTree tree){
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillCollapse(TreeExpansionEvent event) {
        TreePath path = event.getPath();
        Enumeration children = ((TreeNode)path.getLastPathComponent()).children();
        while (children.hasMoreElements()) {
          TreePath childPath = path.pathByAddingChild(children.nextElement());
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
        TreeNode lastPathComponent = (TreeNode)path.getLastPathComponent();
        if (lastPathComponent.getChildCount() == 1) {
          TreePath firstChildPath = path.pathByAddingChild(lastPathComponent.getChildAt(0));
          tree.expandPath(firstChildPath);
        }
      }
    });
  }
}
