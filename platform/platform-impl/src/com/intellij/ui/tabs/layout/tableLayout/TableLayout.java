// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout.tableLayout;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.layout.TabsLayoutBase;

import java.awt.*;
import java.util.List;

public class TableLayout extends TabsLayoutBase {

  public TablePassInfo myLastTableLayout;


  private void computeLayoutTable(TablePassInfo data) {
    final Insets insets = myCallback.getLayoutInsets();
    data.toFitRec =
        new Rectangle(insets.left, insets.top, myCallback.getComponent().getWidth() - insets.left - insets.right, myCallback.getComponent()
                                                                                                                    .getHeight() - insets.top - insets.bottom);
    int eachX = data.toFitRec.x;
    TableRow eachTableRow = new TableRow(data);
    data.table.add(eachTableRow);


    data.requiredRows = 1;
    for (TabInfo eachInfo : data.myVisibleInfos) {
      final TabLabel eachLabel = myCallback.getTabLabel(eachInfo);
      final Dimension size = eachLabel.getPreferredSize();
      if (eachX + size.width >= data.toFitRec.getMaxX()) {
        data.requiredRows++;
        eachX = data.toFitRec.x;
      }
      layout(eachLabel, eachX, 0, size.width, 1);
      eachX += size.width - myCallback.getBorderThickness();
      data.requiredWidth += size.width - myCallback.getBorderThickness();
    }

    eachX = data.toFitRec.x;
    data.rowToFitMaxX = (int)data.toFitRec.getMaxX();

    if (data.requiredRows > 1) {
      final int rowFit = insets.left + data.requiredWidth / data.requiredRows;
      for (TabInfo eachInfo : data.myVisibleInfos) {
        final TabLabel eachLabel = myCallback.getTabLabel(eachInfo);
        final Rectangle eachBounds = eachLabel.getBounds();
        if (eachBounds.contains(rowFit, 0)) {
          data.rowToFitMaxX = (int)eachLabel.getBounds().getMaxX();
          break;
        }
      }
    }

    for (TabInfo eachInfo : data.myVisibleInfos) {
      final TabLabel eachLabel = myCallback.getTabLabel(eachInfo);
      final Dimension size = eachLabel.getPreferredSize();
      if (eachX + size.width <= data.rowToFitMaxX) {
        eachTableRow.add(eachInfo);
        eachX += size.width - myCallback.getBorderThickness();
      }
      else {
        eachTableRow = new TableRow(data);
        data.table.add(eachTableRow);
        eachX = insets.left + size.width;
        eachTableRow.add(eachInfo);
      }
    }
  }

