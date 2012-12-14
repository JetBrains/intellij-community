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
import com.intellij.android.designer.designSurface.layout.RelativeLayoutOperation;
import com.intellij.android.designer.designSurface.layout.relative.*;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RelativeLayoutResizeOperation implements EditOperation {
  public static final String TYPE = "relative_resize";

  private final OperationContext myContext;
  private List<RadComponent> myComponents;
  private RadViewComponent myComponent;
  private RadViewComponent myContainer;

  private RectangleFeedback myBoundsFeedback;
  private RectangleFeedback myWrapFeedback;
  private SnapPointFeedbackHost mySnapFeedback;
  private TextFeedback myHorizontalTextFeedback;
  private TextFeedback myVerticalTextFeedback;

  private Rectangle myContainerBounds;
  private Dimension myWrapSize;

  private Side myResizeHorizontalSide;
  private Side myResizeVerticalSide;

  private List<ResizeSnapPoint> myHorizontalPoints;
  private List<ResizeSnapPoint> myVerticalPoints;

  private SnapPoint myHorizontalPoint;
  private SnapPoint myVerticalPoint;

  public RelativeLayoutResizeOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponents = Arrays.asList(component);
    myComponent = (RadViewComponent)component;
    myContainer = (RadViewComponent)myComponent.getParent();
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
    if (mySnapFeedback == null) {
      myContainer.setClientProperty(SnapPointFeedbackHost.KEY, Boolean.TRUE);

      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myHorizontalTextFeedback = new TextFeedback();
      myHorizontalTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myHorizontalTextFeedback);

      myVerticalTextFeedback = new TextFeedback();
      myVerticalTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myVerticalTextFeedback);

      myBoundsFeedback = new RectangleFeedback(JBColor.BLUE, 2);
      layer.add(myBoundsFeedback);

      int direction = myContext.getResizeDirection();

      if (direction == Position.EAST || direction == Position.SOUTH || direction == Position.SOUTH_EAST) {
        Rectangle bounds = myComponent.getBounds(myContext.getArea().getFeedbackLayer());
        String width = myComponent.getTag().getAttributeValue("layout_width", SdkConstants.NS_RESOURCES);
        String height = myComponent.getTag().getAttributeValue("layout_height", SdkConstants.NS_RESOURCES);

        Pair<Integer, Integer> widthInfo = ResizeOperation.getDefaultSize(width, bounds.width);
        Pair<Integer, Integer> heightInfo = ResizeOperation.getDefaultSize(height, bounds.height);

        myWrapSize = new Dimension(widthInfo.first, heightInfo.first);
        myComponent.calculateWrapSize(myWrapSize, bounds);

        Rectangle wrapBounds;
        if (direction == Position.EAST) {
          wrapBounds = new Rectangle(bounds.x, bounds.y, myWrapSize.width, bounds.height);
        }
        else if (direction == Position.SOUTH) {
          wrapBounds = new Rectangle(bounds.x, bounds.y, bounds.width, myWrapSize.height);
        }
        else {
          wrapBounds = new Rectangle(bounds.getLocation(), myWrapSize);
        }

        myWrapFeedback = new RectangleFeedback(JBColor.GREEN, 1);
        myWrapFeedback.setBounds(wrapBounds);
        layer.add(myWrapFeedback);
      }

      if ((direction & Position.NORTH) != 0) {
        myResizeVerticalSide = Side.top;
      }
      if ((direction & Position.SOUTH) != 0) {
        myResizeVerticalSide = Side.bottom;
      }
      if ((direction & Position.WEST) != 0) {
        myResizeHorizontalSide = Side.left;
      }
      if ((direction & Position.EAST) != 0) {
        myResizeHorizontalSide = Side.right;
      }

      myContainerBounds = myContainer.getBounds(layer);

      mySnapFeedback = new SnapPointFeedbackHost();
      mySnapFeedback.setBounds(myContainerBounds);

      createPoints();

      layer.add(mySnapFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    Rectangle bounds = myContext.getTransformedRectangle(myComponent.getBounds(myContext.getArea().getFeedbackLayer()));
    bounds.width = Math.max(bounds.width, 0);
    bounds.height = Math.max(bounds.height, 0);

    mySnapFeedback.clearAll();
    myHorizontalPoint = null;
    myVerticalPoint = null;

    for (ResizeSnapPoint point : myHorizontalPoints) {
      if (point.processBounds(myComponents, bounds, myResizeHorizontalSide, mySnapFeedback)) {
        myHorizontalPoint = point;
        break;
      }
    }
    for (ResizeSnapPoint point : myVerticalPoints) {
      if (point.processBounds(myComponents, bounds, myResizeVerticalSide, mySnapFeedback)) {
        myVerticalPoint = point;
        break;
      }
    }

    mySnapFeedback.repaint();
    myBoundsFeedback.setBounds(bounds);
    RelativeLayoutOperation.configureTextFeedback(myHorizontalTextFeedback,
                                                  myVerticalTextFeedback,
                                                  myHorizontalPoint,
                                                  myVerticalPoint,
                                                  myContainerBounds);
  }

  @Override
  public void eraseFeedback() {
    if (mySnapFeedback != null) {
      myContainer.extractClientProperty(SnapPointFeedbackHost.KEY);
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myHorizontalTextFeedback);
      layer.remove(myVerticalTextFeedback);
      layer.remove(myBoundsFeedback);

      if (myWrapFeedback != null) {
        layer.remove(myWrapFeedback);
      }

      layer.remove(mySnapFeedback);
      layer.repaint();
      myHorizontalTextFeedback = null;
      myVerticalTextFeedback = null;
      myBoundsFeedback = null;
      myWrapFeedback = null;
      mySnapFeedback = null;
    }
  }

  private void createPoints() {
    myHorizontalPoints = new ArrayList<ResizeSnapPoint>();
    myVerticalPoints = new ArrayList<ResizeSnapPoint>();

    List<RadComponent> snapComponents = RelativeLayoutOperation.getSnapComponents(myContainer, myComponents);
    snapComponents.removeAll(myContext.getComponents());

    int direction = myContext.getResizeDirection();
    if ((direction & Position.NORTH_SOUTH) != 0) {
      createPoints(myVerticalPoints, snapComponents, false, direction);
    }
    if ((direction & Position.EAST_WEST) != 0) {
      createPoints(myHorizontalPoints, snapComponents, true, direction);
    }
  }

  private void createPoints(List<ResizeSnapPoint> points, List<RadComponent> snapComponents, boolean horizontal, int direction) {
    for (RadComponent component : snapComponents) {
      points.add(new ResizeComponentSnapPoint((RadViewComponent)component, horizontal));
    }

    points.add(new ResizeContainerSnapPoint(myContainer, horizontal));

    if (direction == Position.EAST || direction == Position.SOUTH || direction == Position.SOUTH_EAST) {
      points.add(new WrapSizeSnapPoint(myComponent, horizontal, myWrapSize));
    }

    points.add(new AutoResizeSnapPoint(myContainer, horizontal));
  }

  @Override
  public boolean canExecute() {
    return true;
  }

  @Override
  public void execute() throws Exception {
    if (myHorizontalPoint != null) {
      myHorizontalPoint.execute(myComponents);
    }
    if (myVerticalPoint != null) {
      myVerticalPoint.execute(myComponents);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.NORTH_WEST, TYPE,
                                                "Change layout:width x layout:height, top x left alignment"));
    decorator
      .addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.NORTH, TYPE, "Change layout:height, top alignment"));
    decorator.addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.NORTH_EAST, TYPE,
                                                "Change layout:width x layout:height, top x right alignment"));
    decorator
      .addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.EAST, TYPE, "Change layout:width, right alignment"));
    decorator.addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.SOUTH_EAST, TYPE,
                                                "Change layout:width x layout:height, bottom x right alignment"));
    decorator.addPoint(
      new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.SOUTH, TYPE, "Change layout:height, bottom alignment"));
    decorator.addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.SOUTH_WEST, TYPE,
                                                "Change layout:width x layout:height, bottom x left alignment"));
    decorator
      .addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.WEST, TYPE, "Change layout:width, left alignment"));
  }
}