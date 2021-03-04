// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.ImmediateFullValueEvaluator;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import icons.PlatformDebuggerImplIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

import static com.intellij.util.ui.tree.TreeUtil.getNodeRowX;

class XDebuggerTreeRenderer extends ColoredTreeCellRenderer {
  private final MyColoredTreeCellRenderer myLink = new MyColoredTreeCellRenderer();
  private boolean myHaveLink;
  private int myLinkOffset;
  private int myLinkWidth;
  private Object myIconTag;

  private final MyLongTextHyperlink myLongTextLink = new MyLongTextHyperlink();

  XDebuggerTreeRenderer() {
    getIpad().right = 0;
    myLink.getIpad().left = 0;
    myUsedCustomSpeedSearchHighlighting = true;
  }

  @Override
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
    updateIcon(node);
    myIconTag = node.getIconTag();

    Rectangle treeVisibleRect = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewRect() : tree.getVisibleRect();
    int rowX = getNodeRowX(tree, row);

    if (myHaveLink) {
      setupLinkDimensions(treeVisibleRect, rowX);
    }
    else {
      int visibleRectRightX = treeVisibleRect.x + treeVisibleRect.width;
      int notFittingWidth = rowX + super.getPreferredSize().width - visibleRectRightX;
      if (node instanceof XValueNodeImpl && notFittingWidth > 0) {
        // text does not fit visible area - show link
        String rawValue = DebuggerUIUtil.getNodeRawValue((XValueNodeImpl)node);
        if (!StringUtil.isEmpty(rawValue) && tree.isShowing()) {
          Point treeRightSideOnScreen = new Point(visibleRectRightX, treeVisibleRect.y);
          SwingUtilities.convertPointToScreen(treeRightSideOnScreen, tree);
          Rectangle screen = ScreenUtil.getScreenRectangle(treeRightSideOnScreen);
          // text may fit the screen in ExpandableItemsHandler
          if (screen.x + screen.width < treeRightSideOnScreen.x + notFittingWidth) {
            myLongTextLink.setupComponent(rawValue, ((XDebuggerTree)tree).getProject());
            append(myLongTextLink.getLinkText(), myLongTextLink.getTextAttributes(), myLongTextLink);
            setupLinkDimensions(treeVisibleRect, rowX);
            myLinkWidth = 0;
          }
        }
      }
    }
    putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, myHaveLink);
    SpeedSearchUtil.applySpeedSearchHighlightingFiltered(tree, value, this, false, selected);
  }

  private void updateIcon(XDebuggerTreeNode node) {
    Icon icon = node instanceof XValueNodeImpl &&
                node.getTree().getPinToTopManager().isEnabled() &&
                node.getTree().getPinToTopManager().isItemPinned((XValueNodeImpl)node) ?
                PlatformDebuggerImplIcons.PinToTop.PinnedItem : node.getIcon();
    setIcon(icon);
  }

  private void setupLinkDimensions(Rectangle treeVisibleRect, int rowX) {
    Dimension linkSize = myLink.getPreferredSize();
    myLinkWidth = linkSize.width;
    myLinkOffset = Math.min(super.getPreferredSize().width, treeVisibleRect.x + treeVisibleRect.width - myLinkWidth - rowX);
    myLink.setSize(myLinkWidth, getHeight()); // actually we only set width here, height is not yet ready
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
      Graphics2D textGraphics = (Graphics2D)g.create(0, 0, myLinkOffset, getHeight());
      try {
        super.doPaint(textGraphics);
      } finally {
        textGraphics.dispose();
      }
      g.translate(myLinkOffset, 0);
      myLink.setSize(myLink.getWidth(), getHeight());
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
      Object linkTag = myLink.getFragmentTagAt(x - myLinkOffset);
      if (linkTag != null) {
        return linkTag;
      }
    }
    Object baseFragment = super.getFragmentTagAt(x);
    if (baseFragment != null) {
      return baseFragment;
    }
    if (myIconTag != null && findFragmentAt(x) == FRAGMENT_ICON) {
      return myIconTag;
    }
    return null;
  }

  private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {

    MyColoredTreeCellRenderer() {
      myUsedCustomSpeedSearchHighlighting = true;
    }

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

  private static class MyLongTextHyperlink extends XDebuggerTreeNodeHyperlink {
    private String myText;
    private Project myProject;

    MyLongTextHyperlink() {
      super(XDebuggerBundle.message("node.test.show.full.value"));
    }

    public void setupComponent(String text, Project project) {
      myText = text;
      myProject = project;
    }

    @Override
    public boolean alwaysOnScreen() {
      return true;
    }

    @Override
    public void onClick(MouseEvent event) {
      DebuggerUIUtil.showValuePopup(new ImmediateFullValueEvaluator(myText), event, myProject, null);
      event.consume();
    }
  }
}
