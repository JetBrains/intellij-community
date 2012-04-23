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
package com.intellij.android.designer.designSurface.layout;

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.ViewsMetaManager;
import com.intellij.android.designer.model.agrid.GridInfo;
import com.intellij.android.designer.model.agrid.GridInsertType;
import com.intellij.android.designer.model.table.RadTableLayoutComponent;
import com.intellij.android.designer.model.table.RadTableRowLayout;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.designSurface.feedbacks.InsertFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class TableLayoutOperation extends AbstractEditOperation {
  private static final int CROSS_SIZE = 10;

  private GridFeedback myFeedback;
  private InsertFeedback myInsertFeedback;
  private TextFeedback myTextFeedback;
  private Rectangle myBounds;
  private int myColumn;
  private int myRow;
  private GridInsertType myInsertType;
  private boolean myExist;

  public TableLayoutOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  private GridInfo getGridInfo() {
    return ((RadTableLayoutComponent)myContainer).getVirtualGridInfo();
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myInsertFeedback = new InsertFeedback(Color.green);
      layer.add(myInsertFeedback);

      myBounds = myContainer.getBounds(layer);

      myFeedback = new GridFeedback();
      myFeedback.setBounds(myBounds);
      layer.add(myFeedback);

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myTextFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();
    calculateGridInfo();
    configureTextFeedback();
    myFeedback.repaint();
  }

  private void configureTextFeedback() {
    myTextFeedback.clear();

    int row = myRow;
    int column = myColumn;

    myTextFeedback.append("[");

    if (myInsertType == GridInsertType.before_h_cell) {
      myTextFeedback.append("before ");
    }
    else if (myInsertType == GridInsertType.after_h_cell) {
      myTextFeedback.append("after ");
    }
    else if (myInsertType != GridInsertType.in_cell) {
      myTextFeedback.append("insert: ");

      if (myInsertType == GridInsertType.corner_top_right) {
        column++;
      }
      else if (myInsertType == GridInsertType.corner_bottom_left) {
        row++;
      }
      else if (myInsertType == GridInsertType.corner_bottom_right) {
        row++;
        column++;
      }
    }

    myTextFeedback.append("row ");
    myTextFeedback.bold(Integer.toString(row));
    myTextFeedback.append(", ");

    if (myInsertType == GridInsertType.before_v_cell) {
      myTextFeedback.append("before ");
    }
    else if (myInsertType == GridInsertType.after_v_cell) {
      myTextFeedback.append("after ");
    }

    myTextFeedback.append("column ");
    myTextFeedback.bold(Integer.toString(column));
    myTextFeedback.append("]");

    myTextFeedback.centerTop(myBounds);
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myInsertFeedback);
      layer.remove(myFeedback);
      layer.remove(myTextFeedback);
      layer.repaint();
      myFeedback = null;
      myInsertFeedback = null;
      myTextFeedback = null;
    }
  }

  private void calculateGridInfo() {
    GridInfo gridInfo = getGridInfo();
    Point location = SwingUtilities.convertPoint(myContext.getArea().getFeedbackLayer(), myContext.getLocation(), myFeedback);

    myColumn = getLineIndex(gridInfo.vLines, location.x);
    myRow = getLineIndex(gridInfo.hLines, location.y);

    if (gridInfo.components == null) {
      myInsertType = GridInsertType.in_cell;
      myExist = false;
    }
    else {
      myExist = isExist(myRow, myColumn);
      myInsertType = GridInsertType.in_cell;

      Rectangle cellRect = getInsertRect(myExist);
      Rectangle inCellRect = getInsertInRect(cellRect);
      boolean isExistCell = gridInfo.components != null && myRow < gridInfo.components.length && myColumn < gridInfo.components[0].length;

      if (!inCellRect.contains(location)) {
        if (location.x <= inCellRect.x) {
          if (location.y <= inCellRect.y) {
            if (isExistCell) {
              myInsertType = GridInsertType.corner_top_left;
              myInsertFeedback.cross(myBounds.x + cellRect.x, myBounds.y + cellRect.y, CROSS_SIZE);
            }
          }
          else if (inCellRect.y < location.y && location.y < inCellRect.getMaxY()) {
            if (myExist && (myColumn == 0 || isExist(myRow, myColumn - 1))) {
              myInsertType = GridInsertType.before_v_cell;
              myInsertFeedback.vertical(myBounds.x + cellRect.x, myBounds.y + cellRect.y, cellRect.height);
            }
          }
          else if (isExistCell) {
            myInsertType = GridInsertType.corner_bottom_left;
            myInsertFeedback.cross(myBounds.x + cellRect.x, myBounds.y + cellRect.y + cellRect.height, CROSS_SIZE);
          }
        }
        else if (location.x >= inCellRect.getMaxX()) {
          if (location.y <= inCellRect.y) {
            if (isExistCell) {
              myInsertType = GridInsertType.corner_top_right;
              myInsertFeedback.cross(myBounds.x + cellRect.x + cellRect.width, myBounds.y + cellRect.y, CROSS_SIZE);
            }
          }
          else if (inCellRect.y < location.y && location.y < inCellRect.getMaxY()) {
            if (myExist && (myColumn == gridInfo.lastColumn || isExist(myRow, myColumn + 1))) {
              myInsertType = GridInsertType.after_v_cell;
              myInsertFeedback.vertical(myBounds.x + cellRect.x + cellRect.width, myBounds.y + cellRect.y, cellRect.height);
            }
          }
          else if (isExistCell) {
            myInsertType = GridInsertType.corner_bottom_right;
            myInsertFeedback.cross(myBounds.x + cellRect.x + cellRect.width, myBounds.y + cellRect.y + cellRect.height, CROSS_SIZE);
          }
        }
        else if (location.y <= inCellRect.y) {
          if (myExist && (myRow == 0 || isExist(myRow - 1, myColumn))) {
            myInsertType = GridInsertType.before_h_cell;
            cellRect = getInsertRect(false);
            myInsertFeedback.horizontal(myBounds.x + cellRect.x, myBounds.y + cellRect.y, cellRect.width);
          }
        }
        else if (location.y >= inCellRect.getMaxY()) {
          if (myExist && (myRow == gridInfo.lastRow || isExist(myRow + 1, myColumn))) {
            myInsertType = GridInsertType.after_h_cell;
            cellRect = getInsertRect(false);
            myInsertFeedback.horizontal(myBounds.x + cellRect.x, myBounds.y + cellRect.y + cellRect.height, cellRect.width);
          }
        }
      }
    }

    if (myInsertType == GridInsertType.in_cell) {
      myInsertFeedback.setVisible(false);
    }
  }

  private static int getLineIndex(int[] line, int location) {
    for (int i = 0; i < line.length - 1; i++) {
      if (line[i] <= location && location <= line[i + 1]) {
        return i;
      }
    }
    return Math.max(0, line.length - 1);
  }

  private boolean isExist(int row, int column) {
    RadComponent[][] components = getGridInfo().components;
    if (components != null &&
        0 <= row && row < components.length &&
        0 <= column && column < components[0].length) {
      return components[row][column] != null;
    }
    return false;
  }

  private Rectangle getInsertRect(boolean includeSpan) {
    GridInfo gridInfo = getGridInfo();
    int startColumn = myColumn;
    int endColumn = myColumn + 1;

    if (includeSpan) {
      RadComponent[] columnComponents = gridInfo.components[myRow];
      RadComponent existComponent = columnComponents[startColumn];

      while (startColumn > 0) {
        if (columnComponents[startColumn - 1] == existComponent) {
          startColumn--;
        }
        else {
          break;
        }
      }

      while (endColumn < columnComponents.length) {
        if (columnComponents[endColumn] == existComponent) {
          endColumn++;
        }
        else {
          break;
        }
      }
    }

    int x1 = startColumn < gridInfo.vLines.length ? gridInfo.vLines[startColumn] : 0;
    int x2 = endColumn < gridInfo.vLines.length ? gridInfo.vLines[endColumn] : gridInfo.width;

    int y1 = myRow < gridInfo.hLines.length ? gridInfo.hLines[myRow] : 0;
    int y2 = myRow + 1 < gridInfo.hLines.length ? gridInfo.hLines[myRow + 1] : gridInfo.height;

    return new Rectangle(x1, y1, x2 - x1, y2 - y1);
  }

  private static Rectangle getInsertInRect(Rectangle cellRect) {
    int borderWidth = Math.min(cellRect.width / 3, 10);
    int borderHeight = Math.min(cellRect.height / 3, 10);
    return new Rectangle(cellRect.x + borderWidth, cellRect.y + borderHeight, cellRect.width - 2 * borderWidth,
                         cellRect.height - 2 * borderHeight);
  }

  @Override
  public boolean canExecute() {
    return myComponents.size() == 1 && (myInsertType != GridInsertType.in_cell || !myExist);
  }

  @Override
  public void execute() throws Exception {
    GridInfo gridInfo = getGridInfo();

    RadViewComponent container = (RadViewComponent)myContainer;
    List<RadComponent> rows = myContainer.getChildren();
    RadComponent editComponent = myComponents.get(0);

    MetaManager metaManager = ViewsMetaManager.getInstance(container.getTag().getProject());
    MetaModel tableRowModel = metaManager.getModelByTag("TableRow");

    if (myInsertType == GridInsertType.in_cell) {
      if (gridInfo.components != null && myRow < gridInfo.components.length) {
        RadViewComponent rowComponent = (RadViewComponent)rows.get(myRow);
        if (RadTableRowLayout.is(rowComponent)) {
          insertInRow(rowComponent, null, true, myColumn + 1, myColumn);
        }
        else {
          convertToTableRowAndExecute(rowComponent, false, tableRowModel, myColumn);
        }
      }
      else {
        RadViewComponent newRowComponent = null;
        for (int i = rows.size(); i <= myRow; i++) {
          newRowComponent = ModelParser.createComponent(null, tableRowModel);
          ModelParser.addComponent(container, newRowComponent, null);
        }

        execute(myContext, newRowComponent, myComponents, null);
        RadTableLayoutComponent.setCellIndex(editComponent, myColumn);
      }
    }
    else if (myInsertType == GridInsertType.before_h_cell || myInsertType == GridInsertType.after_h_cell) {
      insertInNewRow(tableRowModel, myInsertType == GridInsertType.before_h_cell, myRow, myColumn);
    }
    else if (myInsertType == GridInsertType.before_v_cell || myInsertType == GridInsertType.after_v_cell) {
      int column = myColumn;
      if (myInsertType == GridInsertType.after_v_cell) {
        column++;
      }

      shiftColumns(column);

      RadViewComponent rowComponent = (RadViewComponent)rows.get(myRow);
      if (RadTableRowLayout.is(rowComponent)) {
        insertInRow(rowComponent,
                    myInsertType == GridInsertType.before_v_cell ? gridInfo.components[myRow][column] : null,
                    myInsertType == GridInsertType.after_v_cell,
                    column, column);
      }
      else {
        convertToTableRowAndExecute(rowComponent,
                                    myInsertType == GridInsertType.before_v_cell,
                                    tableRowModel,
                                    column);
      }
    }
    else {
      int column = myColumn;
      if (myInsertType == GridInsertType.corner_top_right || myInsertType == GridInsertType.corner_bottom_right) {
        column++;
      }

      shiftColumns(column);

      insertInNewRow(tableRowModel,
                     myInsertType == GridInsertType.corner_top_left || myInsertType == GridInsertType.corner_top_right,
                     myRow, column);
    }
  }

  private void insertInRow(RadViewComponent rowComponent,
                           @Nullable RadComponent insertBefore,
                           boolean calculateInsert,
                           int startColumn,
                           int column) throws Exception {
    if (calculateInsert) {
      GridInfo gridInfo = getGridInfo();
      RadComponent[] rowComponents = gridInfo.components[myRow];

      for (int i = startColumn; i < rowComponents.length; i++) {
        insertBefore = rowComponents[i];
        if (insertBefore != null) {
          break;
        }
      }
    }

    RadComponent editComponent = myComponents.get(0);
    if (editComponent != insertBefore) {
      execute(myContext, rowComponent, myComponents, (RadViewComponent)insertBefore);
    }

    RadTableLayoutComponent.setCellIndex(editComponent, column);
  }

  private void insertInNewRow(MetaModel tableRowModel, boolean before, int row, int column) throws Exception {
    List<RadComponent> rows = myContainer.getChildren();
    RadComponent insertBefore = null;

    if (before) {
      insertBefore = rows.get(row);
    }
    else if (row + 1 < rows.size()) {
      insertBefore = rows.get(row + 1);
    }

    RadViewComponent newRowComponent = ModelParser.createComponent(null, tableRowModel);
    ModelParser.addComponent((RadViewComponent)myContainer, newRowComponent, (RadViewComponent)insertBefore);

    execute(myContext, newRowComponent, myComponents, null);
    RadTableLayoutComponent.setCellIndex(myComponents.get(0), column);
  }

  private void convertToTableRowAndExecute(RadViewComponent rowComponent,
                                           boolean insertBefore,
                                           MetaModel tableRowModel,
                                           int column)
    throws Exception {
    RadViewComponent newRowComponent = ModelParser.createComponent(null, tableRowModel);
    ModelParser.addComponent((RadViewComponent)myContainer, newRowComponent, rowComponent);
    ModelParser.moveComponent(newRowComponent, rowComponent, null);

    RadComponent editComponent = myComponents.get(0);
    if (!insertBefore || editComponent != rowComponent) {
      execute(myContext, newRowComponent, myComponents, insertBefore ? rowComponent : null);
    }

    if (column > 1) {
      RadTableLayoutComponent.setCellIndex(editComponent, column);
    }
  }

  private void shiftColumns(int startColumn) {
    List<RadComponent> rows = myContainer.getChildren();
    RadComponent[][] components = getGridInfo().components;

    for (int i = 0; i < components.length; i++) {
      if (RadTableRowLayout.is(rows.get(i))) {
        RadComponent[] rowComponents = components[i];

        for (int j = startColumn; j < rowComponents.length; j++) {
          RadComponent cellComponent = rowComponents[j];

          if (cellComponent != null) {
            RadTableLayoutComponent.setCellIndex(cellComponent, j + 1);
          }
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Feedback
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private class GridFeedback extends JComponent {
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.setColor(BorderStaticDecorator.COLOR);

      GridInfo gridInfo = getGridInfo();

      for (int x : gridInfo.vLines) {
        g.drawLine(x, 0, x, gridInfo.height);
      }
      for (int y : gridInfo.hLines) {
        g.drawLine(0, y, gridInfo.width, y);
      }
      g.drawRect(0, 0, gridInfo.width - 1, gridInfo.height - 1);
      g.drawRect(1, 1, gridInfo.width - 3, gridInfo.height - 3);

      if (myInsertType == GridInsertType.in_cell) {
        Rectangle cellRect = getInsertRect(myExist);

        if (myExist) {
          AlphaFeedback.fillRect2(g, cellRect.x, cellRect.y, cellRect.width, cellRect.height, Color.pink);
        }
        else {
          AlphaFeedback.fillRect1(g, cellRect.x, cellRect.y, cellRect.width, cellRect.height, Color.green);
        }
      }
    }
  }
}