  @Override
  protected LayoutPassInfo doLayout(List<TabInfo> infosToShow, boolean isForced) {
    resetLayout(true);
    Insets insets = myCallback.getLayoutInsets();
    int eachY = insets.top;
    TablePassInfo data = new TablePassInfo(infosToShow, this, myCallback);

    if (!myCallback.isHiddenTabs()) {
      computeLayoutTable(data);
      insets = myCallback.getLayoutInsets();
      eachY = insets.top;
      int eachX;

      for (TableRow eachRow : data.table) {
        eachX = insets.left;

        int deltaToFit = 0;
        boolean toAjust = false;
        if (eachRow.width < data.toFitRec.width && data.table.size() > 1) {
          deltaToFit = (int)Math.floor((double)(data.toFitRec.width - eachRow.width) / (double)eachRow.myColumns.size());
          toAjust = true;
        }

        for (int i = 0; i < eachRow.myColumns.size(); i++) {
          TabInfo tabInfo = eachRow.myColumns.get(i);
          final TabLabel label = myCallback.getTabLabel(tabInfo);


          int width;
          if (i < eachRow.myColumns.size() - 1 || !toAjust) {
            width = label.getPreferredSize().width + deltaToFit;
          }
          else {
            width = data.toFitRec.width + insets.left - eachX;
          }

          layout(label, eachX, eachY, width, data.tabsRowHeight);
          label.setAlignmentToCenter(deltaToFit > 0);

          boolean lastCell = i == eachRow.myColumns.size() - 1;
          eachX += width - (lastCell ? 0 : myCallback.getBorderThickness());
        }
        eachY += data.tabsRowHeight;
      }
    }

    if (myCallback.getSelectedInfo() != null) {
      final JBTabsImpl.Toolbar selectedToolbar = myCallback.getToolbar(myCallback.getSelectedInfo());

      final int componentY = eachY + (myCallback.isEditorTabs() ? 0 : 2) - myCallback.getLayoutInsets().top;
      if (!myCallback.isHorizontalToolbar() && selectedToolbar != null && !selectedToolbar.isEmpty()) {
        final int toolbarWidth = selectedToolbar.getPreferredSize().width;
        final int vSeparatorWidth = toolbarWidth > 0 ? myCallback.getBorderThickness() : 0;
        if (myCallback.isToolbarBeforeTabs()) {
          Rectangle compRect = layoutComp(toolbarWidth + vSeparatorWidth, componentY, 
                                          myCallback.getSelectedInfo().getComponent(), 0, 0);
          layout(selectedToolbar, compRect.x - toolbarWidth - vSeparatorWidth, compRect.y, toolbarWidth, compRect.height);
        }
        else {
          final int width = myCallback.getComponent().getWidth() - toolbarWidth - vSeparatorWidth;
          Rectangle compRect = layoutComp(new Rectangle(0, componentY, width, myCallback.getComponent().getHeight()),
                                                     myCallback.getSelectedInfo().getComponent(), 0, 0);
          layout(selectedToolbar, compRect.x + compRect.width + vSeparatorWidth, compRect.y, toolbarWidth, compRect.height);
        }
      }
      else {
        layoutComp(0, componentY, myCallback.getSelectedInfo().getComponent(), 0, 0);
      }
    }

    // TODO can be optimized by using prev dataPass
    data.myExtraBorderLines.clear();
    for (TableRow row : data.table) {
      if (!row.myColumns.isEmpty()) {
        TabInfo tabInfo = row.myColumns.get(0);
        TabLabel tabLabel = myCallback.getTabLabel(tabInfo);
        Rectangle tabLabelBounds = tabLabel.getBounds();
        int y = tabLabelBounds.y + tabLabelBounds.height - myCallback.getBorderThickness();
        data.myExtraBorderLines.add(new LayoutPassInfo.LineCoordinates(
          0, y, myCallback.getComponent().getSize().width, y));
      }
    }

    myLastTableLayout = data;
    return data;
  }

  @Override
  public int getDropIndexFor(Point point) {
    if (myLastTableLayout == null) return -1;
    int result = -1;

    Component c = myCallback.getComponent().getComponentAt(point);

    if (c instanceof JBTabsImpl) {
      for (int i = 0; i < myLastTableLayout.myVisibleInfos.size() - 1; i++) {
        TabLabel first = myCallback.getTabLabel(myLastTableLayout.myVisibleInfos.get(i));
        TabLabel second = myCallback.getTabLabel(myLastTableLayout.myVisibleInfos.get(i + 1));

        Rectangle firstBounds = first.getBounds();
        Rectangle secondBounds = second.getBounds();

        final boolean between = firstBounds.getMaxX() < point.x
                    && secondBounds.getX() > point.x
                    && firstBounds.y < point.y
                    && secondBounds.getMaxY() > point.y;

        if (between) {
          c = first;
          break;
        }
      }
    }

    if (c instanceof TabLabel) {
      TabInfo info = ((TabLabel)c).getInfo();
      int index = myLastTableLayout.myVisibleInfos.indexOf(info);
      boolean isDropTarget = myCallback.isDropTarget(info);
      if (!isDropTarget) {
        for (int i = 0; i <= index; i++) {
          if (myCallback.isDropTarget(myLastTableLayout.myVisibleInfos.get(i))) {
            index -= 1;
            break;
          }
        }
        result = index;
      } else if (index < myLastTableLayout.myVisibleInfos.size()) {
        result = index;
      }
    }
    return result;
  }

  @Override
  public boolean isSingleRow() {
    return false;
  }

  @Override
  public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
    if (myLastTableLayout == null) {
      return super.isDragOut(tabLabel, deltaX, deltaY);
    }

    Rectangle area = new Rectangle(myLastTableLayout.toFitRec.width, tabLabel.getBounds().height);
    for (int i = 0; i < myLastTableLayout.myVisibleInfos.size(); i++) {
      area = area.union(myCallback.getTabLabel(myLastTableLayout.myVisibleInfos.get(i)).getBounds());
    }
    return Math.abs(deltaY) > area.height * getDragOutMultiplier();
  }
}
