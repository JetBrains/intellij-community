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
package com.intellij.android.designer.model.layout.grid;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.SdkConstants;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewContainer;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.IGridProvider;
import com.intellij.designer.componentTree.AttributeWrapper;
import com.intellij.designer.model.IComponentDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * @author Alexander Lobas
 */
public class RadGridLayoutComponent extends RadViewContainer implements IComponentDecorator, IGridProvider {
  private GridInfo myGridInfo;
  private GridInfo myVirtualGridInfo;

  @Override
  public void decorateTree(SimpleColoredComponent renderer, AttributeWrapper wrapper) {
    XmlTag tag = getTag();
    StringBuilder value = new StringBuilder(" (");

    String rowCount = tag.getAttributeValue("rowCount", SdkConstants.NS_RESOURCES);
    value.append(StringUtil.isEmpty(rowCount) ? "?" : rowCount).append(", ");

    String columnCount = tag.getAttributeValue("columnCount", SdkConstants.NS_RESOURCES);
    value.append(StringUtil.isEmpty(columnCount) ? "?" : columnCount).append(", ");

    value.append(isHorizontal() ? "horizontal" : "vertical");

    renderer.append(value.append(")").toString(), wrapper.getAttribute(SimpleTextAttributes.REGULAR_ATTRIBUTES));
  }

  public boolean isHorizontal() {
    return !"vertical".equals(getTag().getAttributeValue("orientation", SdkConstants.NS_RESOURCES));
  }

  @Override
  public void setViewInfo(ViewInfo viewInfo) {
    super.setViewInfo(viewInfo);
    myGridInfo = null;
    myVirtualGridInfo = null;
  }

  @Override
  public GridInfo getGridInfo() {
    if (myGridInfo == null) {
      myGridInfo = new GridInfo();

      try {
        Object viewObject = myViewInfo.getViewObject();
        Class<?> viewClass = viewObject.getClass();

        myGridInfo.rowCount = (Integer)viewClass.getMethod("getRowCount").invoke(viewObject);
        myGridInfo.columnCount = (Integer)viewClass.getMethod("getColumnCount").invoke(viewObject);

        Field field_horizontalAxis = viewClass.getDeclaredField("horizontalAxis");
        field_horizontalAxis.setAccessible(true);
        Object horizontalAxis = field_horizontalAxis.get(viewObject);

        Class<?> class_Axis = horizontalAxis.getClass();

        Field field_locations = class_Axis.getField("locations");
        field_locations.setAccessible(true);

        myGridInfo.vLines = (int[])field_locations.get(horizontalAxis);
        myGridInfo.emptyColumns = configureEmptyLines(myGridInfo.vLines);

        Field field_verticalAxis = viewClass.getDeclaredField("verticalAxis");
        field_verticalAxis.setAccessible(true);
        Object verticalAxis = field_verticalAxis.get(viewObject);

        myGridInfo.hLines = (int[])field_locations.get(verticalAxis);
        myGridInfo.emptyRows = configureEmptyLines(myGridInfo.hLines);

        Rectangle bounds = getBounds();

        for (RadComponent child : getChildren()) {
          Rectangle childBounds = child.getBounds();
          myGridInfo.width = Math.max(myGridInfo.width, childBounds.x + childBounds.width - bounds.x);
          myGridInfo.height = Math.max(myGridInfo.height, childBounds.y + childBounds.height - bounds.y);
        }

        if (myGridInfo.vLines != null && myGridInfo.vLines.length > 0) {
          myGridInfo.vLines[myGridInfo.vLines.length - 1] = myGridInfo.width;
        }
        if (myGridInfo.hLines != null && myGridInfo.hLines.length > 0) {
          myGridInfo.hLines[myGridInfo.hLines.length - 1] = myGridInfo.height;
        }
      }
      catch (Throwable e) {
      }
    }
    return myGridInfo;
  }

  private static final int EMPTY_CELL = 5;

  private static boolean[] configureEmptyLines(int[] lines) {
    boolean[] empty = new boolean[lines.length - 1];
    int[] originalLines = Arrays.copyOf(lines, lines.length);

    for (int i = 0; i < empty.length; i++) {
      int line_i = originalLines[i];
      int length = originalLines[i + 1] - line_i;
      empty[i] = length == 0;

      if (length == 0) {
        int startMove = i + 1;
        while (startMove < lines.length && line_i == lines[startMove]) {
          startMove++;
        }

        for (int j = i + 1; j < lines.length; j++) {
          lines[j] += EMPTY_CELL;
        }
        for (int j = startMove; j < lines.length; j++) {
          if (lines[j - 1] < lines[j] - 2 * EMPTY_CELL) {
            lines[j] -= 2 * EMPTY_CELL;
          }
        }
      }
    }

    return empty;
  }

