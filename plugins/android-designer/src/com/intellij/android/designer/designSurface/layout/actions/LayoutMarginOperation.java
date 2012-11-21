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
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.EmptyPoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class LayoutMarginOperation implements EditOperation {
  public static final String TYPE = "layout_margin";

  protected final OperationContext myContext;
  protected RadViewComponent myComponent;
  protected RectangleFeedback myFeedback;
  protected TextFeedback myTextFeedback;
  private Rectangle myBounds;
  protected Rectangle myMargins;

  public LayoutMarginOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    myBounds = myComponent.getBounds(myContext.getArea().getFeedbackLayer());
    myMargins = myComponent.getMargins();
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

      myFeedback = new RectangleFeedback(getFeedbackColor(), 2);
      layer.add(myFeedback);

      layer.repaint();
    }
  }

  protected Color getFeedbackColor() {
    return Color.orange;
  }

  @Override
  public void showFeedback() {
    createFeedback();

    Rectangle bounds = myContext.getTransformedRectangle(myBounds);
    applyMargins(bounds, myMargins);
    myFeedback.setBounds(bounds);

    myTextFeedback.clear();
    fillTextFeedback();
    myTextFeedback.locationTo(myContext.getLocation(), 15);
  }

  protected void fillTextFeedback() {
    Point moveDelta = myContext.getMoveDelta();
    Dimension sizeDelta = myContext.getSizeDelta();
    int direction = myContext.getResizeDirection();

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

        XmlAttribute margin = tag.getAttribute("layout_margin", SdkConstants.NS_RESOURCES);
        if (margin != null) {
          String value = margin.getValue();
          margin.delete();

          if (!StringUtil.isEmpty(value)) {
            tag.setAttribute("layout_marginLeft", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginRight", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginTop", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginBottom", SdkConstants.NS_RESOURCES, value);
          }
        }

        Point moveDelta = myContext.getMoveDelta();
        Dimension sizeDelta = myContext.getSizeDelta();
        int direction = myContext.getResizeDirection();

        if (direction == Position.WEST) { // left
          setValue(tag, "layout_marginLeft", myMargins.x - moveDelta.x);
        }
        else if (direction == Position.EAST) { // right
          setValue(tag, "layout_marginRight", myMargins.width + sizeDelta.width);
        }
        else if (direction == Position.NORTH) { // top
          setValue(tag, "layout_marginTop", myMargins.y - moveDelta.y);
        }
        else if (direction == Position.SOUTH) { // bottom
          setValue(tag, "layout_marginBottom", myMargins.height + sizeDelta.height);
        }
      }
    });
  }

  private static void setValue(XmlTag tag, String name, int value) {
    if (value == 0) {
      ModelParser.deleteAttribute(tag, name);
    }
    else {
      tag.setAttribute(name, SdkConstants.NS_RESOURCES, Integer.toString(value) + "dp");
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(ResizeSelectionDecorator decorator) {
    pointFeedback(decorator);

    decorator.addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.WEST, TYPE, "Change layout:margin.left") { // left
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        location.x -= ((RadViewComponent)component).getMargins().x;
        return location;
      }
    });

    pointRight(decorator, Color.orange, 0.25, TYPE, "Change layout:margin.right");

    decorator.addPoint(new DirectionResizePoint(Color.orange, Color.black, Position.NORTH, TYPE, "Change layout:margin.top") { // top
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        location.y -= ((RadViewComponent)component).getMargins().y;
        return location;
      }
    });

    pointBottom(decorator, Color.orange, 0.25, TYPE, "Change layout:margin.bottom");
  }

  public static final BasicStroke STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{1, 2}, 0);

  protected static void pointFeedback(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new EmptyPoint() {
      @Override
      protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
        Rectangle bounds = component.getBounds(layer);
        applyMargins(bounds, ((RadViewComponent)component).getMargins());

        g.setStroke(STROKE);
        g.setColor(Color.red);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    });
  }

  protected static void pointRight(ResizeSelectionDecorator decorator,
                                   Color color,
                                   double ySeparator,
                                   Object type,
                                   @Nullable String description) {
    decorator.addPoint(new DirectionResizePoint(color, Color.black, Position.EAST, type, description) {
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        location.x += ((RadViewComponent)component).getMargins().width;
        return location;
      }
    }.move(1, ySeparator));
  }

  protected static void pointBottom(ResizeSelectionDecorator decorator,
                                    Color color,
                                    double xSeparator,
                                    Object type,
                                    @Nullable String description) {
    decorator.addPoint(new DirectionResizePoint(color, Color.black, Position.SOUTH, type, description) {
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        location.y += ((RadViewComponent)component).getMargins().height;
        return location;
      }
    }.move(xSeparator, 1));
  }

  private static void applyMargins(Rectangle bounds, Rectangle margins) {
    bounds.x -= margins.x;
    bounds.width += margins.x;

    bounds.y -= margins.y;
    bounds.height += margins.y;

    bounds.width += margins.width;
    bounds.height += margins.height;
  }
}