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

import com.android.SdkConstants;
import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;

import java.awt.*;
import java.util.Iterator;

/**
 * @author Alexander Lobas
 */
public class AbsoluteLayoutOperation extends AbstractEditOperation {
  private AlphaFeedback myFeedback;
  private Rectangle myBounds;
  private Point myStartLocation;

  public AbsoluteLayoutOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

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

        myStartLocation = myBounds.getLocation();
      }

      myFeedback = new AlphaFeedback(myComponents.size() == 1 ? Color.green : Color.orange);
      myFeedback.setSize(myBounds.width, myBounds.height);

      layer.add(myFeedback);
      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();
    Point location = myContext.getLocation();
    Dimension delta = myContext.getSizeDelta();

    if (delta == null || (delta.width == 0 && delta.height == 0) || myComponents.size() > 1) {
      myBounds.x = location.x - myBounds.width / 2;
      myBounds.y = location.y - myBounds.height / 2;
      myFeedback.setLocation(myBounds.x, myBounds.y);
    }
    else {
      myBounds.width = location.x - myBounds.x;
      myBounds.height = location.y - myBounds.y;
      myFeedback.setSize(myBounds.width, myBounds.height);
    }
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      layer.repaint();
      myFeedback = null;
    }
  }

  @Override
  public void execute() throws Exception {
    if (!myContext.isMove()) {
      super.execute();
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
        Rectangle parentBounds = myContainer.getBounds();

        if (myContext.isCreate() || myContext.isPaste()) {
          Point location = myContainer.convertPoint(layer, myBounds.x, myBounds.y);
          Dimension delta = myContext.getSizeDelta();

          for (RadComponent component : myComponents) {
            XmlTag tag = ((RadViewComponent)component).getTag();

            tag.setAttribute("layout_x", SdkConstants.NS_RESOURCES, Integer.toString(location.x - parentBounds.x) + "dp");
            tag.setAttribute("layout_y", SdkConstants.NS_RESOURCES, Integer.toString(location.y - parentBounds.y) + "dp");

            if (delta != null && myComponents.size() == 1) {
              if (delta.width > 0) {
                tag.setAttribute("layout_width", SdkConstants.NS_RESOURCES, Integer.toString(myBounds.width) + "dp");
              }
              if (delta.height > 0) {
                tag.setAttribute("layout_height", SdkConstants.NS_RESOURCES, Integer.toString(myBounds.height) + "dp");
              }
            }
          }
        }
        else {
          int moveDeltaX = myBounds.x - myStartLocation.x;
          int moveDeltaY = myBounds.y - myStartLocation.y;

          for (RadComponent component : myComponents) {
            Rectangle bounds = component.getBounds(layer);
            Point location = component.convertPoint(layer, bounds.x + moveDeltaX, bounds.y + moveDeltaY);
            XmlTag tag = ((RadViewComponent)component).getTag();

            tag.setAttribute("layout_x", SdkConstants.NS_RESOURCES, Integer.toString(location.x - parentBounds.x) + "dp");
            tag.setAttribute("layout_y", SdkConstants.NS_RESOURCES, Integer.toString(location.y - parentBounds.y) + "dp");
          }
        }
      }
    });
  }
}