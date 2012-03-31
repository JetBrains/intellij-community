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
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.RectangleComponent;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.Colors;
import com.intellij.ui.LightColors;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;

import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeOperation implements EditOperation {
  public static final String TYPE = "resize_children";

  private static SimpleTextAttributes DIMENSION_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.lightGray);
  private static SimpleTextAttributes SNAP_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Colors.DARK_GREEN);
  private static final int SNAP_DELTA = 4;
  private static final int WRAP_CONTENT = 0 << 30;

  private final OperationContext myContext;
  private RadViewComponent myComponent;

  private RectangleComponent myWrapFeedback;
  private RectangleComponent myFillFeedback;
  private RectangleComponent myFeedback;
  private SimpleColoredComponent myTextFeedback;

  private String myStaticWidth;
  private String myStaticHeight;

  private Dimension myWrapSize;
  private Dimension myFillSize;

  private Rectangle myBounds;

  public static void points(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(Position.EAST, TYPE));
    decorator.addPoint(new DirectionResizePoint(Position.SOUTH, TYPE));
    decorator.addPoint(new DirectionResizePoint(Position.SOUTH_EAST, TYPE));
  }

  public ResizeOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;

    Rectangle bounds = myComponent.getBounds(myContext.getArea().getFeedbackLayer());

    String width = myComponent.getTag().getAttributeValue("android:layout_width");
    String height = myComponent.getTag().getAttributeValue("android:layout_height");

    Pair<Integer, Integer> widthInfo = getDefaultSize(width, bounds.width);
    Pair<Integer, Integer> heightInfo = getDefaultSize(height, bounds.height);

    myWrapSize = new Dimension(widthInfo.first, heightInfo.first);
    calculateWrapSize(bounds);

    myFillSize = new Dimension(widthInfo.second, heightInfo.second);
    calculateFillParentSize(bounds);

    createStaticFeedback(bounds, width, height);
  }

  private void calculateWrapSize(Rectangle bounds) {
    if (myWrapSize.width == -1 || myWrapSize.height == -1) {
      try {
        Object viewObject = myComponent.getViewInfo().getViewObject();
        Class<?> viewClass = viewObject.getClass();

        viewClass.getMethod("forceLayout").invoke(viewObject);
        viewClass.getMethod("measure", int.class, int.class).invoke(viewObject, WRAP_CONTENT, WRAP_CONTENT);

        if (myWrapSize.width == -1) {
          myWrapSize.width = (Integer)viewClass.getMethod("getMeasuredWidth").invoke(viewObject);
        }
        if (myWrapSize.height == -1) {
          myWrapSize.height = (Integer)viewClass.getMethod("getMeasuredHeight").invoke(viewObject);
        }
      }
      catch (Throwable e) {
        if (myWrapSize.width == -1) {
          myWrapSize.width = bounds.width;
        }
        if (myWrapSize.height == -1) {
          myWrapSize.height = bounds.height;
        }
      }
    }
  }

  private void calculateFillParentSize(Rectangle bounds) {
    if (myFillSize.width == -1 || myFillSize.height == -1) {
      Rectangle parentBounds = myComponent.getParent().getBounds(myContext.getArea().getFeedbackLayer());

      if (myFillSize.width == -1) {
        myFillSize.width = parentBounds.x + parentBounds.width - bounds.x;
      }
      if (myFillSize.height == -1) {
        myFillSize.height = parentBounds.y + parentBounds.height - bounds.y;
      }
    }
    if (myWrapSize.width == myFillSize.width) {
      myFillSize.width += 10;
    }
    if (myWrapSize.height == myFillSize.height) {
      myFillSize.height += 10;
    }
  }

  private static Pair<Integer, Integer> getDefaultSize(String value, int size) {
    int wrap = -1;
    int fill = -1;

    if ("wrap_content".equals(value)) {
      wrap = size;
    }
    else if ("fill_parent".equals(value) || "match_parent".equals(value)) {
      fill = size;
    }

    return new Pair<Integer, Integer>(wrap, fill);
  }

  private void createStaticFeedback(Rectangle bounds, String width, String height) {
    int direction = myContext.getResizeDirection();

    Rectangle wrapBounds;
    Rectangle fillBounds;
    if (direction == Position.EAST) {
      myStaticHeight = height;
      wrapBounds = new Rectangle(bounds.x, bounds.y, myWrapSize.width, bounds.height);
      fillBounds = new Rectangle(bounds.x, bounds.y, myFillSize.width, bounds.height);
    }
    else if (direction == Position.SOUTH) {
      myStaticWidth = width;
      wrapBounds = new Rectangle(bounds.x, bounds.y, bounds.width, myWrapSize.height);
      fillBounds = new Rectangle(bounds.x, bounds.y, bounds.width, myFillSize.height);
    }
    else {
      wrapBounds = new Rectangle(bounds.getLocation(), myWrapSize);
      fillBounds = new Rectangle(bounds.getLocation(), myFillSize);
    }

    myWrapFeedback = new RectangleComponent(Color.green, 1);
    myWrapFeedback.setBounds(wrapBounds);

    myFillFeedback = new RectangleComponent(Color.green, 1);
    myFillFeedback.setBounds(fillBounds);
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myFeedback = new RectangleComponent(Color.blue, 2);
      layer.add(myFeedback);

      myTextFeedback = new SimpleColoredComponent();
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
      layer.add(myTextFeedback);
      layer.add(myWrapFeedback);
      layer.add(myFillFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

    createFeedback();

    myBounds = myContext.getTransformedRectangle(myComponent.getBounds(layer));

    int direction = myContext.getResizeDirection();

    if ((direction & Position.EAST) != 0) {
      if (!snapToWidth(myBounds, myWrapSize, SNAP_DELTA)) {
        snapToWidth(myBounds, myFillSize, SNAP_DELTA);
      }
    }
    if ((direction & Position.SOUTH) != 0) {
      if (!snapToHeight(myBounds, myWrapSize, SNAP_DELTA)) {
        snapToHeight(myBounds, myFillSize, SNAP_DELTA);
      }
    }

    myFeedback.setBounds(myBounds);

    myTextFeedback.clear();
    addTextSize(myStaticWidth, myBounds.width, myWrapSize.width, myFillSize.width);
    myTextFeedback.append(" x ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    addTextSize(myStaticHeight, myBounds.height, myWrapSize.height, myFillSize.height);

    Dimension textSize = myTextFeedback.getPreferredSize();
    Point location = myContext.getLocation();
    myTextFeedback.setBounds(location.x + 15, location.y + 15, textSize.width, textSize.height);
  }

  private void addTextSize(String staticText, int size, int wrap, int fill) {
    if (staticText == null) {
      if (size == wrap) {
        myTextFeedback.append("wrap_content", SNAP_ATTRIBUTES);
      }
      else if (size == fill) {
        myTextFeedback.append("match_parent", SNAP_ATTRIBUTES);
      }
      else {
        myTextFeedback.append(Integer.toString(size));
        myTextFeedback.append("dp", DIMENSION_ATTRIBUTES);
      }
    }
    else {
      if (staticText.length() > 2) {
        int index = staticText.length() - 2;
        String dimension = staticText.substring(index);
        if (ArrayUtil.indexOf(ResourceRenderer.DIMENSIONS, dimension) != -1) {
          myTextFeedback.append(staticText.substring(0, index));
          myTextFeedback.append(dimension, DIMENSION_ATTRIBUTES);
        }
        else {
          myTextFeedback.append(staticText);
        }
      }
      else {
        myTextFeedback.append(staticText);
      }
    }
  }

  private static boolean snapToWidth(Rectangle bounds, Dimension size, int delta) {
    if (Math.abs(bounds.width - size.width) < delta) {
      bounds.width = size.width;
      return true;
    }
    return false;
  }

  private static boolean snapToHeight(Rectangle bounds, Dimension size, int delta) {
    if (Math.abs(bounds.height - size.height) < delta) {
      bounds.height = size.height;
      return true;
    }
    return false;
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      layer.remove(myTextFeedback);
      layer.remove(myWrapFeedback);
      layer.remove(myFillFeedback);

      layer.repaint();

      myFeedback = null;
      myTextFeedback = null;
      myWrapFeedback = null;
      myFillFeedback = null;
    }
  }

  @Override
  public boolean canExecute() {
    return true;
  }

  @Override
  public void execute() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        int direction = myContext.getResizeDirection();

        if ((direction & Position.EAST) != 0) {
          myComponent.getTag().setAttribute("android:layout_width", getSize(myBounds.width, myWrapSize.width, myFillSize.width));
        }
        if ((direction & Position.SOUTH) != 0) {
          myComponent.getTag().setAttribute("android:layout_height", getSize(myBounds.height, myWrapSize.height, myFillSize.height));
        }
      }
    });
  }

  private static String getSize(int size, int wrap, int fill) {
    if (size == wrap) {
      return "wrap_content";
    }
    else if (size == fill) {
      return "fill_parent";
    }
    else {
      return Integer.toString(size) + "dp";
    }
  }
}