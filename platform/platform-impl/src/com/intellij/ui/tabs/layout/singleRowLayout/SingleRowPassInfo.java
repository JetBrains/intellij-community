// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout.singleRowLayout;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutCallback;
import com.intellij.ui.tabs.layout.LayoutPassInfoBase;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class SingleRowPassInfo extends LayoutPassInfoBase {
  final Dimension layoutSize;
  final int contentCount;
  int position;
  int requiredLength;
  int toFitLength;
  public final List<TabInfo> toLayout;
  public final List<TabInfo> toDrop;
  final int moreRectAxisSize;
  public Rectangle moreRect;

  public WeakReference<JComponent> hToolbar;
  public WeakReference<JComponent> vToolbar;

  public Insets insets;

  public WeakReference<JComponent> comp;
  public Rectangle tabRectangle;
  final int scrollOffset;

  int headerToFitWidth = 0;

  List<LineCoordinates> myExtraBorderLines;

  public SingleRowPassInfo(List<TabInfo> visibleInfos,
                           SingleRowLayout tabsLayout,
                           TabsLayoutCallback tabsLayoutCallback) {
    super(visibleInfos, tabsLayout, tabsLayoutCallback);
    layoutSize = tabsLayoutCallback.getComponent().getSize();
    contentCount = tabsLayoutCallback.getAllTabsCount();
    toLayout = new ArrayList<>();
    toDrop = new ArrayList<>();
    moreRectAxisSize = tabsLayout.getStrategy().getMoreRectAxisSize();
    scrollOffset = tabsLayout.getScrollOffset();
  }

  @Deprecated
  @Override
  public int getRowCount() {
    return 1;
  }

  @Deprecated
  @Override
  public int getColumnCount(final int row) {
    return myVisibleInfos.size();
  }

  @Deprecated
  @Override
  public TabInfo getTabAt(final int row, final int column) {
    return myVisibleInfos.get(column);
  }

  @Override
  public Rectangle getHeaderRectangle() {
    return (Rectangle)tabRectangle.clone();
  }

  @Override
  public List<LineCoordinates> getExtraBorderLines() {
    return myExtraBorderLines;
  }
}
