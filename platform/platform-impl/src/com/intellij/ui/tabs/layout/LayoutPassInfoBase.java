// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayout;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutCallback;

import java.awt.*;
import java.util.List;

public abstract class LayoutPassInfoBase extends LayoutPassInfo {

  /* its a container rect without insets */
  final public Rectangle layoutRectWithoutInsets;
  final public int tabsRowHeight;


  protected LayoutPassInfoBase(List<TabInfo> visibleInfos, TabsLayout tabsLayout, TabsLayoutCallback tabsLayoutCallback) {
    super(visibleInfos);

    Insets insets = tabsLayoutCallback.getLayoutInsets();
    layoutRectWithoutInsets = new Rectangle(
      insets.left, insets.top,
      tabsLayoutCallback.getComponent().getWidth() - insets.left - insets.right,
      tabsLayoutCallback.getComponent().getHeight() - insets.top - insets.bottom);

    MaxDimensions maxDimensions = computeMaxSize(tabsLayout, tabsLayoutCallback);
    tabsRowHeight = Math.max(maxDimensions.myLabel.height, maxDimensions.myToolbar.height);


  }

  private MaxDimensions computeMaxSize(TabsLayout tabsLayout, TabsLayoutCallback tabsLayoutCallback) {
    MaxDimensions max = new MaxDimensions();
    final boolean isToolbarOnTabs = tabsLayout.isToolbarOnTabs();

    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel label = tabsLayoutCallback.getTabLabel(eachInfo);
      max.myLabel.height = Math.max(max.myLabel.height, label.getPreferredSize().height);
      max.myLabel.width = Math.max(max.myLabel.width, label.getPreferredSize().width);

      if (isToolbarOnTabs) {
        final JBTabsImpl.Toolbar toolbar = tabsLayoutCallback.getToolbar(eachInfo);
        if (toolbar != null && !toolbar.isEmpty()) {
          max.myToolbar.height = Math.max(max.myToolbar.height, toolbar.getPreferredSize().height);
          max.myToolbar.width = Math.max(max.myToolbar.width, toolbar.getPreferredSize().width);
        }
      }
    }

    return max;
  }

  @Deprecated
  @Override
  public int getRowCount() {
    return 0;
  }

  @Deprecated
  @Override
  public int getColumnCount(int row) {
    return 0;
  }

  @Deprecated
  @Override
  public TabInfo getTabAt(int row, int column) {
    return null;
  }

  private static class MaxDimensions {
    final Dimension myLabel = new Dimension();
    final Dimension myToolbar = new Dimension();
  }

}
