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

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.Gravity;
import com.intellij.android.designer.model.layout.RadFrameLayout;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class FrameLayoutMarginOperation implements EditOperation {
  public static final String TYPE = "frame_layout_margin";

  private final OperationContext myContext;
  private RadViewComponent myComponent;
  private RectangleFeedback myFeedback;
  private TextFeedback myTextFeedback;
  private Rectangle myMargins;

  public FrameLayoutMarginOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    myMargins = getMargins(myComponent);
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

      myFeedback = new RectangleFeedback(Color.orange, 2);
      layer.add(myFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    Rectangle bounds = myContext.getTransformedRectangle(myComponent.getBounds(myContext.getArea().getFeedbackLayer()));
    applyMargins(bounds, myMargins);
    myFeedback.setBounds(bounds);

    Point moveDelta = myContext.getMoveDelta();
    Dimension sizeDelta = myContext.getSizeDelta();
    int direction = myContext.getResizeDirection();

    myTextFeedback.clear();

    if (direction == Position.WEST) { // left
      myTextFeedback.append(Integer.toString(myMargins.x - moveDelta.x));
    }
    else if (direction == Position.EAST) { // right

      myTextFeedback.append(Integer.toString(myMargins.width + sizeDelta.width));
    }
    else if (direction == Position.NORTH) { // top
      myTextFeedback.append(Integer.toString(myMargins.y - moveDelta.y));
    }
    else if (direction == Position.SOUTH) { // bottom
      myTextFeedback.append(Integer.toString(myMargins.height + sizeDelta.height));
    }

    myTextFeedback.dimension("dp");
    myTextFeedback.locationTo(myContext.getLocation(), 15);
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
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = myComponent.getTag();

        XmlAttribute margin = tag.getAttribute("android:layout_margin");
        if (margin != null) {
          String value = margin.getValue();
          margin.delete();

          if (!StringUtil.isEmpty(value)) {
            tag.setAttribute("android:layout_marginLeft", value);
            tag.setAttribute("android:layout_marginRight", value);
            tag.setAttribute("android:layout_marginTop", value);
            tag.setAttribute("android:layout_marginBottom", value);
          }
        }

        Point moveDelta = myContext.getMoveDelta();
        Dimension sizeDelta = myContext.getSizeDelta();
        int direction = myContext.getResizeDirection();

        if (direction == Position.WEST) { // left
          setValue(tag, "android:layout_marginLeft", myMargins.x - moveDelta.x);
        }
        else if (direction == Position.EAST) { // right
          setValue(tag, "android:layout_marginRight", myMargins.width + sizeDelta.width);
        }
        else if (direction == Position.NORTH) { // top
          setValue(tag, "android:layout_marginTop", myMargins.y - moveDelta.y);
        }
        else if (direction == Position.SOUTH) { // bottom
          setValue(tag, "android:layout_marginBottom", myMargins.height + sizeDelta.height);
        }
      }
    });
  }

  private static void setValue(XmlTag tag, String name, int value) {
    if (value == 0) {
      XmlAttribute attribute = tag.getAttribute(name);
      if (attribute != null) {
        attribute.delete();
      }
    }
    else {
      tag.setAttribute(name, Integer.toString(value) + "dp");
    }
  }

  public static boolean visible(RadComponent component, DirectionResizePoint point) {
    Pair<Gravity, Gravity> gravity = RadFrameLayout.gravity(component);
    int direction = point.getDirection();

    if (direction == Position.WEST) { // left
      return gravity.first == Gravity.left || gravity.first == Gravity.center;
    }
    else if (direction == Position.EAST) { // right
      return gravity.first == Gravity.right || gravity.first == Gravity.center;
    }
    else if (direction == Position.NORTH) { // top
      return gravity.second == Gravity.top || gravity.second == Gravity.center;
    }
    else if (direction == Position.SOUTH) { // bottom
      return gravity.second == Gravity.bottom || gravity.second == Gravity.center;
    }
    return true;
  }

  private static final BasicStroke STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{1, 2}, 0);

  public static void points(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new ResizePoint() {

      @Override
      protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
        Rectangle bounds = component.getBounds(layer);
        applyMargins(bounds, getMargins(component));

        g.setStroke(STROKE);
        g.setColor(Color.red);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }

      @Override
      public InputTool findTargetTool(DecorationLayer layer, RadComponent component, int x, int y) {
        return null;
      }

      @Override
      public Object getType() {
        return null;
      }

      @Override
      protected InputTool createTool(RadComponent component) {
        return null;
      }

      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        return null;
      }
    });

    decorator.addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.WEST, FrameLayoutMarginOperation.TYPE) { // left
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        location.x -= getMargin(component, "leftMargin");
        return location;
      }
    });

    decorator
      .addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.EAST, FrameLayoutMarginOperation.TYPE) { // right
        @Override
        protected Point getLocation(DecorationLayer layer, RadComponent component) {
          Point location = super.getLocation(layer, component);
          location.x += getMargin(component, "rightMargin");
          return location;
        }
      }.move(1, 0.25));

    decorator.addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.NORTH, FrameLayoutMarginOperation.TYPE) { // top
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        location.y -= getMargin(component, "topMargin");
        return location;
      }
    });

    decorator.addPoint(
      new DirectionResizePoint(Color.orange, Color.black, Position.SOUTH, FrameLayoutMarginOperation.TYPE) { // bottom
        @Override
        protected Point getLocation(DecorationLayer layer, RadComponent component) {
          Point location = super.getLocation(layer, component);
          location.y += getMargin(component, "bottomMargin");
          return location;
        }
      }.move(0.25, 1));
  }

  private static void applyMargins(Rectangle bounds, Rectangle margins) {
    bounds.x -= margins.x;
    bounds.width += margins.x;

    bounds.y -= margins.y;
    bounds.height += margins.y;

    bounds.width += margins.width;
    bounds.height += margins.height;
  }

  private static Rectangle getMargins(RadComponent component) {
    Rectangle margins = new Rectangle();

    try {
      ViewInfo viewInfo = ((RadViewComponent)component).getViewInfo();
      Object layoutParams = viewInfo.getLayoutParamsObject();
      Class<?> layoutClass = layoutParams.getClass();

      margins.x = layoutClass.getField("leftMargin").getInt(layoutParams);
      margins.y = layoutClass.getField("topMargin").getInt(layoutParams);
      margins.width = layoutClass.getField("rightMargin").getInt(layoutParams);
      margins.height = layoutClass.getField("bottomMargin").getInt(layoutParams);
    }
    catch (Throwable e) {
    }

    return margins;
  }

  private static int getMargin(RadComponent component, String marginName) {
    try {
      ViewInfo viewInfo = ((RadViewComponent)component).getViewInfo();
      Object layoutParams = viewInfo.getLayoutParamsObject();

      return layoutParams.getClass().getField(marginName).getInt(layoutParams);
    }
    catch (Throwable e) {
      return 0;
    }
  }
}