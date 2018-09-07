// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.issueLinks;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public class TreeNodePartListener extends LinkMouseListenerBase {
  private final ClickableTreeCellRenderer myRenderer;
  //recalc optimization
  private DefaultMutableTreeNode myLastHitNode;
  private Component myRenderedComp;

  public TreeNodePartListener(ClickableTreeCellRenderer renderer) {
    myRenderer = renderer;
  }

  @Override
  protected Object getTagAt(@NotNull final MouseEvent e) {
    final JTree tree = (JTree) e.getSource();
    final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
      if (myLastHitNode != treeNode) {
        myLastHitNode = treeNode;
        myRenderedComp = myRenderer.getTreeCellRendererComponent(tree, treeNode, false, false, treeNode.isLeaf(), -1, false);
      }

      if (myRenderedComp != null) {
        Rectangle bounds = tree.getPathBounds(path);
        if (bounds != null) {
          Component root =
            tree.getCellRenderer().getTreeCellRendererComponent(tree, treeNode, false, false, treeNode.isLeaf(), -1, false);
          root.setSize(bounds.getSize());
          root.doLayout();
          final int compX = myRenderedComp.getX() + bounds.x;
          final int compY = myRenderedComp.getY() + bounds.y;
          if ((compX < e.getX()) && ((compX + myRenderedComp.getWidth()) > e.getX()) &&
            (compY < e.getY()) && ((compY + myRenderedComp.getHeight()) > e.getY())) {
            return myRenderer.getTag();
          }
        }
      }
    }
    return null;
  }
}
