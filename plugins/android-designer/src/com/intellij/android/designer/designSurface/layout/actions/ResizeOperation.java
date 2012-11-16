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

import com.android.sdklib.SdkConstants;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
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
import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeOperation implements EditOperation {
  public static final String TYPE = "resize_children";

  public final static Color blue = new Color(0, 50, 255);
  private static final int SNAP_DELTA = 4;

  private final OperationContext myContext;
  private RadViewComponent myComponent;

  private RectangleFeedback myWrapFeedback;
  private RectangleFeedback myFillFeedback;
  private RectangleFeedback myFeedback;
  private TextFeedback myTextFeedback;

  private String myStaticWidth;
  private String myStaticHeight;

  private Dimension myWrapSize;
  private Dimension myFillSize;

  private Rectangle myBounds;

  public ResizeOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;

    Rectangle bounds = myComponent.getBounds(myContext.getArea().getFeedbackLayer());
    String width = myComponent.getTag().getAttributeValue("layout_width", SdkConstants.NS_RESOURCES);
    String height = myComponent.getTag().getAttributeValue("layout_height", SdkConstants.NS_RESOURCES);

    Pair<Integer, Integer> widthInfo = getDefaultSize(width, bounds.width);
    Pair<Integer, Integer> heightInfo = getDefaultSize(height, bounds.height);

    myWrapSize = new Dimension(widthInfo.first, heightInfo.first);
    myComponent.calculateWrapSize(myWrapSize, bounds);

    myFillSize = new Dimension(widthInfo.second, heightInfo.second);
    calculateFillParentSize(bounds);

    createStaticFeedback(bounds, width, height);
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

  public static Pair<Integer, Integer> getDefaultSize(String value, int size) {
    int wrap = -1;
    int fill = -1;

    if ("wrap_content".equals(value)) {
      wrap = size;
    }
    else if (isFill(value)) {
      fill = size;
    }

    return new Pair<Integer, Integer>(wrap, fill);
  }

  public static boolean isFill(String value) {
    return "fill_parent".equals(value) || "match_parent".equals(value);
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

    myWrapFeedback = new RectangleFeedback(Color.green, 1);
    myWrapFeedback.setBounds(wrapBounds);

    myFillFeedback = new RectangleFeedback(Color.green, 1);
    myFillFeedback.setBounds(fillBounds);
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myFeedback = new RectangleFeedback(Color.blue, 2);
      layer.add(myFeedback);

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(new LineMarginBorder(0, 5, 3, 0));
      layer.add(myTextFeedback);
      layer.add(myWrapFeedback);
      layer.add(myFillFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    myBounds = myContext.getTransformedRectangle(myComponent.getBounds(myContext.getArea().getFeedbackLayer()));
    myBounds.width = Math.max(myBounds.width, 0);
    myBounds.height = Math.max(myBounds.height, 0);

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

    myTextFeedback.locationTo(myContext.getLocation(), 15);
  }

  private void addTextSize(String staticText, int size, int wrap, int fill) {
    if (staticText == null) {
      if (size == wrap) {
        myTextFeedback.snap("wrap_content");
      }
      else if (size == fill) {
        myTextFeedback.snap("match_parent");
      }
      else {
        myTextFeedback.append(Integer.toString(size));
        myTextFeedback.dimension("dp");
      }
    }
    else if (staticText.length() > 3 && staticText.endsWith("dip")) {
      myTextFeedback.append(staticText.substring(0, staticText.length() - 3));
      myTextFeedback.dimension("dip");
    }
    else if (staticText.length() > 2) {
      int index = staticText.length() - 2;
      String dimension = staticText.substring(index);
      if (ArrayUtil.indexOf(ResourceRenderer.DIMENSIONS, dimension) != -1) {
        myTextFeedback.append(staticText.substring(0, index));
        myTextFeedback.dimension(dimension);
      }
      else {
        myTextFeedback.append(staticText);
      }
    }
    else {
      myTextFeedback.append(staticText);
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
          myComponent.getTag()
            .setAttribute("layout_width", SdkConstants.NS_RESOURCES, getSize(myBounds.width, myWrapSize.width, myFillSize.width));
        }
        if ((direction & Position.SOUTH) != 0) {
          myComponent.getTag()
            .setAttribute("layout_height", SdkConstants.NS_RESOURCES, getSize(myBounds.height, myWrapSize.height, myFillSize.height));
        }
      }
    });
  }

  private static String getSize(int size, int wrap, int fill) {
    if (size == wrap) {
      return "wrap_content";
    }
    if (size == fill) {
      return "fill_parent";
    }
    return Integer.toString(size) + "dp";
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(ResizeSelectionDecorator decorator) {
    width(decorator);
    height(decorator);
    decorator.addPoint(new DirectionResizePoint(blue, Color.black, Position.SOUTH_EAST, TYPE, "Change layout:width x layout:height"));
  }

  public static void width(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(blue, Color.black, Position.EAST, TYPE, "Change layout:width"));
  }

  public static void height(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(blue, Color.black, Position.SOUTH, TYPE, "Change layout:height"));
  }
}