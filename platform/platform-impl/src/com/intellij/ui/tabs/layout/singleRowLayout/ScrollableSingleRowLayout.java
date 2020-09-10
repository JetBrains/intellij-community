// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout.singleRowLayout;

import com.intellij.application.options.editor.EditorTabPlacementKt;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayout;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;

/**
 * @author yole
 */
public class ScrollableSingleRowLayout extends SingleRowLayout {
  public static final int DEADZONE_FOR_DECLARE_TAB_HIDDEN = 10;
  private final Alarm afterScrollAlarm = new Alarm();
  private int myScrollOffset = 0;
  private boolean myMouseInsideTabsArea = false;
  private TabsSideSplitter mySplitter = null;

  @Override
  int getScrollOffset() {
    return myScrollOffset;
  }

  @Override
  public void scroll(int units) {
    myScrollOffset += units;
    clampScrollOffsetToBounds(myLastSingRowLayout);
  }

  @Override
  protected boolean checkLayoutLabels(SingleRowPassInfo data, boolean isForcedLayout) {
    TabInfo selectedInfo = myCallback.getSelectedInfo();
    boolean scrollSelectionInViewPending = isTabOutOfView(selectedInfo);

    if (scrollSelectionInViewPending) {
      return true;
    }
    return super.checkLayoutLabels(data, isForcedLayout);
  }

  private void clampScrollOffsetToBounds(@Nullable SingleRowPassInfo data) {
    if (data == null) {
      return;
    }
    if (data.requiredLength < data.toFitLength) {
      myScrollOffset = 0;
    }
    else {
      myScrollOffset = Math.max(0, Math.min(myScrollOffset, data.requiredLength - data.toFitLength + getStrategy().getMoreRectAxisSize()));
    }
  }

  @Override
  public int getScrollUnitIncrement() {
    if (myLastSingRowLayout != null) {
      final List<TabInfo> visibleInfos = myLastSingRowLayout.myVisibleInfos;
      if (visibleInfos.size() > 0) {
        final TabInfo info = visibleInfos.get(0);
        return getStrategy().getScrollUnitIncrement(myCallback.getTabLabel(info));
      }
    }
    return 0;
  }

  private void doScrollSelectionInView(SingleRowPassInfo passInfo) {
    if (myMouseInsideTabsArea) {
      return;
    }
    int offset = -myScrollOffset;
    for (TabInfo info : passInfo.myVisibleInfos) {
      final int length = getRequiredLength(info);
      if (info == myCallback.getSelectedInfo()) {
        if (offset < 0) {
          scroll(offset);
        }
        else {
          final int maxLength = passInfo.toFitLength - getStrategy().getMoreRectAxisSize();
          if (offset + length > maxLength) {
            // left side should be always visible
            if (length < maxLength) {
              scroll(offset + length - maxLength);
            }
            else {
              scroll(offset);
            }
          }
        }
        break;
      }
      offset += length;
    }
  }

  @Override
  protected void recomputeToLayout(SingleRowPassInfo data) {
    calculateRequiredLength(data);
    doScrollSelectionInView(data);
    clampScrollOffsetToBounds(data);
  }

  @Override
  protected void layoutMoreButton(SingleRowPassInfo data) {
    if (data.requiredLength > data.toFitLength) {
      data.moreRect = getStrategy().getMoreRect(data);
      Rectangle bounds = new Rectangle(data.moreRect);
      Dimension preferredSize = myMoreToolbar.getComponent().getPreferredSize();
      int xDelta = bounds.width - preferredSize.width;
      int yDelta = bounds.height - preferredSize.height;
      bounds.x += xDelta / 2;
      bounds.width -= xDelta;
      bounds.y += yDelta / 2;
      bounds.height -= yDelta;
      myMoreToolbar.getComponent().setBounds(bounds);
    } else {
      myMoreToolbar.getComponent().setBounds(new Rectangle());
    }
  }

