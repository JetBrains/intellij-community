/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.issueLinks;

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

  protected Object getTagAt(final MouseEvent e) {
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
