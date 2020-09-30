// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * @author yole
 */
public class TreeLinkMouseListener extends LinkMouseListenerBase {
  private final ColoredTreeCellRenderer myRenderer;
  protected WeakReference<TreeNode> myLastHitNode;

  public TreeLinkMouseListener(final ColoredTreeCellRenderer renderer) {
    myRenderer = renderer;
  }

  protected void showTooltip(final JTree tree, final MouseEvent e, final HaveTooltip launcher) {
    final String text = tree.getToolTipText(e);
    final String newText = launcher == null ? null : launcher.getTooltip();
    if (!Objects.equals(text, newText)) {
      tree.setToolTipText(newText);
    }
  }

  @Nullable
  @Override
  protected Object getTagAt(@NotNull final MouseEvent e) {
    JTree tree = (JTree)e.getSource();
    Object tag = null;
    HaveTooltip haveTooltip = null;
    final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      int dx = getRendererRelativeX(e, tree, path);
      final TreeNode treeNode = (TreeNode)path.getLastPathComponent();
      AppUIUtil.targetToDevice(myRenderer, tree);
      if (myLastHitNode == null || myLastHitNode.get() != treeNode || e.getButton() != MouseEvent.NOBUTTON) {
        if (doCacheLastNode()) {
          myLastHitNode = new WeakReference<>(treeNode);
        }
        myRenderer.getTreeCellRendererComponent(tree, treeNode, false, false, treeNode.isLeaf(), tree.getRowForPath(path), false);
      }
      tag = myRenderer.getFragmentTagAt(dx);
      if (tag != null && treeNode instanceof HaveTooltip) {
        haveTooltip = (HaveTooltip)treeNode;
      }
    }
    showTooltip(tree, e, haveTooltip);
    return tag;
  }

  protected int getRendererRelativeX(@NotNull MouseEvent e, @NotNull JTree tree, @NotNull TreePath path) {
    final Rectangle rectangle = tree.getPathBounds(path);
    assert rectangle != null;
    return e.getX() - rectangle.x;
  }

  protected boolean doCacheLastNode() {
    return true;
  }

  public interface HaveTooltip {
    @NlsContexts.Tooltip String getTooltip();
  }
}