  @Override
  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length) {
    if (data.requiredLength > data.toFitLength) {
      length = getStrategy().getLengthIncrement(label.getPreferredSize());
      final int moreRectSize = getStrategy().getMoreRectAxisSize();
      if (data.position + length > data.toFitLength - moreRectSize) {
        final int clippedLength = getStrategy().drawPartialOverflowTabs()
                                  ? data.toFitLength - data.position - moreRectSize : 0;
        super.applyTabLayout(data, label, clippedLength);
        label.setAlignmentToCenter(false);
        return false;
      }
    }
    return super.applyTabLayout(data, label, length);
  }

  @Override
  public boolean isTabOutOfView(TabInfo tabInfo) {
    final TabLabel label = myCallback.getTabLabel(tabInfo);
    if (label == null) {
      return false;
    }
    final Rectangle bounds = label.getBounds();
    return getStrategy().getMinPosition(bounds) < -DEADZONE_FOR_DECLARE_TAB_HIDDEN
           || bounds.width < label.getPreferredSize().width - DEADZONE_FOR_DECLARE_TAB_HIDDEN
           || bounds.height < label.getPreferredSize().height - DEADZONE_FOR_DECLARE_TAB_HIDDEN;
  }

  @Override
  protected int getSplitterSideTabsLimit() {
    return mySplitter == null ? 0 : mySplitter.getSideTabsLimit();
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(afterScrollAlarm);
    detachSplitter();
  }

  @Override
  protected SingleRowPassInfo doLayout(List<TabInfo> infosToShow, boolean isForced) {
    SingleRowPassInfo data = super.doLayout(infosToShow, isForced);
    dealWithSplitter(data);
    return data;
  }

  private void dealWithSplitter(SingleRowPassInfo data) {
    if (myCallback.getTabsPosition().isSide()) {
      attachSplitter();
      setUpSplitter(data);
    } else {
      detachSplitter();
    }
  }

  private void attachSplitter() {
    if (mySplitter == null) {
      mySplitter = new TabsSideSplitter(myCallback);
      mySplitter.getDivider().setOpaque(false);
    }

    OnePixelDivider divider = mySplitter.getDivider();
    JComponent component = myCallback.getComponent();
    if (divider.getParent() != component) {
      component.add(divider);
    }
  }

  private void setUpSplitter(SingleRowPassInfo data) {
    int dividerX = myCallback.getTabsPosition() == JBTabsPosition.left
                   ? data.insets.left + data.tabRectangle.width
                   : data.layoutSize.width - data.insets.right - data.tabRectangle.width;
    int dividerHeight = data.layoutRectWithoutInsets.height;
    mySplitter.getDivider().setBounds(dividerX, data.insets.top, 1, dividerHeight);
  }

  private void detachSplitter() {
    if (mySplitter != null) {
      OnePixelDivider divider = mySplitter.getDivider();
      if (divider.getParent() != null) {
        myCallback.getComponent().remove(divider);
      }
    }
  }

  @Nullable
  @Override
  public MouseWheelListener getMouseWheelListener() {
    return new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent event) {
        int units = event.getUnitsToScroll();
        if (units == 0) return;
        if (myLastSingRowLayout != null) {
          scroll((int)(event.getPreciseWheelRotation() * getScrollUnitIncrement()));
          myCallback.relayout(false, false);
        }
      }
    };
  }

  @Override
  public void mouseMotionEventDispatched(MouseEvent mouseMotionEvent) {
    if (myLastSingRowLayout == null) return;
    Point point = mouseMotionEvent.getPoint();
    SwingUtilities.convertPointToScreen(point, mouseMotionEvent.getComponent());
    Rectangle rect = myCallback.getComponent().getVisibleRect();
    rect = rect.intersection(myLastSingRowLayout.tabRectangle);
    Point p = rect.getLocation();
    SwingUtilities.convertPointToScreen(p, myCallback.getComponent());
    rect.setLocation(p);
    boolean inside = rect.contains(point);
    if (inside != myMouseInsideTabsArea) {
      myMouseInsideTabsArea = inside;
      afterScrollAlarm.cancelAllRequests();
      if (!inside) {
        afterScrollAlarm.addRequest(() -> {
          // here is no any "isEDT"-checks <== this task should be called in EDT <==
          // <== Alarm instance executes tasks in EDT <== used constructor of Alarm uses EDT for tasks by default
          if (!myMouseInsideTabsArea) {
            myCallback.relayout(false, false);
          }
        }, 500);
      }
    }
  }

  @Override
  public boolean ignoreTabLabelLimitedWidthWhenPaint() {
    return true;
  }

  public static class ScrollableSingleRowTabsLayoutInfo extends TabsLayoutInfo {

    @NonNls private final static String ID = "ScrollableSingleRowTabsLayoutInfo";

    private JPanel myPanel;
    public JCheckBox myCheckBox;

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public String getName() {
      return IdeBundle.message("tabs.layout.scrollable.single.row.name");
    }

    @NotNull
    @Override
    protected TabsLayout createTabsLayoutInstance() {
      return new ScrollableSingleRowLayout();
    }

    @Nullable
    @Override
    public Integer[] getAvailableTabsPositions() {
      return EditorTabPlacementKt.getTAB_PLACEMENTS();
    }
  }
}
