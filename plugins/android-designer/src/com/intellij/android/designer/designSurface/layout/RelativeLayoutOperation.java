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
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IdeBorderFactory;

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
  private TextFeedback myHorizontalTextFeedback;
  private TextFeedback myVerticalTextFeedback;
  private Rectangle myContainerBounds;
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

      myHorizontalTextFeedback = new TextFeedback();
      myHorizontalTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myHorizontalTextFeedback);

      myVerticalTextFeedback = new TextFeedback();
      myVerticalTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myVerticalTextFeedback);

      myContainerBounds = myContainer.getBounds(layer);

      mySnapFeedback = new SnapPointFeedbackHost();
      mySnapFeedback.setBounds(myContainerBounds);
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

      // XXX
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
    configureTextFeedback();
  }

  private void configureTextFeedback() {
    myHorizontalTextFeedback.clear();
    myVerticalTextFeedback.clear();

    myHorizontalTextFeedback.setVisible(myHorizontalPoint != null);
    if (myHorizontalPoint != null) {
      myHorizontalPoint.addTextInfo(myHorizontalTextFeedback);

      if (myVerticalPoint == null) {
        myHorizontalTextFeedback.centerTop(myContainerBounds);
      }
    }

    myVerticalTextFeedback.setVisible(myVerticalPoint != null);
    if (myVerticalPoint != null) {
      myVerticalPoint.addTextInfo(myVerticalTextFeedback);

      if (myHorizontalPoint == null) {
        myVerticalTextFeedback.centerTop(myContainerBounds);
      }
    }

    if (myHorizontalPoint != null && myVerticalPoint != null) {
      Dimension size1 = myHorizontalTextFeedback.getPreferredSize();
      Dimension size2 = myVerticalTextFeedback.getPreferredSize();

      int width = Math.max(size1.width, size2.width);
      int height = size1.height + size2.height;
      int x = myContainerBounds.x + myContainerBounds.width / 2 - width / 2;
      int y = myContainerBounds.y - height - 10;

      myHorizontalTextFeedback.setBounds(x, y, width, size1.height);
      myVerticalTextFeedback.setBounds(x, y + size1.height, width, size2.height);
    }
  }

  @Override
  public void eraseFeedback() {
    if (mySnapFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myHorizontalTextFeedback);
      layer.remove(myVerticalTextFeedback);
      layer.remove(mySnapFeedback);
      layer.remove(myBoundsFeedback);
      layer.repaint();
      myHorizontalTextFeedback = null;
      myVerticalTextFeedback = null;
      mySnapFeedback = null;
      myBoundsFeedback = null;
    }
  }

  private void createPoints() {
    myHorizontalPoints = new ArrayList<SnapPoint>();
    myVerticalPoints = new ArrayList<SnapPoint>();

    List<RadComponent> children = new ArrayList<RadComponent>(myContainer.getChildren());
    removeDepends(children, myComponents);
    for (RadComponent component : children) {
      createChildPoints((RadViewComponent)component);
    }

    RadViewComponent container = (RadViewComponent)myContainer;

    myHorizontalPoints.add(new ContainerSnapPoint(container, true));
    myVerticalPoints.add(new ContainerSnapPoint(container, false));

    myHorizontalPoints.add(new AutoSnapPoint(container, true));
    myVerticalPoints.add(new AutoSnapPoint(container, false));
  }

  private void createChildPoints(RadViewComponent component) {
    myHorizontalPoints.add(new ComponentSnapPoint(component, true));
    myVerticalPoints.add(new ComponentSnapPoint(component, false));
    myVerticalPoints.add(new BaselineSnapPoint(component));
  }

  private static void removeDepends(List<RadComponent> allChildren, List<RadComponent> editComponents) {
    allChildren.removeAll(editComponents);
    // XXX
  }

  @Override
  public void execute() throws Exception {
    if (myContext.isCreate() || myContext.isPaste() || myContext.isAdd()) {
      super.execute();
    }

    if (!myContext.isCreate() && !myContext.isPaste()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          for (RadComponent component : myComponents) {
            clear((RadViewComponent)component);
          }
        }
      });
    }

    if (myHorizontalPoint != null) {
      myHorizontalPoint.execute(myComponents);
    }
    if (myVerticalPoint != null) {
      myVerticalPoint.execute(myComponents);
    }
  }

  private static final String[] ATTRIBUTES =
    {"android:layout_toLeftOf", "android:layout_toRightOf", "android:layout_above", "android:layout_below", "android:layout_alignBaseline",
      "android:layout_alignLeft", "android:layout_alignTop", "android:layout_alignRight", "android:layout_alignBottom",
      "android:layout_alignParentLeft", "android:layout_alignParentTop", "android:layout_alignParentRight",
      "android:layout_alignParentBottom", "android:layout_centerInParent", "android:layout_centerHorizontal",
      "android:layout_centerVertical", "android:layout_margin", "android:layout_marginLeft", "android:layout_marginTop",
      "android:layout_marginRight",
      "android:layout_marginBottom", "android:layout_marginStart", "android:layout_marginEnd"};

  private static void clear(RadViewComponent component) {
    XmlTag tag = component.getTag();
    for (String attribute : ATTRIBUTES) {
      ModelParser.deleteAttribute(tag, attribute);
    }
  }
}