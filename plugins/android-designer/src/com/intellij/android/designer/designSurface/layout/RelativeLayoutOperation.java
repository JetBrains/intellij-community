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
import com.intellij.android.designer.designSurface.RootView;
import com.intellij.android.designer.designSurface.layout.relative.*;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.relative.RelativeInfo;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
      myContainer.setClientProperty(SnapPointFeedbackHost.KEY, Boolean.TRUE);

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

        myBoundsFeedback = new AlphaFeedback(myComponents.size() == 1 ? JBColor.GREEN : JBColor.ORANGE);
        // XXX
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

        if (myComponents.size() == 1) {
          RadComponent component = myComponents.get(0);
          final Rectangle bounds = component.getBounds();
          final BufferedImage image = ((RootView)((RadViewComponent)component.getRoot()).getNativeComponent()).getImage();

          myBoundsFeedback = new AlphaFeedback(null) {
            @Override
            protected void paintOther2(Graphics2D g2d) {
              g2d.drawImage(image,
                            0, 0, bounds.width, bounds.height,
                            bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height,
                            null);
            }
          };
        }
        else {
          myBoundsFeedback = new AlphaFeedback(JBColor.ORANGE);
        }
      }

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
    configureTextFeedback(myHorizontalTextFeedback, myVerticalTextFeedback, myHorizontalPoint, myVerticalPoint, myContainerBounds);
  }

  public static void configureTextFeedback(TextFeedback horizontalTextFeedback,
                                           TextFeedback verticalTextFeedback,
                                           SnapPoint horizontalPoint,
                                           SnapPoint verticalPoint,
                                           Rectangle containerBounds) {
    horizontalTextFeedback.clear();
    verticalTextFeedback.clear();

    horizontalTextFeedback.setVisible(horizontalPoint != null);
    if (horizontalPoint != null) {
      horizontalPoint.addTextInfo(horizontalTextFeedback);

      if (verticalPoint == null) {
        horizontalTextFeedback.centerTop(containerBounds);
      }
    }

    verticalTextFeedback.setVisible(verticalPoint != null);
    if (verticalPoint != null) {
      verticalPoint.addTextInfo(verticalTextFeedback);

      if (horizontalPoint == null) {
        verticalTextFeedback.centerTop(containerBounds);
      }
    }

    if (horizontalPoint != null && verticalPoint != null) {
      Dimension size1 = horizontalTextFeedback.getPreferredSize();
      Dimension size2 = verticalTextFeedback.getPreferredSize();

      int width = Math.max(size1.width, size2.width);
      int height = size1.height + size2.height;
      int x = containerBounds.x + containerBounds.width / 2 - width / 2;
      int y = containerBounds.y - height - 10;

      horizontalTextFeedback.setBounds(x, y, width, size1.height);
      verticalTextFeedback.setBounds(x, y + size1.height, width, size2.height);
    }
  }

  @Override
  public void eraseFeedback() {
    if (mySnapFeedback != null) {
      myContainer.extractClientProperty(SnapPointFeedbackHost.KEY);
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

    for (RadComponent component : getSnapComponents(myContainer, myComponents)) {
      myHorizontalPoints.add(new ComponentSnapPoint((RadViewComponent)component, true));
      myVerticalPoints.add(new ComponentSnapPoint((RadViewComponent)component, false));
      myVerticalPoints.add(new BaselineSnapPoint((RadViewComponent)component));
    }

    RadViewComponent container = (RadViewComponent)myContainer;

    myHorizontalPoints.add(new ContainerSnapPoint(container, true));
    myVerticalPoints.add(new ContainerSnapPoint(container, false));

    myHorizontalPoints.add(new AutoSnapPoint(container, true));
    myVerticalPoints.add(new AutoSnapPoint(container, false));
  }

  public static List<RadComponent> getSnapComponents(RadComponent container, List<RadComponent> components) {
    List<RadComponent> children = new ArrayList<RadComponent>(container.getChildren());
    children.removeAll(components);

    Map<RadComponent, RelativeInfo> relativeInfos = container.getClientProperty(RelativeInfo.KEY);
    for (RadComponent editComponent : components) {
      for (Iterator<RadComponent> I = children.iterator(); I.hasNext(); ) {
        RadComponent child = I.next();
        RelativeInfo info = relativeInfos.get(child);
        if (info.contains(editComponent)) {
          I.remove();
        }
      }
    }

    return children;
  }

  @Override
  public void execute() throws Exception {
    if (myContext.isCreate() || myContext.isPaste() || myContext.isAdd()) {
      super.execute();
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (RadComponent component : myComponents) {
          clear((RadViewComponent)component);
        }
      }
    });

    if (myHorizontalPoint != null) {
      myHorizontalPoint.execute(myComponents);
    }
    if (myVerticalPoint != null) {
      myVerticalPoint.execute(myComponents);
    }
  }

  private static final String[] ATTRIBUTES =
    {"layout_toLeftOf", "layout_toRightOf", "layout_above", "layout_below", "layout_alignBaseline",
      "layout_alignLeft", "layout_alignTop", "layout_alignRight", "layout_alignBottom",
      "layout_alignParentLeft", "layout_alignParentTop", "layout_alignParentRight",
      "layout_alignParentBottom", "layout_centerInParent", "layout_centerHorizontal",
      "layout_centerVertical", "layout_margin", "layout_marginLeft", "layout_marginTop",
      "layout_marginRight", "layout_marginBottom", "layout_marginStart", "layout_marginEnd"};

  private static void clear(RadViewComponent component) {
    XmlTag tag = component.getTag();
    for (String attribute : ATTRIBUTES) {
      ModelParser.deleteAttribute(tag, attribute);
    }

    // TODO: clear out depends???
  }
}