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
import com.intellij.designer.designSurface.feedbacks.LineInsertFeedback;
import com.intellij.designer.model.RadComponent;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TableLayoutOperation extends AbstractEditOperation {
  private GridFeedback myFeedback;
  private LineInsertFeedback myHInsertFeedback;
  private LineInsertFeedback myVInsertFeedback;
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

      myHInsertFeedback = new LineInsertFeedback(Color.green, true);
      myVInsertFeedback = new LineInsertFeedback(Color.green, false);

      layer.add(myFeedback);
      layer.add(myHInsertFeedback);
      layer.add(myVInsertFeedback);
      layer.repaint();
    }

    calculateGridInfo();
    myFeedback.repaint();
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      layer.add(myHInsertFeedback);
      layer.add(myVInsertFeedback);
      layer.repaint();
      myFeedback = null;
      myHInsertFeedback = null;
      myVInsertFeedback = null;
    }
  }

  private void calculateGridInfo() {
    GridInfo gridInfo = getGridInfo();
    Point location = SwingUtilities.convertPoint(myContext.getArea().getFeedbackLayer(), myContext.getLocation(), myFeedback);

    myColumn = getLineIndex(gridInfo.vLines, location.x);
    myRow = getLineIndex(gridInfo.hLines, location.y);

    RadComponent[][] components = gridInfo.components;
    if (components == null) {
      myInsertType = GridInsertType.in_cell;
      myExist = false;
    }
    else {
      myInsertType = null;

      Rectangle bounds = myContainer.getBounds(myContext.getArea().getFeedbackLayer());
      Rectangle cellRect = getInsertRect(myExist);
      Rectangle inCellRect = getInsertInRect(cellRect);

      if (inCellRect.contains(location)) {
        myInsertType = GridInsertType.in_cell;

        myHInsertFeedback.setVisible(false);
        myVInsertFeedback.setVisible(false);
      }
      else if (location.x < inCellRect.x) {
        myInsertType = GridInsertType.before_v_cell;

        myHInsertFeedback.setVisible(false);

        myVInsertFeedback.setLocation(bounds.x + cellRect.x, bounds.y + cellRect.y);
        myVInsertFeedback.setSize(0, cellRect.height);
        myVInsertFeedback.setVisible(true);
      }
      else if (location.x > inCellRect.x) {
        myInsertType = GridInsertType.after_v_cell;

        myHInsertFeedback.setVisible(false);

        myVInsertFeedback.setLocation(bounds.x + cellRect.x + cellRect.width, bounds.y + cellRect.y);
        myVInsertFeedback.setSize(0, cellRect.height);
        myVInsertFeedback.setVisible(true);
      }
      else if (location.y < inCellRect.y) {
        myInsertType = GridInsertType.before_h_cell;

        myHInsertFeedback.setVisible(true);
        myHInsertFeedback.setLocation(bounds.x + cellRect.x, bounds.y + cellRect.y);
        myHInsertFeedback.setSize(cellRect.width, 0);

        myVInsertFeedback.setVisible(false);
      }
      else if (location.y > inCellRect.y) {
        myInsertType = GridInsertType.after_h_cell;

        myHInsertFeedback.setVisible(true);
        myHInsertFeedback.setLocation(bounds.x + cellRect.x, bounds.y + cellRect.y + cellRect.height);
        myHInsertFeedback.setSize(cellRect.width, 0);

        myVInsertFeedback.setVisible(false);
      }

      if (myInsertType == GridInsertType.in_cell) {
        if (myRow < components.length && myColumn < components[0].length) {
          myExist = components[myRow][myColumn] != null;
        }
        else {
          myExist = false;
        }
      }
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
    int borderWidth = getInsertBorderLength(cellRect.width);
    int borderHeight = getInsertBorderLength(cellRect.height);
    return new Rectangle(cellRect.x + borderWidth, cellRect.y + borderHeight, cellRect.width - 2 * borderWidth,
                         cellRect.height - 2 * borderHeight);
  }

  private static int getInsertBorderLength(int length) {
    int border = 5;
    if (length < 3 * border) {
      border = length / 3;
    }
    return border;
  }

  @Override
  public boolean canExecute() {
    return !myExist;
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