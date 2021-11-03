// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout.singleRowLayout;

import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutCallback;
import com.intellij.ui.tabs.layout.TabsLayoutBase;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public abstract class SingleRowLayout extends TabsLayoutBase {

  public SingleRowPassInfo myLastSingRowLayout;

  private final SingleRowLayoutStrategy myTop;
  private final SingleRowLayoutStrategy myLeft;
  private final SingleRowLayoutStrategy myBottom;
  private final SingleRowLayoutStrategy myRight;

  public final ActionToolbar myMoreToolbar;

  public SingleRowLayout() {
    myTop = new SingleRowLayoutStrategy.Top(this);
    myLeft = new SingleRowLayoutStrategy.Left(this);
    myBottom = new SingleRowLayoutStrategy.Bottom(this);
    myRight = new SingleRowLayoutStrategy.Right(this);

    ActionManager actionManager = ActionManager.getInstance();
    AnAction tabListAction = actionManager.getAction("TabList");
    myMoreToolbar = actionManager
      .createActionToolbar(ActionPlaces.TABS_MORE_TOOLBAR, new DefaultActionGroup(tabListAction), true);
    myMoreToolbar.getComponent().setBorder(JBUI.Borders.empty());
    myMoreToolbar.getComponent().setOpaque(false);
    myMoreToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
  }

  @Override
  public void init(@NotNull TabsLayoutCallback callback) {
    super.init(callback);
    myMoreToolbar.setTargetComponent(myCallback.getComponent());
    myCallback.getComponent().add(myMoreToolbar.getComponent());
  }

  SingleRowLayoutStrategy getStrategy() {
    switch (myCallback.getTabsPosition()) {
      case top:
        return myTop;
      case left:
        return myLeft;
      case bottom:
        return myBottom;
      case right:
        return myRight;
    }
    return null;
  }

  protected boolean checkLayoutLabels(SingleRowPassInfo data, boolean isForcedLayout) {
    boolean layoutLabels = true;

    if (!isForcedLayout &&
        myLastSingRowLayout != null &&
        myLastSingRowLayout.contentCount == myCallback.getAllTabsCount() &&
        myLastSingRowLayout.layoutSize.equals(myCallback.getComponent().getSize()) &&
        myLastSingRowLayout.scrollOffset == getScrollOffset()) {
      for (TabInfo each : data.myVisibleInfos) {
        final TabLabel eachLabel = myCallback.getTabLabel(each);
        if (!eachLabel.isValid()) {
          layoutLabels = true;
          break;
        }
        if (myCallback.getSelectedInfo() == each) {
          if (eachLabel.getBounds().width != 0) {
            layoutLabels = false;
          }
        }
      }
    }

    return layoutLabels;
  }

  int getScrollOffset() {
    return 0;
  }

  public void scroll(int units) {
  }

  public int getScrollUnitIncrement() {
    return 0;
  }

  @Override
  public void dispose() {
    myCallback.getComponent().remove(myMoreToolbar.getComponent());
  }

  @Override
  protected Rectangle layoutComp(int componentX, int componentY, JComponent comp, int deltaWidth, int deltaHeight) {
    return super.layoutComp(componentX, componentY, comp, deltaWidth, deltaHeight);
  }

  @Override
  protected Rectangle layoutComp(Rectangle bounds, JComponent comp, int deltaWidth, int deltaHeight) {
    return super.layoutComp(bounds, comp, deltaWidth, deltaHeight);
  }

  @Override
  protected Rectangle layout(JComponent c, int x, int y, int width, int height) {
    return super.layout(c, x, y, width, height);
  }

  @Override
  protected Rectangle layout(JComponent component, Rectangle bounds) {
    return super.layout(component, bounds);
  }

  @Override
  protected double getDragOutMultiplier() {
    return super.getDragOutMultiplier();  // just to open visibility to the package
  }

  @Override
  protected SingleRowPassInfo doLayout(List<TabInfo> infosToShow, boolean isForced) {
    SingleRowPassInfo data = new SingleRowPassInfo(infosToShow, this, myCallback);
    data.headerToFitWidth = computeHeaderToFitWidth(data);

    final boolean shouldLayoutLabels = checkLayoutLabels(data, isForced);
    if (!shouldLayoutLabels) {
      data = myLastSingRowLayout;
    }

    final TabInfo selected = myCallback.getSelectedInfo();
    prepareLayoutPassInfo(data, selected);

    resetLayout(shouldLayoutLabels || myCallback.isHiddenTabs());

    if (shouldLayoutLabels && !myCallback.isHiddenTabs()) {
      recomputeToLayout(data);

      data.position = getStrategy().getStartPosition(data) - getScrollOffset();

      layoutLabels(data);

      layoutMoreButton(data);
    }

    if (selected != null) {
      data.comp = new WeakReference<>(selected.getComponent());
      getStrategy().layoutComp(data);
    }

    if (data.toLayout.size() > 0) {
      data.tabRectangle = getStrategy().getTabRectangle(data);
    } else {
      data.tabRectangle = new Rectangle();
    }

    layoutExtraBorderLines(data);

    myLastSingRowLayout = data;
    return data;
  }

  private void layoutExtraBorderLines(SingleRowPassInfo data) {
    data.myExtraBorderLines = new ArrayList<>();

    if (data.toLayout.size() > 0) {
      LayoutPassInfo.LineCoordinates borderLine = getStrategy().computeExtraBorderLine(data);
      data.myExtraBorderLines.add(borderLine);
    }

    if (data.vToolbar != null && data.vToolbar.get() != null) {
      Rectangle bounds = data.vToolbar.get().getBounds();
      data.myExtraBorderLines.add(new LayoutPassInfo.LineCoordinates(
        bounds.x + bounds.width, bounds.y,
        bounds.x + bounds.width, bounds.y + bounds.height));
    }
  }

  protected void prepareLayoutPassInfo(SingleRowPassInfo data, TabInfo selected) {
    data.insets = myCallback.getLayoutInsets();
    if (myCallback.isHorizontalToolbar()) {
      data.insets.left += myCallback.getFirstTabOffset();
    }

    final JBTabsImpl.Toolbar selectedToolbar = myCallback.getToolbar(selected);
    data.hToolbar =
      new WeakReference<>(selectedToolbar != null && myCallback.isHorizontalToolbar() && !selectedToolbar.isEmpty() ? selectedToolbar : null);
    data.vToolbar =
      new WeakReference<>(selectedToolbar != null && !myCallback.isHorizontalToolbar() && !selectedToolbar.isEmpty() ? selectedToolbar : null);
    data.toFitLength = getStrategy().getToFitLength(data);
  }

  protected void layoutMoreButton(SingleRowPassInfo data) {
    if (data.toDrop.size() > 0) {
      data.moreRect = getStrategy().getMoreRect(data);
    }
  }

  protected void layoutLabels(final SingleRowPassInfo data) {
    boolean layoutStopped = false;
    for (TabInfo eachInfo : data.toLayout) {
      final TabLabel label = myCallback.getTabLabel(eachInfo);
      if (layoutStopped) {
        final Rectangle rec = getStrategy().getLayoutRect(data, 0, 0);
        layout(label, rec);
        continue;
      }

      final Dimension eachSize = label.getPreferredSize();

      int length = getStrategy().getLengthIncrement(eachSize);
      boolean continueLayout = applyTabLayout(data, label, length);

      data.position = getStrategy().getMaxPosition(label.getBounds());
      data.position -= myCallback.getBorderThickness();

      if (!continueLayout) {
        layoutStopped = true;
      }
    }

    for (TabInfo eachInfo : data.toDrop) {
      resetLayout(myCallback.getTabLabel(eachInfo));
    }
  }

  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length) {
    final Rectangle rec = getStrategy().getLayoutRect(data, data.position, length);
    layout(label, rec);

    label.setAlignmentToCenter(myCallback.isEditorTabs() && getStrategy().isToCenterTextWhenStretched());
    return true;
  }


  protected abstract void recomputeToLayout(final SingleRowPassInfo data);

  protected void calculateRequiredLength(SingleRowPassInfo data) {
    for (TabInfo eachInfo : data.myVisibleInfos) {
      data.requiredLength += getRequiredLength(eachInfo);
      if (myCallback.getTabsPosition().isSide()) {
        data.requiredLength -= 1;
      }
      data.toLayout.add(eachInfo);
    }
  }

  protected int getRequiredLength(TabInfo eachInfo) {
    TabLabel label = myCallback.getTabLabel(eachInfo);
    return getStrategy().getLengthIncrement(label != null ? label.getPreferredSize() : new Dimension())
                                      - (myCallback.isEditorTabs() ? myCallback.getBorderThickness() : 0);
  }

  public boolean isTabOutOfView(TabInfo tabInfo) {
    return myLastSingRowLayout != null && myLastSingRowLayout.toDrop.contains(tabInfo);
  }

  @Override
  public int getDropIndexFor(Point point) {
    if (myLastSingRowLayout == null) return -1;

    int result = -1;

    Component c = myCallback.getComponent().getComponentAt(point);

    if (c == myCallback.getComponent()) {
      for (int i = 0; i < myLastSingRowLayout.myVisibleInfos.size() - 1; i++) {
        TabLabel first = myCallback.getTabLabel(myLastSingRowLayout.myVisibleInfos.get(i));
        TabLabel second = myCallback.getTabLabel(myLastSingRowLayout.myVisibleInfos.get(i + 1));

        Rectangle firstBounds = first.getBounds();
        Rectangle secondBounds = second.getBounds();

        final boolean between;

        boolean horizontal = getStrategy() instanceof SingleRowLayoutStrategy.Horizontal;
        if (horizontal) {
          between = firstBounds.getMaxX() < point.x
                    && secondBounds.getX() > point.x
                    && firstBounds.y < point.y
                    && secondBounds.getMaxY() > point.y;
        } else {
          between = firstBounds.getMaxY() < point.y
                    && secondBounds.getY() > point.y
                    && firstBounds.x < point.x
                    && secondBounds.getMaxX() > point.x;
        }

        if (between) {
          c = first;
          break;
        }
      }

    }

    if (c instanceof TabLabel) {
      TabInfo info = ((TabLabel)c).getInfo();
      int index = myLastSingRowLayout.myVisibleInfos.indexOf(info);
      boolean isDropTarget = myCallback.isDropTarget(info);
      if (!isDropTarget) {
        for (int i = 0; i <= index; i++) {
          if (myCallback.isDropTarget(myLastSingRowLayout.myVisibleInfos.get(i))) {
            index -= 1;
            break;
          }
        }
        result = index;
      } else if (index < myLastSingRowLayout.myVisibleInfos.size()) {
        result = index;
      }
    }

    return result;
  }

  @Override
  public boolean isToolbarOnTabs() {
    return getStrategy().isToolbarOnTabs();
  }

  @Override
  public boolean isSingleRow() {
    return true;
  }

  @Override
  public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
    return getStrategy().isDragOut(tabLabel, deltaX, deltaY);
  }

  public TabsLayoutCallback getCallback() {
    return myCallback;
  }

  private int computeHeaderToFitWidth(SingleRowPassInfo data) {
    if (!myCallback.getTabsPosition().isSide()) {
      return data.layoutRectWithoutInsets.width;
    } else {
      int max = 0;
      for (TabInfo eachInfo : data.myVisibleInfos) {
        TabLabel label = myCallback.getTabLabel(eachInfo);
        max = Math.max(max, label.getPreferredSize().width);
      }
      int splitterSideTabsLimit = getSplitterSideTabsLimit();
      if (splitterSideTabsLimit > 0) {
        max = Math.min(max, splitterSideTabsLimit);
      }
      return max;
    }
  }

  protected int getSplitterSideTabsLimit() {
    return 0;
  }
}
