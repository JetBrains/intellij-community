// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout.singleRowLayout;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.ShapeTransform;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;

import javax.swing.*;
import java.awt.*;

public abstract class SingleRowLayoutStrategy {

  private static final int MIN_TAB_WIDTH = 50;
  private static final int SELECTION_TAB_V_SHIFT = 2;
  final SingleRowLayout myLayout;

  protected SingleRowLayoutStrategy(final SingleRowLayout layout) {
    myLayout = layout;
  }

  abstract int getMoreRectAxisSize();

  public abstract int getStartPosition(final SingleRowPassInfo data);

  public abstract int getToFitLength(final SingleRowPassInfo data);

  public abstract int getLengthIncrement(final Dimension dimension);

  public abstract int getMinPosition(final Rectangle bounds);

  public abstract int getMaxPosition(final Rectangle bounds);

  protected abstract int getFixedFitLength(final SingleRowPassInfo data);

  public Rectangle getLayoutRect(final SingleRowPassInfo data, final int position, final int length) {
    return getLayoutRec(position, getFixedPosition(data), length, getFixedFitLength(data));
  }

  protected abstract Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength);

  protected abstract Rectangle getTabRectangle(final SingleRowPassInfo data);

  protected abstract int getFixedPosition(final SingleRowPassInfo data);

  public abstract Rectangle getMoreRect(final SingleRowPassInfo data);

  public abstract boolean isToCenterTextWhenStretched();

  public abstract ShapeTransform createShapeTransform(Rectangle rectangle);

  public abstract void layoutComp(SingleRowPassInfo data);

  public boolean isToolbarOnTabs() {
    return false;
  }

  public abstract boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY);

  /**
   * Whether a tab that didn't fit completely on the right/bottom side in scrollable layout should be clipped or hidden altogether.
   *
   * @return true if the tab should be clipped, false if hidden.
   */
  public abstract boolean drawPartialOverflowTabs();

  /**
   * Return the change of scroll offset for every unit of mouse wheel scrolling.
   *
   * @param label the first visible tab label
   * @return the scroll amount
   */
  public abstract int getScrollUnitIncrement(TabLabel label);

  public abstract LayoutPassInfo.LineCoordinates computeExtraBorderLine(SingleRowPassInfo data);

  abstract static class Horizontal extends SingleRowLayoutStrategy {
    protected Horizontal(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public boolean isToCenterTextWhenStretched() {
      return true;
    }

    @Override
    public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
      Rectangle bounds = tabLabel.getBounds();
      if (bounds.x + bounds.width + deltaX < 0 || bounds.x + bounds.width > tabLabel.getParent().getWidth()) return true;
      return Math.abs(deltaY) > tabLabel.getHeight() * myLayout.getDragOutMultiplier();
    }

    @Override
    public int getMoreRectAxisSize() {
      return ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width + 6;
    }

    @Override
    public int getToFitLength(final SingleRowPassInfo data) {
      JComponent hToolbar = data.hToolbar.get();
      if (hToolbar != null) {
        return data.layoutRectWithoutInsets.width - hToolbar.getMinimumSize().width;
      } else {
        return data.layoutRectWithoutInsets.width;
      }
    }

    @Override
    public int getLengthIncrement(final Dimension labelPrefSize) {
      return myLayout.getCallback().isEditorTabs() ? Math.max(labelPrefSize.width, MIN_TAB_WIDTH) : labelPrefSize.width;
    }

    @Override
    public int getMinPosition(Rectangle bounds) {
      return (int)bounds.getX();
    }

    @Override
    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxX();
    }

    @Override
    public int getFixedFitLength(final SingleRowPassInfo data) {
      return data.tabsRowHeight;
    }

    @Override
    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(position, fixedPos, length, fixedFitLength);
    }

    @Override
    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    @Override
    public boolean drawPartialOverflowTabs() {
      return true;
    }

    @Override
    public int getScrollUnitIncrement(TabLabel label) {
      return 10;
    }
  }

  static class Top extends Horizontal {

    Top(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    protected Rectangle getTabRectangle(SingleRowPassInfo data) {
      return new Rectangle(data.layoutRectWithoutInsets.x, data.layoutRectWithoutInsets.y,
                           data.layoutRectWithoutInsets.width, data.tabsRowHeight);
    }

    @Override
    public boolean isToolbarOnTabs() {
      return myLayout.getCallback().isHorizontalToolbar() && myLayout.getCallback().isToolbarOnTabs();
    }

    @Override
    public LayoutPassInfo.LineCoordinates computeExtraBorderLine(SingleRowPassInfo data) {
      int x1 = 0;
      int x2 = myLayout.getCallback().getComponent().getSize().width;
      int y = data.tabRectangle.y + data.tabRectangle.height - myLayout.getCallback().getBorderThickness();
      return new LayoutPassInfo.LineCoordinates(x1, y, x2, y);
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Top(labelRec);
    }

    @Override
    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      int x;
      if (myLayout.getCallback().isEditorTabs()) {
        x = data.layoutSize.width - data.moreRectAxisSize;
      }
      else {
        x = data.position;
      }
      return new Rectangle(x, data.insets.top, data.moreRectAxisSize, data.tabsRowHeight);
    }


    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myLayout.getCallback().isHiddenTabs()) {
        layoutComp(data, 0, 0, 0, 0);
      } else {
        JComponent vToolbar = data.vToolbar.get();
        final int vToolbarWidth = vToolbar != null ? vToolbar.getPreferredSize().width : 0;
        final int vSeparatorWidth = vToolbarWidth > 0 ? myLayout.getCallback().getBorderThickness() : 0;
        final int x = vToolbarWidth > 0 ? vToolbarWidth + vSeparatorWidth : 0;
        JComponent hToolbar = data.hToolbar.get();
        final int hToolbarHeight = !myLayout.getCallback().isToolbarOnTabs() && hToolbar != null ? hToolbar.getPreferredSize().height : 0;
        final int y = data.tabsRowHeight + (Math.max(hToolbarHeight, 0));

        JComponent comp = data.comp.get();
        if (hToolbar != null) {
          final Rectangle compBounds = myLayout.layoutComp(x, y, comp, 0, 0);
          if (myLayout.getCallback().isToolbarOnTabs()) {
            int toolbarX = (data.moreRect != null ? (int)data.moreRect.getMaxX() : data.position) + myLayout.getCallback().getToolbarInsetForOnTabsMode();
            final Rectangle rec = new Rectangle(
              toolbarX, data.insets.top,
              myLayout.getCallback().getComponent().getSize().width - data.insets.left - toolbarX,
              data.tabsRowHeight - myLayout.getCallback().getBorderThickness()); // we use "-borderThickness" because otherwise the toolbar can overlap and hide the border line
            myLayout.layout(hToolbar, rec);
          } else {
            final int toolbarHeight = hToolbar.getPreferredSize().height;
            myLayout.layout(hToolbar, compBounds.x, compBounds.y - toolbarHeight, compBounds.width, toolbarHeight);
          }
        } else if (vToolbar != null) {
          if (myLayout.getCallback().isToolbarBeforeTabs()) {
            final Rectangle compBounds = myLayout.layoutComp(x, y, comp, 0, 0);
            myLayout.layout(vToolbar, compBounds.x - vToolbarWidth - vSeparatorWidth, compBounds.y, vToolbarWidth, compBounds.height);
          } else {
            int width = vToolbarWidth > 0
                        ? myLayout.getCallback().getComponent().getWidth() - vToolbarWidth - vSeparatorWidth
                        : myLayout.getCallback().getComponent().getWidth();
            final Rectangle compBounds = myLayout.layoutComp(
              new Rectangle(0, y, width, myLayout.getCallback().getComponent().getHeight()),
              comp, 0, 0);
            myLayout.layout(vToolbar, compBounds.x + compBounds.width + vSeparatorWidth, compBounds.y, vToolbarWidth, compBounds.height);
          }
        } else {
          myLayout.layoutComp(x, y, comp, 0, 0);
        }
      }
    }
  }

  public void layoutComp(SingleRowPassInfo data, int deltaX, int deltaY, int deltaWidth, int deltaHeight) {
    JComponent hToolbar = data.hToolbar.get();
    JComponent vToolbar = data.vToolbar.get();
    if (hToolbar != null) {
      final int toolbarHeight = hToolbar.getPreferredSize().height;
      final int hSeparatorHeight = toolbarHeight > 0 ? 1 : 0;
      final Rectangle compRect =
        myLayout.layoutComp(deltaX, toolbarHeight + hSeparatorHeight + deltaY, data.comp.get(), deltaWidth, deltaHeight);
      myLayout.layout(hToolbar, compRect.x, compRect.y - toolbarHeight - hSeparatorHeight, compRect.width, toolbarHeight);
    }
    else if (vToolbar != null) {
      final int toolbarWidth = vToolbar.getPreferredSize().width;
      final int vSeparatorWidth = toolbarWidth > 0 ? 1 : 0;
      if (myLayout.getCallback().isToolbarBeforeTabs()) {
        final Rectangle compRect =
          myLayout.layoutComp(toolbarWidth + vSeparatorWidth + deltaX, deltaY, data.comp.get(), deltaWidth, deltaHeight);
        myLayout.layout(vToolbar, compRect.x - toolbarWidth - vSeparatorWidth, compRect.y, toolbarWidth, compRect.height);
      }
      else {
        final Rectangle compRect = myLayout.layoutComp(
          new Rectangle(deltaX, deltaY, myLayout.getCallback().getComponent().getWidth() - toolbarWidth - vSeparatorWidth,
                        myLayout.getCallback().getComponent().getHeight()),
          data.comp.get(), deltaWidth, deltaHeight);
        myLayout.layout(vToolbar, compRect.x + compRect.width + vSeparatorWidth, compRect.y, toolbarWidth, compRect.height);
      }
    }
    else {
      myLayout.layoutComp(deltaX, deltaY, data.comp.get(), deltaWidth, deltaHeight);
    }
  }

  static class Bottom extends Horizontal {
    Bottom(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    protected Rectangle getTabRectangle(SingleRowPassInfo data) {
      return new Rectangle(data.layoutRectWithoutInsets.x,
                           data.layoutRectWithoutInsets.y + data.layoutRectWithoutInsets.height - data.tabsRowHeight,
                           data.layoutRectWithoutInsets.width,
                           data.tabsRowHeight);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myLayout.getCallback().isHiddenTabs()) {
        layoutComp(data, 0, 0, 0, 0);
      } else {
        layoutComp(data, 0, 0, 0, -(data.tabsRowHeight));
      }
    }

    @Override
    public LayoutPassInfo.LineCoordinates computeExtraBorderLine(SingleRowPassInfo data) {
      int x1 = 0;
      int x2 = myLayout.getCallback().getComponent().getSize().width;
      int y = data.tabRectangle.y;
      return new LayoutPassInfo.LineCoordinates(x1, y, x2, y);
    }

    @Override
    public int getFixedPosition(final SingleRowPassInfo data) {
      return myLayout.getCallback().getComponent().getSize().height - data.insets.bottom - data.tabsRowHeight;
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(myLayout.getCallback().getComponent().getWidth() - data.insets.right - data.moreRectAxisSize + 2, getFixedPosition(data),
                           data.moreRectAxisSize - 1, data.tabsRowHeight);
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Bottom(labelRec);
    }
  }

  abstract static class Vertical extends SingleRowLayoutStrategy {

    protected Vertical(SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
      Rectangle bounds = tabLabel.getBounds();
      if (bounds.y + bounds.height + deltaX < 0 || bounds.y + bounds.height > tabLabel.getParent().getHeight()) return true;
      return Math.abs(deltaX) > tabLabel.getWidth() * TabLayout.getDragOutMultiplier();
    }

    @Override
    public boolean isToCenterTextWhenStretched() {
      return false;
    }

    @Override
    int getMoreRectAxisSize() {
      return ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width + 6;
    }

    @Override
    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    @Override
    public int getToFitLength(final SingleRowPassInfo data) {
      return data.layoutRectWithoutInsets.height;
    }

    @Override
    public int getLengthIncrement(final Dimension labelPrefSize) {
      return labelPrefSize.height;
    }

    @Override
    public int getMinPosition(Rectangle bounds) {
      return (int) bounds.getMinY();
    }

    @Override
    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxY();
    }

    @Override
    public int getFixedFitLength(final SingleRowPassInfo data) {
      return data.headerToFitWidth;
    }

    @Override
    public boolean drawPartialOverflowTabs() {
      return false;
    }

    @Override
    public int getScrollUnitIncrement(TabLabel label) {
      return label.getPreferredSize().height;
    }
  }

  static class Left extends Vertical {
    Left(final SingleRowLayout layout) {
      super(layout);
    }


    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myLayout.getCallback().isHiddenTabs()) {
        layoutComp(data, 0, 0, 0, 0);
      } else {
        layoutComp(data, data.headerToFitWidth, 0, 0, 0);
      }
    }

    @Override
    public LayoutPassInfo.LineCoordinates computeExtraBorderLine(SingleRowPassInfo data) {
      int x = data.tabRectangle.x + data.tabRectangle.width - myLayout.getCallback().getBorderThickness();
      int y1 = 0;
      int y2 = myLayout.getCallback().getComponent().getSize().height;
      return new LayoutPassInfo.LineCoordinates(x, y1, x, y2);
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Left(labelRec);
    }

    @Override
    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength, length);
    }

    @Override
    protected Rectangle getTabRectangle(SingleRowPassInfo data) {
      return new Rectangle(data.layoutRectWithoutInsets.x,
                           data.layoutRectWithoutInsets.y,
                           data.headerToFitWidth,
                           data.layoutRectWithoutInsets.height);
    }

    @Override
    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(data.insets.left + SELECTION_TAB_V_SHIFT,
                           myLayout.getCallback().getComponent().getHeight() - data.insets.bottom - data.moreRectAxisSize - 1,
                           data.headerToFitWidth,
                           data.moreRectAxisSize - 1);
    }

  }

  static class Right extends Vertical {
    Right(SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myLayout.getCallback().isHiddenTabs()) {
        layoutComp(data, 0, 0, 0, 0);
      } else {
        layoutComp(data, 0, 0, -data.headerToFitWidth, 0);
      }
    }

    @Override
    public LayoutPassInfo.LineCoordinates computeExtraBorderLine(SingleRowPassInfo data) {
      int x = data.tabRectangle.x;
      int y1 = 0;
      int y2 = myLayout.getCallback().getComponent().getSize().height;
      return new LayoutPassInfo.LineCoordinates(x, y1, x, y2);
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Right(labelRec);
    }

    @Override
    public Rectangle getLayoutRec(int position, int fixedPos, int length, int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength - 1, length);
    }

    @Override
    protected Rectangle getTabRectangle(SingleRowPassInfo data) {
      return new Rectangle(data.layoutRectWithoutInsets.x + data.layoutRectWithoutInsets.width - data.headerToFitWidth,
                           data.layoutRectWithoutInsets.y,
                           data.headerToFitWidth,
                           data.layoutRectWithoutInsets.height);
    }

    @Override
    public int getFixedPosition(SingleRowPassInfo data) {
      return data.layoutSize.width - data.headerToFitWidth - data.insets.right;
    }

    @Override
    public Rectangle getMoreRect(SingleRowPassInfo data) {
      return new Rectangle(data.layoutSize.width - data.insets.right - data.headerToFitWidth,
                           myLayout.getCallback().getComponent().getHeight() - data.insets.bottom - data.moreRectAxisSize - 1,
                           data.headerToFitWidth,
                           data.moreRectAxisSize - 1);
    }
  }

}
