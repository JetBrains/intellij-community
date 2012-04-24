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
package com.intellij.android.designer.designSurface.layout.actions;

import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.layout.table.RadTableLayoutComponent;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.IntArrayList;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class LayoutSpanOperation implements EditOperation {
  public static final String TYPE = "layout_span";

  private static final Color COLOR = Color.green.darker();

  private final OperationContext myContext;
  private RadViewComponent myComponent;
  private RectangleFeedback myFeedback;
  private TextFeedback myTextFeedback;
  private Rectangle myBounds;
  private Rectangle myTableBounds;
  private int mySpan;
  private int[] mySpans;
  private int[] myOffsets;
  private int[] myColumns;
  private int myIndex = -1;

  public LayoutSpanOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    myBounds = myComponent.getBounds(myContext.getArea().getFeedbackLayer());
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(new LineMarginBorder(0, 5, 3, 0));
      layer.add(myTextFeedback);

      myFeedback = new RectangleFeedback(COLOR, 2);
      layer.add(myFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    Rectangle bounds = myContext.getTransformedRectangle(myBounds);
    boolean right = myContext.getResizeDirection() == Position.EAST;

    calculateColumns(right);

    int location = right ? bounds.x + bounds.width : bounds.x;

    if (location < myOffsets[0]) {
      myIndex = 0;
    }
    else {
      myIndex = -1;

      for (int i = 0; i < myOffsets.length - 1; i++) {
        if (myOffsets[i] <= location && location <= myOffsets[i + 1]) {
          int delta1 = location - myOffsets[i];
          int delta2 = myOffsets[i + 1] - location;
          myIndex = delta2 >= delta1 ? i : i + 1;
          break;
        }
      }

      if (myIndex == -1) {
        myIndex = myOffsets.length - 1;
      }
    }

    if (right) {
      myFeedback.setBounds(myBounds.x, myBounds.y, myOffsets[myIndex] - myBounds.x, myBounds.height);
    }
    else {
      myFeedback.setBounds(myOffsets[myIndex], myBounds.y, myBounds.x + myBounds.width - myOffsets[myIndex], myBounds.height);
    }

    myTextFeedback.clear();

    if (!right) {
      myTextFeedback.append("layout:column ");
      myTextFeedback.append(Integer.toString(myColumns[myIndex]));
      myTextFeedback.append(", ");
    }

    myTextFeedback.append("layout:span ");
    myTextFeedback.append(Integer.toString(mySpans[myIndex]));
    myTextFeedback.centerTop(myTableBounds);
  }

  private void calculateColumns(boolean right) {
    if (mySpans != null) {
      return;
    }

    RadTableLayoutComponent tableComponent = (RadTableLayoutComponent)myComponent.getParent().getParent();
    GridInfo gridInfo = tableComponent.getVirtualGridInfo();
    RadComponent[] rowComponents = gridInfo.components[tableComponent.getChildren().indexOf(myComponent.getParent())];
    int column = ArrayUtil.indexOf(rowComponents, myComponent);

    mySpan = RadTableLayoutComponent.getCellSpan(myComponent);
    myTableBounds = tableComponent.getBounds(myContext.getArea().getFeedbackLayer());

    IntArrayList spans = new IntArrayList();
    IntArrayList offsets = new IntArrayList();

    if (right) {
      int span = 1;

      for (int i = column; i < column + mySpan; i++) {
        spans.add(span++);
        offsets.add(myTableBounds.x + gridInfo.vLines[i + 1]);
      }

      for (int i = column + mySpan; i < rowComponents.length; i++) {
        if (rowComponents[i] == null) {
          spans.add(span++);
          offsets.add(myTableBounds.x + gridInfo.vLines[i + 1]);
        }
        else {
          break;
        }
      }
    }
    else {
      IntArrayList columns = new IntArrayList();
      int span = mySpan;

      for (int i = column; i < column + mySpan; i++) {
        spans.add(span--);
        offsets.add(myTableBounds.x + gridInfo.vLines[i]);
        columns.add(i);
      }

      span = mySpan;

      for (int i = column - 1; i >= 0; i--) {
        if (rowComponents[i] == null) {
          spans.add(0, ++span);
          offsets.add(0, myTableBounds.x + gridInfo.vLines[i]);
          columns.add(0, i);
        }
        else {
          break;
        }
      }

      myColumns = columns.toArray();
    }

    mySpans = spans.toArray();
    myOffsets = offsets.toArray();
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myTextFeedback);
      layer.remove(myFeedback);
      layer.repaint();
      myTextFeedback = null;
      myFeedback = null;
    }
  }

  @Override
  public boolean canExecute() {
    return true;
  }

  @Override
  public void execute() throws Exception {
    if (mySpans[myIndex] == mySpan) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = myComponent.getTag();

        if (myContext.getResizeDirection() == Position.WEST) {
          tag.setAttribute("android:layout_column", Integer.toString(myColumns[myIndex]));
        }

        int span = mySpans[myIndex];
        if (span == 1) {
          ModelParser.deleteAttribute(tag, "android:layout_span");
        }
        else {
          tag.setAttribute("android:layout_span", Integer.toString(span));
        }
      }
    });
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void tablePoints(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(COLOR,
                                                Color.black,
                                                Position.WEST,
                                                TYPE,
                                                "Change layout:column x layout:span")); // left

    decorator.addPoint(new DirectionResizePoint(COLOR,
                                                Color.black,
                                                Position.EAST,
                                                TYPE,
                                                "Change layout:span")); // right
  }

  public static void gridPoints(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(COLOR,
                                                Color.black,
                                                Position.WEST,
                                                TYPE,
                                                "Change layout:column x layout:columnSpan")); // left

    decorator.addPoint(new DirectionResizePoint(COLOR,
                                                Color.black,
                                                Position.EAST,
                                                TYPE,
                                                "Change layout:columnSpan").move(1, 0.25)); // right

    decorator.addPoint(new DirectionResizePoint(COLOR,
                                                Color.black,
                                                Position.NORTH,
                                                TYPE,
                                                "Change layout:row x layout:rowSpan")); // top

    decorator.addPoint(new DirectionResizePoint(COLOR,
                                                Color.black,
                                                Position.SOUTH,
                                                TYPE,
                                                "Change layout:rowSpan").move(0.25, 1)); // bottom
  }
}