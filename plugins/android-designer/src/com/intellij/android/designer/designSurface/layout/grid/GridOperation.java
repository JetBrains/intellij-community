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
package com.intellij.android.designer.designSurface.layout.grid;

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.designSurface.layout.BorderStaticDecorator;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.GridInsertType;
import com.intellij.android.designer.model.grid.IGridProvider;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.designSurface.feedbacks.InsertFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class GridOperation extends AbstractEditOperation {
  private GridFeedback myFeedback;
  private InsertFeedback myInsertFeedback;
  private TextFeedback myTextFeedback;
  private Rectangle myBounds;
  protected int myColumn;
  protected int myRow;
  protected GridInsertType myInsertType;
  protected boolean myExist;

  public GridOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  protected final GridInfo getGridInfo() {
    return ((IGridProvider)myContainer).getVirtualGridInfo();
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myInsertFeedback = new InsertFeedback(JBColor.GREEN);
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

  @Override
  public boolean canExecute() {
    return myComponents.size() == 1 && (myInsertType != GridInsertType.in_cell || !myExist);
  }

  @Override
  public abstract void execute() throws Exception;

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Grid
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static final int CROSS_SIZE = 10;

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
              boolean insert = true;

              if (isMoveOperation()) {
                if (myColumn != 0 || getMovedIndex(false) == 0) {
                  insert = !isSingleMovedAxis(false);
                }
              }

              if (insert) {
                myInsertType = GridInsertType.before_v_cell;
                cellRect = getInsertRect(false);
                myInsertFeedback.vertical(myBounds.x + cellRect.x, myBounds.y + cellRect.y, cellRect.height);
              }
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
            if (myExist && (myColumn == gridInfo.lastInsertColumn || isExist(myRow, myColumn + 1))) {
              if (!isMoveOperation() || !isSingleMovedAxis(false)) {
                myInsertType = GridInsertType.after_v_cell;
                cellRect = getInsertRect(false);
                myInsertFeedback.vertical(myBounds.x + cellRect.x + cellRect.width, myBounds.y + cellRect.y, cellRect.height);
              }
            }
          }
          else if (isExistCell) {
            myInsertType = GridInsertType.corner_bottom_right;
            myInsertFeedback.cross(myBounds.x + cellRect.x + cellRect.width, myBounds.y + cellRect.y + cellRect.height, CROSS_SIZE);
          }
        }
        else if (location.y <= inCellRect.y) {
          if (myExist && (myRow == 0 || isExist(myRow - 1, myColumn))) {
            boolean insert = true;

            if (isMoveOperation()) {
              if (myRow != 0 || getMovedIndex(true) == 0) {
                insert = !isSingleMovedAxis(true);
              }
            }

            if (insert) {
              myInsertType = GridInsertType.before_h_cell;
              cellRect = getInsertRect(false);
              myInsertFeedback.horizontal(myBounds.x + cellRect.x, myBounds.y + cellRect.y, cellRect.width);
            }
          }
        }
        else if (location.y >= inCellRect.getMaxY()) {
          if (myExist && (myRow == gridInfo.lastInsertRow || isExist(myRow + 1, myColumn))) {
            if (!isMoveOperation() || !isSingleMovedAxis(true)) {
              myInsertType = GridInsertType.after_h_cell;
              cellRect = getInsertRect(false);
              myInsertFeedback.horizontal(myBounds.x + cellRect.x, myBounds.y + cellRect.y + cellRect.height, cellRect.width);
            }
          }
        }
      }
    }

    if (myInsertType == GridInsertType.in_cell) {
      myInsertFeedback.setVisible(false);
    }
  }

  protected boolean isMoveOperation() {
    return myContext.isMove();
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

  private Rectangle getInsertRect(boolean includeSpans) {
    GridInfo gridInfo = getGridInfo();
    int startColumn = myColumn;
    int endColumn = myColumn + 1;
    int startRow = myRow;
    int endRow = myRow + 1;

    if (includeSpans) {
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

      while (startRow > 0) {
        if (gridInfo.components[startRow - 1][startColumn] == existComponent) {
          startRow--;
        }
        else {
          break;
        }
      }
      while (endRow < gridInfo.components.length) {
        if (gridInfo.components[endRow][startColumn] == existComponent) {
          endRow++;
        }
        else {
          break;
        }
      }
    }

    int x1 = startColumn < gridInfo.vLines.length ? gridInfo.vLines[startColumn] : 0;
    int x2 = endColumn < gridInfo.vLines.length ? gridInfo.vLines[endColumn] : gridInfo.width;

    int y1 = startRow < gridInfo.hLines.length ? gridInfo.hLines[startRow] : 0;
    int y2 = endRow < gridInfo.hLines.length ? gridInfo.hLines[endRow] : gridInfo.height;

    return new Rectangle(x1, y1, x2 - x1, y2 - y1);
  }

  private static Rectangle getInsertInRect(Rectangle cellRect) {
    int borderWidth = Math.min(cellRect.width / 3, 10);
    int borderHeight = Math.min(cellRect.height / 3, 10);
    return new Rectangle(cellRect.x + borderWidth, cellRect.y + borderHeight, cellRect.width - 2 * borderWidth,
                         cellRect.height - 2 * borderHeight);
  }

  protected abstract int getMovedIndex(boolean row);

  protected abstract boolean isSingleMovedAxis(boolean row);

  protected final int getSizeInRow(int rowIndex, RadComponent excludeComponent) {
    int size = 0;
    RadComponent[][] components = getGridInfo().components;

    if (rowIndex < components.length) {
      RadComponent[] rowComponents = components[rowIndex];

      for (int j = 0; j < rowComponents.length; j++) {
        RadComponent cellComponent = rowComponents[j];
        if (cellComponent != null) {
          if (cellComponent != excludeComponent) {
            size++;
          }

          while (j + 1 < rowComponents.length && cellComponent == rowComponents[j + 1]) {
            j++;
          }
        }
      }
    }

    return size;
  }

  protected final int getSizeInColumn(int columnIndex, int columnCount, RadComponent excludeComponent) {
    int size = 0;
    RadComponent[][] components = getGridInfo().components;

    if (columnIndex < columnCount) {
      for (int j = 0; j < components.length; j++) {
        RadComponent cellComponent = components[j][columnIndex];

        if (cellComponent != null) {
          if (cellComponent != excludeComponent) {
            size++;
          }

          while (j + 1 < components.length && cellComponent == components[j + 1][columnIndex]) {
            j++;
          }
        }
      }
    }

    return size;
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