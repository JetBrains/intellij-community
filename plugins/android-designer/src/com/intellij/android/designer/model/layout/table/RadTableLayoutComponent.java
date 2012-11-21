/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model.layout.table;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.SdkConstants;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewContainer;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.IGridProvider;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadTableLayoutComponent extends RadViewContainer implements IGridProvider {
  private GridInfo myGridInfo;
  private GridInfo myVirtualGridInfo;

  @Override
  public void setViewInfo(ViewInfo viewInfo) {
    super.setViewInfo(viewInfo);
    myGridInfo = null;
    myVirtualGridInfo = null;
  }

  private int[] getColumnWidths() {
    try {
      Object viewObject = myViewInfo.getViewObject();
      Class<?> viewClass = viewObject.getClass();
      Field maxWidths = viewClass.getDeclaredField("mMaxWidths");
      maxWidths.setAccessible(true);
      int[] columnWidths = (int[])maxWidths.get(viewObject);
      return columnWidths == null ? ArrayUtil.EMPTY_INT_ARRAY : columnWidths;
    }
    catch (Throwable e) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }
  }

  public GridInfo getGridInfo() {
    if (myGridInfo == null) {
      myGridInfo = new GridInfo();

      int[] columnWidths = getColumnWidths();
      if (columnWidths.length > 0) {
        myGridInfo.emptyColumns = new boolean[columnWidths.length];
        myGridInfo.vLines = new int[columnWidths.length + 1];

        for (int i = 0; i < columnWidths.length; i++) {
          int width = Math.max(columnWidths[i], 0);
          myGridInfo.emptyColumns[i] = width == 0;

          if (width == 0) {
            width = 2;
            if (i + 1 < columnWidths.length) {
              columnWidths[i + 1] -= width;
            }
          }

          myGridInfo.width += width;
          myGridInfo.vLines[i + 1] = myGridInfo.width;
        }
      }

      List<RadComponent> rows = getChildren();
      if (!rows.isEmpty()) {
        Rectangle bounds = getBounds();
        if (columnWidths.length == 0) {
          myGridInfo.width = bounds.width;
        }

        myGridInfo.hLines = new int[rows.size() + 1];
        int index = 1;
        for (RadComponent row : rows) {
          Rectangle rowBounds = row.getBounds();
          myGridInfo.hLines[index++] = myGridInfo.height = rowBounds.y - bounds.y + rowBounds.height;
        }
      }
    }
    return myGridInfo;
  }

  public GridInfo getVirtualGridInfo() {
    if (myVirtualGridInfo == null) {
      myVirtualGridInfo = new GridInfo();
      GridInfo gridInfo = getGridInfo();
      Rectangle bounds = getBounds();

      myVirtualGridInfo.width = bounds.width;
      myVirtualGridInfo.height = bounds.height;

      int deltaWidth = bounds.width - (gridInfo.vLines.length == 0 ? 0 : gridInfo.width);
      myVirtualGridInfo.vLines = GridInfo.addLineInfo(gridInfo.vLines, deltaWidth);

      int deltaHeight = bounds.height - gridInfo.height;
      myVirtualGridInfo.hLines = GridInfo.addLineInfo(gridInfo.hLines, deltaHeight);

      List<RadComponent> rows = getChildren();
      if (!rows.isEmpty()) {
        int columnSize = Math.max(1, gridInfo.vLines.length - 1);
        if (deltaWidth < 2) {
          myVirtualGridInfo.lastInsertColumn = columnSize - 1;
        }
        if (deltaHeight < 2) {
          myVirtualGridInfo.lastInsertRow = rows.size() - 1;
        }

        myVirtualGridInfo.components = getGridComponents(true);
      }
    }
    return myVirtualGridInfo;
  }

  public RadComponent[][] getGridComponents(boolean fillSpans) {
    GridInfo gridInfo = getGridInfo();
    List<RadComponent> rows = getChildren();
    int columnSize = Math.max(1, gridInfo.vLines.length - 1);
    RadComponent[][] components = new RadComponent[rows.size()][columnSize];

    for (int i = 0; i < components.length; i++) {
      RadComponent row = rows.get(i);

      if (RadTableRowLayout.is(row)) {
        int index = 0;
        for (RadComponent column : row.getChildren()) {
          int cellIndex = getCellIndex(column);
          if (cellIndex > index) {
            index = cellIndex;
          }

          int cellSpan = getCellSpan(column);
          if (fillSpans) {
            for (int j = 0; j < cellSpan; j++) {
              components[i][index++] = column;
            }
          }
          else {
            components[i][index] = column;
            index += cellSpan;
          }
        }
      }
      else {
        components[i][0] = row;
      }
    }

    return components;
  }

  public static int getCellIndex(RadComponent component) {
    try {
      String column = ((RadViewComponent)component).getTag().getAttributeValue("layout_column", SdkConstants.NS_RESOURCES);
      return Integer.parseInt(column);
    }
    catch (Throwable e) {
      return -1;
    }
  }

  public static int getCellSpan(RadComponent component) {
    try {
      String span = ((RadViewComponent)component).getTag().getAttributeValue("layout_span", SdkConstants.NS_RESOURCES);
      return Integer.parseInt(span);
    }
    catch (Throwable e) {
      return 1;
    }
  }

  public static void setCellIndex(final RadComponent component, final int column) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)component).getTag();
        tag.setAttribute("layout_column", SdkConstants.NS_RESOURCES, Integer.toString(column));
        ModelParser.deleteAttribute(tag, "layout_span");
      }
    });
  }

  public static void setCellSpan(final RadComponent component, final int span) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)component).getTag();
        tag.setAttribute("layout_span", SdkConstants.NS_RESOURCES, Integer.toString(span));
      }
    });
  }
}