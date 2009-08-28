package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author yole
*/
public class TreeLinkMouseListener extends LinkMouseListenerBase {
  private final ColoredTreeCellRenderer myRenderer;
  protected DefaultMutableTreeNode myLastHitNode;

  public TreeLinkMouseListener(final ColoredTreeCellRenderer renderer) {
    myRenderer = renderer;
  }

  protected void showTooltip(final JTree tree, final MouseEvent e, final HaveTooltip launcher) {
    final String text = tree.getToolTipText(e);
    final String newText = launcher == null ? null : launcher.getTooltip();
    if (! Comparing.equal(text, newText)) {
      tree.setToolTipText(newText);
    }
  }

  @Nullable @Override
  protected Object getTagAt(final MouseEvent e) {
    JTree tree = (JTree) e.getSource();
    Object tag = null;
    HaveTooltip haveTooltip = null;
    final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final Rectangle rectangle = tree.getPathBounds(path);
      int dx = e.getX() - rectangle.x;
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
      if (myLastHitNode != treeNode) {
        myLastHitNode = treeNode;
        myRenderer.getTreeCellRendererComponent(tree, treeNode, false, false, treeNode.isLeaf(), -1, false);
      }
      int i = myRenderer.findFragmentAt(dx);
      if (i >= 0) {
        tag = myRenderer.getFragmentTag(i);
        if (treeNode instanceof HaveTooltip) {
          haveTooltip = (HaveTooltip) treeNode;
        }
      }
    }
    showTooltip(tree, e, haveTooltip);
    return tag;
  }

  public static class BrowserLauncher implements Runnable {
    private final String myUrl;

    public BrowserLauncher(final String url) {
      myUrl = url;
    }

    public void run() {
      BrowserUtil.launchBrowser(myUrl);
    }
  }

  public interface HaveTooltip {
    String getTooltip();
  }
}
