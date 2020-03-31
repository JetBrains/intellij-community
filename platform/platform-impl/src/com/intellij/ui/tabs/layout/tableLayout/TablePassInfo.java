// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout.tableLayout;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayout;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutCallback;
import com.intellij.ui.tabs.layout.LayoutPassInfoBase;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TablePassInfo extends LayoutPassInfoBase {
  final List<TableRow> table = new ArrayList<>();
  public Rectangle toFitRec;
  final Map<TabInfo, TableRow> myInfo2Row = new HashMap<>();

  int requiredWidth;
  int requiredRows;
  int rowToFitMaxX;

  final TabsLayoutCallback myCallback;

  List<LineCoordinates> myExtraBorderLines = new ArrayList<>();

  public TablePassInfo(List<TabInfo> visibleInfos,
                       TabsLayout tabsLayout,
                       TabsLayoutCallback tabsLayoutCallback) {
    super(visibleInfos, tabsLayout, tabsLayoutCallback);
    myCallback = tabsLayoutCallback;
  }

  public boolean isInSelectionRow(final TabInfo tabInfo) {
    final TableRow row = myInfo2Row.get(tabInfo);
    final int index = table.indexOf(row);
    return index != -1 && index == table.size() - 1;
  }

  @Deprecated
  @Override
  public int getRowCount() {
    return table.size();
  }

  @Deprecated
  @Override
  public int getColumnCount(final int row) {
    return table.get(row).myColumns.size();
  }

  @Deprecated
  @Override
  public TabInfo getTabAt(final int row, final int column) {
    return table.get(row).myColumns.get(column);
  }

  @Override
  public Rectangle getHeaderRectangle() {
    return (Rectangle)toFitRec.clone();
  }

  @Override
  public List<LineCoordinates> getExtraBorderLines() {
    return myExtraBorderLines;
  }
}
