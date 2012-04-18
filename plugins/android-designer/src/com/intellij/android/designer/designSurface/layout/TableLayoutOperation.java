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
import com.intellij.android.designer.model.table.GridInfo;
import com.intellij.android.designer.model.table.GridInsertType;
import com.intellij.android.designer.model.table.RadTableLayoutComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.designSurface.feedbacks.InsertFeedback;
import com.intellij.designer.model.RadComponent;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TableLayoutOperation extends AbstractEditOperation {
  private static final int CROSS_SIZE = 10;

  private GridFeedback myFeedback;
  private InsertFeedback myInsertFeedback;
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

  @Override
  public void showFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myFeedback = new GridFeedback();
      myFeedback.setBounds(myContainer.getBounds(layer));

      myInsertFeedback = new InsertFeedback(Color.green);

      layer.add(myInsertFeedback);
      layer.add(myFeedback);
      layer.repaint();
    }

    calculateGridInfo();
    myFeedback.repaint();
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myInsertFeedback);
      layer.remove(myFeedback);
      layer.repaint();
      myFeedback = null;
      myInsertFeedback = null;
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

      Rectangle bounds = myContainer.getBounds(myContext.getArea().getFeedbackLayer());
      Rectangle cellRect = getInsertRect(myExist);
      Rectangle inCellRect = getInsertInRect(cellRect);
      boolean isExistCell = myRow < gridInfo.components.length && myColumn < gridInfo.components[0].length;

      if (!inCellRect.contains(location)) {
        if (location.x <= inCellRect.x) {
          if (location.y <= inCellRect.y) {
            if (isExistCell) {
              myInsertType = GridInsertType.corner_top_left;
              myInsertFeedback.cross(bounds.x + cellRect.x, bounds.y + cellRect.y, CROSS_SIZE);
            }
          }
          else if (inCellRect.y < location.y && location.y < inCellRect.getMaxY()) {
            if (myExist && (myColumn == 0 || isExist(myRow, myColumn - 1))) {
              myInsertType = GridInsertType.before_v_cell;
              myInsertFeedback.vertical(bounds.x + cellRect.x, bounds.y + cellRect.y, cellRect.height);
            }
          }
          else if (isExistCell) {
            myInsertType = GridInsertType.corner_bottom_left;
            myInsertFeedback.cross(bounds.x + cellRect.x, bounds.y + cellRect.y + cellRect.height, CROSS_SIZE);
          }
        }
        else if (location.x >= inCellRect.getMaxX()) {
          if (location.y <= inCellRect.y) {
            if (isExistCell) {
              myInsertType = GridInsertType.corner_top_right;
              myInsertFeedback.cross(bounds.x + cellRect.x + cellRect.width, bounds.y + cellRect.y, CROSS_SIZE);
            }
          }
          else if (inCellRect.y < location.y && location.y < inCellRect.getMaxY()) {
            if (myExist && (myColumn == gridInfo.lastColumn || isExist(myRow, myColumn + 1))) {
              myInsertType = GridInsertType.after_v_cell;
              myInsertFeedback.vertical(bounds.x + cellRect.x + cellRect.width, bounds.y + cellRect.y, cellRect.height);
            }
          }
          else if (isExistCell) {
            myInsertType = GridInsertType.corner_bottom_right;
            myInsertFeedback.cross(bounds.x + cellRect.x + cellRect.width, bounds.y + cellRect.y + cellRect.height, CROSS_SIZE);
          }
        }
        else if (location.y <= inCellRect.y) {
          if (myExist && (myRow == 0 || isExist(myRow - 1, myColumn))) {
            myInsertType = GridInsertType.before_h_cell;
            myInsertFeedback.horizontal(bounds.x + cellRect.x, bounds.y + cellRect.y, cellRect.width);
          }
        }
        else if (location.y >= inCellRect.getMaxY()) {
          if (myExist && (myRow == gridInfo.lastRow || isExist(myRow + 1, myColumn))) {
            myInsertType = GridInsertType.after_h_cell;
            myInsertFeedback.horizontal(bounds.x + cellRect.x, bounds.y + cellRect.y + cellRect.height, cellRect.width);
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
    return myInsertType != GridInsertType.in_cell || !myExist;
  }

  @Override
  public void execute() throws Exception {
    super.execute(); // TODO: Auto-generated method stub
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