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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author yole
*/
public class TreeLinkMouseListener extends LinkMouseListenerBase {
  private final ColoredTreeCellRenderer myRenderer;
  protected TreeNode myLastHitNode;

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
      final TreeNode treeNode = (TreeNode) path.getLastPathComponent();
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
