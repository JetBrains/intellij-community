/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ui.AbstractExpandableItemsHandler;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBInsets;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * @author nik
 */
class XDebuggerTreeRenderer extends ColoredTreeCellRenderer {
  private final MyColoredTreeCellRenderer myLink = new MyColoredTreeCellRenderer();
  private boolean myHaveLink;
  private int myLinkOffset;
  private int myLinkWidth;

  public XDebuggerTreeRenderer() {
    Insets myLinkIpad = myLink.getIpad();
    myLink.setIpad(new JBInsets(myLinkIpad.top, 0, myLinkIpad.bottom, myLinkIpad.right));
    Insets myIpad = getIpad();
    setIpad(new JBInsets(myLinkIpad.top, myIpad.left, myLinkIpad.bottom, 0));
  }

  public void customizeCellRenderer(@NotNull final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    myHaveLink = false;
    myLink.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    XDebuggerTreeNode node = (XDebuggerTreeNode)value;
    node.appendToComponent(this);
    setIcon(node.getIcon());
    if (myHaveLink) {
      Dimension linkSize = myLink.getPreferredSize();
      myLinkWidth = linkSize.width;
      myLink.setBounds(0, 0, linkSize.width, linkSize.height);
      Rectangle treeVisibleRect = tree.getVisibleRect();
      TreePath path = tree.getPathForRow(row);
      int rowX = path != null ? ((XDebuggerTree.LinkTreeUI)tree.getUI()).getRowX(row, path.getPathCount() - 1) : 0;
      myLinkOffset = Math.min(super.getPreferredSize().width, treeVisibleRect.x + treeVisibleRect.width - myLinkWidth - rowX);
    }
    putClientProperty(AbstractExpandableItemsHandler.DISABLE_EXPANDABLE_HANDLER, myHaveLink ? true : null);
  }

  @Override
  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
    if (tag instanceof XDebuggerTreeNodeHyperlink && ((XDebuggerTreeNodeHyperlink)tag).alwaysOnScreen()) {
      myHaveLink = true;
      myLink.append(fragment, attributes, tag);
    }
    else {
      super.append(fragment, attributes, tag);
    }
  }

  @Override
  protected void doPaint(Graphics2D g) {
    if (myHaveLink) {
      Graphics2D textGraphics = (Graphics2D)g.create(0, 0, myLinkOffset, g.getClipBounds().height);
      try {
        super.doPaint(textGraphics);
      } finally {
        textGraphics.dispose();
      }
      g.translate(myLinkOffset, 0);
      myLink.doPaint(g);
      g.translate(-myLinkOffset, 0);
    }
    else {
      super.doPaint(g);
    }
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (myHaveLink) {
      size.width += myLinkWidth;
    }
    return size;
  }

  @Nullable
  @Override
  public Object getFragmentTagAt(int x) {
    if (myHaveLink) {
      return myLink.getFragmentTagAt(x - myLinkOffset);
    }
    return null;
  }

  private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {}

    @Override
    protected void doPaint(Graphics2D g) {
      super.doPaint(g);
    }
  }
}
