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

import com.android.SdkConstants;
import com.intellij.android.designer.designSurface.layout.grid.GridSelectionDecorator;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.IGridProvider;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class LayoutSpanOperation implements EditOperation {
  public static final String TYPE = "layout_span";

  protected static final Color COLOR = Color.green.darker();

  protected final OperationContext myContext;
  private final GridSelectionDecorator myDecorator;
  protected RadViewComponent myComponent;
  private RectangleFeedback myFeedback;
  private TextFeedback myTextFeedback;
  private ErrorFeedback myErrorFeedback;
  private Rectangle myBounds;
  private Rectangle myContainerBounds;
  private boolean myShowErrorFeedback;
  protected int mySpan;
  private int[] mySpans;
  private int[] myOffsets;
  private int[] myCells;
  private int myIndex = -1;

  public LayoutSpanOperation(OperationContext context, GridSelectionDecorator decorator) {
    myContext = context;
    myDecorator = decorator;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    myBounds = myDecorator.getCellBounds(myContext.getArea().getFeedbackLayer(), myComponent);
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  protected void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(new LineMarginBorder(0, 5, 3, 0));
      layer.add(myTextFeedback);

      myFeedback = new RectangleFeedback(COLOR, 2);
      layer.add(myFeedback);

      myErrorFeedback = new ErrorFeedback();
      layer.add(myErrorFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    int direction = myContext.getResizeDirection();
    if (direction == Position.WEST || direction == Position.EAST) {
      handleColumns(direction == Position.EAST);
    }
    else {
      handleRows(direction == Position.SOUTH);
    }
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myTextFeedback);
      layer.remove(myFeedback);
      layer.remove(myErrorFeedback);
      layer.repaint();
      myTextFeedback = null;
      myFeedback = null;
      myErrorFeedback = null;
    }
  }

  protected abstract String getColumnAttribute(boolean asName);

  protected abstract String getColumnSpanAttribute(boolean asName);

  protected abstract String getRowAttribute(boolean asName);

  protected abstract String getRowSpanAttribute(boolean asName);

  private void handleRows(boolean bottom) {
    calculateRows(bottom);

    Rectangle bounds = myContext.getTransformedRectangle(myBounds);
    int location = bottom ? bounds.y + bounds.height : bounds.y;

    if (location < myOffsets[0]) {
      myIndex = 0;
      myErrorFeedback.setVisible(!bottom && myShowErrorFeedback);
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
        myErrorFeedback.setVisible(bottom && myShowErrorFeedback);
      }
      else {
        myErrorFeedback.setVisible(false);
      }
    }

    if (bottom) {
      myFeedback.setBounds(myBounds.x, myBounds.y, myBounds.width, myOffsets[myIndex] - myBounds.y);
    }
    else {
      myFeedback.setBounds(myBounds.x, myOffsets[myIndex], myBounds.width, myBounds.y + myBounds.height - myOffsets[myIndex]);
    }

    myTextFeedback.clear();

    if (!bottom) {
      myTextFeedback.append(getRowAttribute(true));
      myTextFeedback.append(" ");
      myTextFeedback.append(Integer.toString(myCells[myIndex]));
      myTextFeedback.append(", ");
    }

    myTextFeedback.append(getRowSpanAttribute(true));
    myTextFeedback.append(" ");
    myTextFeedback.append(Integer.toString(mySpans[myIndex]));
    myTextFeedback.centerTop(myContainerBounds);
  }

  private void handleColumns(boolean right) {
    calculateColumns(right);

    Rectangle bounds = myContext.getTransformedRectangle(myBounds);
    int location = right ? bounds.x + bounds.width : bounds.x;

    if (location < myOffsets[0]) {
      myIndex = 0;
      myErrorFeedback.setVisible(!right && myShowErrorFeedback);
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
        myErrorFeedback.setVisible(right && myShowErrorFeedback);
      }
      else {
        myErrorFeedback.setVisible(false);
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
      myTextFeedback.append(getColumnAttribute(true));
      myTextFeedback.append(" ");
      myTextFeedback.append(Integer.toString(myCells[myIndex]));
      myTextFeedback.append(", ");
    }

    myTextFeedback.append(getColumnSpanAttribute(true));
    myTextFeedback.append(" ");
    myTextFeedback.append(Integer.toString(mySpans[myIndex]));
    myTextFeedback.centerTop(myContainerBounds);
  }

  protected RadComponent getContainer() {
    return myComponent.getParent();
  }

  protected abstract Point getCellInfo();

  private void calculateRows(boolean bottom) {
    if (mySpans != null) {
      return;
    }

    RadComponent container = getContainer();
    GridInfo gridInfo = ((IGridProvider)container).getVirtualGridInfo();
    RadComponent[][] components = gridInfo.components;
    Point cellInfo = getCellInfo();
    int row = cellInfo.y;

    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    myContainerBounds = container.getBounds(layer);

    IntArrayList spans = new IntArrayList();
    IntArrayList offsets = new IntArrayList();

    if (bottom) {
      int span = 1;

      for (int i = row; i < row + mySpan; i++) {
        spans.add(span++);
        offsets.add(myContainerBounds.y + gridInfo.hLines[i + 1]);
      }

      for (int i = row + mySpan; i < components.length; i++) {
        if (components[i][cellInfo.x] == null) {
          spans.add(span++);
          offsets.add(myContainerBounds.y + gridInfo.hLines[i + 1]);
        }
        else {
          myErrorFeedback.setBounds(myDecorator.getCellBounds(layer, components[i][cellInfo.x]));
          myShowErrorFeedback = true;
          break;
        }
      }
    }
    else {
      IntArrayList columns = new IntArrayList();
      int span = mySpan;

      for (int i = row; i < row + mySpan; i++) {
        spans.add(span--);
        offsets.add(myContainerBounds.y + gridInfo.hLines[i]);
        columns.add(i);
      }

      span = mySpan;

      for (int i = row - 1; i >= 0; i--) {
        if (components[i][cellInfo.x] == null) {
          spans.add(0, ++span);
          offsets.add(0, myContainerBounds.y + gridInfo.hLines[i]);
          columns.add(0, i);
        }
        else {
          myErrorFeedback.setBounds(myDecorator.getCellBounds(layer, components[i][cellInfo.x]));
          myShowErrorFeedback = true;
          break;
        }
      }

      myCells = columns.toArray();
    }

    mySpans = spans.toArray();
    myOffsets = offsets.toArray();
  }

  private void calculateColumns(boolean right) {
    if (mySpans != null) {
      return;
    }

    RadComponent container = getContainer();
    GridInfo gridInfo = ((IGridProvider)container).getVirtualGridInfo();
    Point cellInfo = getCellInfo();
    RadComponent[] rowComponents = gridInfo.components[cellInfo.y];
    int column = cellInfo.x;

    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    myContainerBounds = container.getBounds(layer);

    IntArrayList spans = new IntArrayList();
    IntArrayList offsets = new IntArrayList();

    if (right) {
      int span = 1;

      for (int i = column; i < column + mySpan; i++) {
        spans.add(span++);
        offsets.add(myContainerBounds.x + gridInfo.vLines[i + 1]);
      }

      for (int i = column + mySpan; i < rowComponents.length; i++) {
        if (rowComponents[i] == null) {
          spans.add(span++);
          offsets.add(myContainerBounds.x + gridInfo.vLines[i + 1]);
        }
        else {
          myErrorFeedback.setBounds(myDecorator.getCellBounds(layer, rowComponents[i]));
          myShowErrorFeedback = true;
          break;
        }
      }
    }
    else {
      IntArrayList columns = new IntArrayList();
      int span = mySpan;

      for (int i = column; i < column + mySpan; i++) {
        spans.add(span--);
        offsets.add(myContainerBounds.x + gridInfo.vLines[i]);
        columns.add(i);
      }

      span = mySpan;

      for (int i = column - 1; i >= 0; i--) {
        if (rowComponents[i] == null) {
          spans.add(0, ++span);
          offsets.add(0, myContainerBounds.x + gridInfo.vLines[i]);
          columns.add(0, i);
        }
        else {
          myErrorFeedback.setBounds(myDecorator.getCellBounds(layer, rowComponents[i]));
          myShowErrorFeedback = true;
          break;
        }
      }

      myCells = columns.toArray();
    }

    mySpans = spans.toArray();
    myOffsets = offsets.toArray();
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
        int direction = myContext.getResizeDirection();
        if (direction == Position.WEST || direction == Position.EAST) {
          execute(getColumnAttribute(false), getColumnSpanAttribute(false), direction == Position.WEST);
        }
        else {
          execute(getRowAttribute(false), getRowSpanAttribute(false), direction == Position.NORTH);
        }
      }
    });
  }

  private void execute(String cell, String span, boolean cellFix) {
    XmlTag tag = myComponent.getTag();

    if (cellFix) {
      tag.setAttribute(cell, SdkConstants.NS_RESOURCES, Integer.toString(myCells[myIndex]));
    }

    int spanValue = mySpans[myIndex];
    if (spanValue == 1) {
      ModelParser.deleteAttribute(tag, span);
    }
    else {
      tag.setAttribute(span, SdkConstants.NS_RESOURCES, Integer.toString(spanValue));
    }
  }

  private static class ErrorFeedback extends AlphaFeedback {
    public ErrorFeedback() {
      super(Color.pink);
    }

    @Override
    protected void paintOther1(Graphics2D g2d) {
    }

    @Override
    protected void paintOther2(Graphics2D g2d) {
      g2d.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  protected static class SpanPoint extends DirectionResizePoint {
    private final GridSelectionDecorator myDecorator;

    public SpanPoint(Color color,
                     Color border,
                     int direction,
                     Object type,
                     @Nullable String description,
                     GridSelectionDecorator decorator) {
      super(color, border, direction, type, description);
      myDecorator = decorator;
    }

    @Override
    protected Rectangle getBounds(DecorationLayer layer, RadComponent component) {
      return myDecorator.getCellBounds(layer, component);
    }
  }
}