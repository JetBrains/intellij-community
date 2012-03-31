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
import com.intellij.android.designer.model.layout.RadFrameLayout;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.designSurface.feedbacks.RectangleComponent;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
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
  private RectangleComponent myFeedback;
  private TextFeedback myTextFeedback;

  public FrameLayoutMarginOperation(OperationContext context) {
    myContext = context;
  }

  public static void points(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.WEST, TYPE)); // left
    decorator.addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.EAST, TYPE).move(1, 0.25)); // right
    decorator.addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.NORTH, TYPE)); // top
    decorator.addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.SOUTH, TYPE).move(0.25, 1)); // bottom
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

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
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

      myFeedback = new RectangleComponent(Color.orange, 2);
      layer.add(myFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    myFeedback.setBounds(myContext.getTransformedRectangle(myComponent.getBounds(myContext.getArea().getFeedbackLayer())));

    Point moveDelta = myContext.getMoveDelta();
    Dimension sizeDelta = myContext.getSizeDelta();
    int direction = myContext.getResizeDirection();

    myTextFeedback.clear();

    if (direction == Position.WEST) { // left
      myTextFeedback.append(Integer.toString(-moveDelta.x));
    }
    else if (direction == Position.EAST) { // right
      myTextFeedback.append(Integer.toString(sizeDelta.width));
    }
    else if (direction == Position.NORTH) { // top
      myTextFeedback.append(Integer.toString(-moveDelta.y));
    }
    else if (direction == Position.SOUTH) { // bottom
      myTextFeedback.append(Integer.toString(sizeDelta.height));
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
          setValue(tag, "android:layout_marginLeft", -moveDelta.x);
        }
        else if (direction == Position.EAST) { // right
          setValue(tag, "android:layout_marginRight", sizeDelta.width);
        }
        else if (direction == Position.NORTH) { // top
          setValue(tag, "android:layout_marginTop", -moveDelta.y);
        }
        else if (direction == Position.SOUTH) { // bottom
          setValue(tag, "android:layout_marginBottom", sizeDelta.height);
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
}