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
import com.intellij.android.designer.designSurface.layout.relative.*;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RelativeLayoutOperation extends AbstractEditOperation {
  private AlphaFeedback myBoundsFeedback;
  private SnapPointFeedbackHost mySnapFeedback;
  private Rectangle myBounds;
  private List<SnapPoint> myHorizontalPoints;
  private List<SnapPoint> myVerticalPoints;
  private SnapPoint myHorizontalPoint;
  private SnapPoint myVerticalPoint;

  public RelativeLayoutOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  private void createFeedback() {
    if (mySnapFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      mySnapFeedback = new SnapPointFeedbackHost();
      mySnapFeedback.setBounds(myContainer.getBounds(layer));
      createPoints();

      layer.add(mySnapFeedback);

      if (myContext.isCreate() || myContext.isPaste()) {
        myBounds = new Rectangle(0, 0, 64, 32);
      }
      else {
        Iterator<RadComponent> I = myComponents.iterator();
        myBounds = I.next().getBounds(layer);
        while (I.hasNext()) {
          myBounds.add(I.next().getBounds(layer));
        }

        if (myBounds.width == 0) {
          myBounds.width = 64;
        }
        if (myBounds.height == 0) {
          myBounds.height = 32;
        }
      }

      myBoundsFeedback = new AlphaFeedback(myComponents.size() == 1 ? Color.green : Color.orange);
      myBoundsFeedback.setSize(myBounds.width, myBounds.height);

      layer.add(myBoundsFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    Point location = myContext.getLocation();
    myBounds.x = location.x - myBounds.width / 2;
    myBounds.y = location.y - myBounds.height / 2;

    mySnapFeedback.clearAll();
    myHorizontalPoint = null;
    myVerticalPoint = null;

    for (SnapPoint point : myHorizontalPoints) {
      if (point.processBounds(myComponents, myBounds, mySnapFeedback)) {
        myHorizontalPoint = point;
        break;
      }
    }
    for (SnapPoint point : myVerticalPoints) {
      if (point.processBounds(myComponents, myBounds, mySnapFeedback)) {
        myVerticalPoint = point;
        break;
      }
    }

    mySnapFeedback.repaint();

    myBoundsFeedback.setLocation(myBounds.x, myBounds.y);
  }

  private void createPoints() {
    myHorizontalPoints = new ArrayList<SnapPoint>();
    myVerticalPoints = new ArrayList<SnapPoint>();

    List<RadComponent> children = new ArrayList<RadComponent>(myContainer.getChildren());
    children.removeAll(myComponents);
    for (RadComponent component : children) {
      createChildPoints(component);
    }

    createContainerPoints();
  }

  private void createChildPoints(RadComponent component) {
    myHorizontalPoints.add(new ComponentSnapPoint(component, true));
    myHorizontalPoints.add(new ContainerSnapPoint(myContainer, true));
    myVerticalPoints.add(new ComponentSnapPoint(component, false));
    myVerticalPoints.add(new ContainerSnapPoint(myContainer, false));
    myVerticalPoints.add(new BaselineSnapPoint(component));
    // XXX
  }

  private void createContainerPoints() {
    // XXX
  }

  @Override
  public void eraseFeedback() {
    if (mySnapFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(mySnapFeedback);
      layer.remove(myBoundsFeedback);
      layer.repaint();
      mySnapFeedback = null;
      myBoundsFeedback = null;
    }
  }

  @Override
  public void execute() throws Exception {
    // TODO: Auto-generated method stub
  }
}