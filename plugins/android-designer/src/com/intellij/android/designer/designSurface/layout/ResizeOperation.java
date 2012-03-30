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

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.RectangleComponent;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.ui.LightColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeOperation implements EditOperation {
  public static final String TYPE = "resize_children";

  public static ComponentDecorator full() {
    return new ResizeSelectionDecorator(Color.red, 1,
                                        new DirectionResizePoint(Position.NORTH, TYPE),
                                        new DirectionResizePoint(Position.EAST, TYPE),
                                        new DirectionResizePoint(Position.WEST, TYPE),
                                        new DirectionResizePoint(Position.SOUTH, TYPE),
                                        new DirectionResizePoint(Position.NORTH_EAST, TYPE),
                                        new DirectionResizePoint(Position.NORTH_WEST, TYPE),
                                        new DirectionResizePoint(Position.SOUTH_EAST, TYPE),
                                        new DirectionResizePoint(Position.SOUTH_WEST, TYPE));
  }

  private final OperationContext myContext;
  private RadViewComponent myComponent;

  private RectangleComponent myFeedback;
  private JLabel myTextFeedback;
  private RectangleComponent myWrapFeedback;

  private String myStaticWidth;
  private String myStaticHeight;

  private final Rectangle myWrapBounds = new Rectangle();

  public ResizeOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    int direction = myContext.getResizeDirection();

    String width = myComponent.getTag().getAttributeValue("android:layout_width");
    String height = myComponent.getTag().getAttributeValue("android:layout_height");

    if (direction == Position.EAST || direction == Position.WEST) {
      myStaticHeight = height;
    }
    else if (direction == Position.NORTH || direction == Position.SOUTH) {
      myStaticWidth = width;
    }

    Rectangle bounds = myComponent.getBounds(myContext.getArea().getFeedbackLayer());

    if ("wrap_content".equals(width)) {
      myWrapBounds.width = bounds.width;
    }
    else {
      myWrapBounds.width = (int)(bounds.width * 0.75);
    }

    if ("wrap_content".equals(height)) {
      myWrapBounds.height = bounds.height;
    }
    else {
      myWrapBounds.height = (int)(bounds.height * 0.75);
    }
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myWrapFeedback = new RectangleComponent(Color.green, 1);
      layer.add(myWrapFeedback);

      myFeedback = new RectangleComponent(Color.blue, 2);
      layer.add(myFeedback);

      myTextFeedback = new JLabel();
      myTextFeedback.setBackground(LightColors.YELLOW);
      myTextFeedback.setBorder(new EmptyBorder(0, 5, 3, 0) {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
          Color oldColor = g.getColor();
          g.setColor(Color.darkGray);
          g.drawRect(x, y, width - 1, height - 1);
          g.setColor(oldColor);
        }
      });
      myTextFeedback.setOpaque(true);
      layer.add(myTextFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

    createFeedback();

    Rectangle bounds = myContext.getTransformedRectangle(myComponent.getBounds(layer));
    myFeedback.setBounds(bounds);

    int direction = myContext.getResizeDirection();

    if ((direction & Position.EAST) != 0) {
      myWrapBounds.x = bounds.x;
    }
    else {
      myWrapBounds.x = bounds.x + bounds.width - myWrapBounds.width;
    }

    if ((direction & Position.SOUTH) != 0) {
      myWrapBounds.y = bounds.y;
    }
    else {
      myWrapBounds.y = bounds.y + bounds.height - myWrapBounds.height;
    }

    myWrapFeedback.setBounds(myWrapBounds);

    myTextFeedback.setText(getSizeHint(myStaticWidth, bounds.width) + " x " + getSizeHint(myStaticHeight, bounds.height));
    Dimension textSize = myTextFeedback.getPreferredSize();
    Point location = myContext.getLocation();
    myTextFeedback.setBounds(location.x + 15, location.y + 15, textSize.width, textSize.height);
  }

  private static String getSizeHint(String staticText, int size) {
    return staticText != null ? staticText : Integer.toString(size) + "dp";
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myWrapFeedback);
      layer.remove(myFeedback);
      layer.remove(myTextFeedback);

      layer.repaint();

      myWrapFeedback = null;
      myFeedback = null;
      myTextFeedback = null;
    }
  }

  @Override
  public boolean canExecute() {
    return true;  // TODO: Auto-generated method stub
  }

  @Override
  public void execute() throws Exception {
    // TODO: Auto-generated method stub
  }
}