  @Override
  public GridInfo getVirtualGridInfo() {
    if (myVirtualGridInfo == null) {
      myVirtualGridInfo = new GridInfo();
      GridInfo gridInfo = getGridInfo();
      Rectangle bounds = getBounds();

      myVirtualGridInfo.rowCount = gridInfo.rowCount;
      myVirtualGridInfo.columnCount = gridInfo.columnCount;

      myVirtualGridInfo.width = bounds.width;
      myVirtualGridInfo.height = bounds.height;

      int deltaWidth = bounds.width - gridInfo.width;
      myVirtualGridInfo.vLines = GridInfo.addLineInfo(gridInfo.vLines, deltaWidth);

      if (deltaWidth < 2) {
        myVirtualGridInfo.lastInsertColumn = gridInfo.columnCount - 1;
      }

      int deltaHeight = bounds.height - gridInfo.height;
      myVirtualGridInfo.hLines = GridInfo.addLineInfo(gridInfo.hLines, deltaHeight);

      if (deltaHeight < 2) {
        myVirtualGridInfo.lastInsertRow = gridInfo.rowCount - 1;
      }

      myVirtualGridInfo.components = getGridComponents(true);
    }

    return myVirtualGridInfo;
  }

  public RadComponent[][] getGridComponents(boolean fillSpans) {
    GridInfo gridInfo = getGridInfo();
    RadComponent[][] components = new RadComponent[gridInfo.rowCount][gridInfo.columnCount];

    for (RadComponent child : getChildren()) {
      Rectangle cellInfo = getCellInfo(child);

      if (fillSpans) {
        int rowEnd = Math.min(cellInfo.y + cellInfo.height, gridInfo.rowCount);
        int columnEnd = Math.min(cellInfo.x + cellInfo.width, gridInfo.columnCount);
        for (int row = cellInfo.y; row < rowEnd; row++) {
          for (int column = cellInfo.x; column < columnEnd; column++) {
            components[row][column] = child;
          }
        }
      }
      else if (cellInfo.y < gridInfo.rowCount && cellInfo.x < gridInfo.columnCount) {
        components[cellInfo.y][cellInfo.x] = child;
      }
    }

    return components;
  }

  public static Rectangle getCellInfo(RadComponent component) {
    Rectangle cellInfo = new Rectangle();

    try {
      Object layoutParams = ((RadViewComponent)component).getViewInfo().getLayoutParamsObject();
      Class<?> layoutParamsClass = layoutParams.getClass();

      Object columnSpec = layoutParamsClass.getField("columnSpec").get(layoutParams);
      Object rowSpec = layoutParamsClass.getField("rowSpec").get(layoutParams);

      Class<?> class_Spec = columnSpec.getClass();
      Field field_span = class_Spec.getDeclaredField("span");
      field_span.setAccessible(true);

      Object columnSpan = field_span.get(columnSpec);
      Object rowSpan = field_span.get(rowSpec);

      Class<?> class_Interval = columnSpan.getClass();
      Field field_min = class_Interval.getField("min");
      field_min.setAccessible(true);
      Field field_max = class_Interval.getField("max");
      field_max.setAccessible(true);

      cellInfo.x = field_min.getInt(columnSpan);
      cellInfo.y = field_min.getInt(rowSpan);
      cellInfo.width = field_max.getInt(columnSpan) - cellInfo.x;
      cellInfo.height = field_max.getInt(rowSpan) - cellInfo.y;
    }
    catch (Throwable e) {
    }

    return cellInfo;
  }

  public static void setCellIndex(final RadComponent component,
                                  final int row,
                                  final int column,
                                  final boolean clearRowSpan,
                                  final boolean clearColumnSpan) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)component).getTag();
        tag.setAttribute("layout_row", SdkConstants.NS_RESOURCES, Integer.toString(row));
        tag.setAttribute("layout_column", SdkConstants.NS_RESOURCES, Integer.toString(column));
        if (clearRowSpan) {
          ModelParser.deleteAttribute(tag, "layout_rowSpan");
        }
        if (clearColumnSpan) {
          ModelParser.deleteAttribute(tag, "layout_columnSpan");
        }
      }
    });
  }

  public static int getSpan(RadComponent component, boolean row) {
    try {
      String span =
        ((RadViewComponent)component).getTag().getAttributeValue(row ? "layout_rowSpan" : "layout_columnSpan", SdkConstants.NS_RESOURCES);
      return Integer.parseInt(span);
    }
    catch (Throwable e) {
      return 1;
    }
  }

  public static void setSpan(final RadComponent component, final int span, final boolean row) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)component).getTag();
        tag.setAttribute(row ? "layout_rowSpan" : "layout_columnSpan", SdkConstants.NS_RESOURCES, Integer.toString(span));
      }
    });
  }

  public static void clearCellSpans(final RadComponent component) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)component).getTag();
        ModelParser.deleteAttribute(tag, "layout_rowSpan");
        ModelParser.deleteAttribute(tag, "layout_columnSpan");
      }
    });
  }
}