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
package com.intellij.android.designer.designSurface.layout.relative;

import com.android.SdkConstants;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.componentTree.AttributeWrapper;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ComponentSnapPoint extends SnapPoint {
  private Side myBeginSide;
  private Side myEndSide;

  public ComponentSnapPoint(RadViewComponent component, boolean horizontal) {
    super(component, horizontal);
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    feedback.append(myBeginSide.name(), SnapPointFeedbackHost.SNAP_ATTRIBUTES);
    feedback.append(" to ");
    feedback.append(myEndSide.name(), SnapPointFeedbackHost.SNAP_ATTRIBUTES);
    feedback.append(" of ");
    getComponentDecorator().decorate(myComponent, feedback, AttributeWrapper.DEFAULT, false);
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, feedback);

    myEndSide = myBeginSide = null;

    if (myHorizontal) {
      return processLeft(bounds, feedback) || processRight(bounds, feedback);
    }

    return processTop(bounds, feedback) || processBottom(bounds, feedback);
  }

  private boolean processLeft(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startX = myBounds.x - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    return processLeftLeft(startX, endX, targetBounds, feedback) || processLeftRight(startX, endX, targetBounds, feedback);
  }

  protected boolean processLeftLeft(int startX, int endX, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int targetX = targetBounds.x;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x;
      addVerticalFeedback(feedback, myBounds, targetBounds, true);
      side(Side.left, Side.left);
      return true;
    }

    return false;
  }

  protected boolean processLeftRight(int startX, int endX, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int targetX = targetBounds.x + targetBounds.width;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x - targetBounds.width;
      addVerticalFeedback(feedback, myBounds, targetBounds, true);
      side(Side.left, Side.right);
      return true;
    }

    return false;
  }

  private boolean processRight(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startX = myBounds.x + myBounds.width - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    return processRightLeft(startX, endX, targetBounds, feedback) || processRightRight(startX, endX, targetBounds, feedback);
  }

  protected boolean processRightLeft(int startX, int endX, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int targetX = targetBounds.x;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x + myBounds.width;
      addVerticalFeedback(feedback, myBounds, targetBounds, false);
      side(Side.right, Side.left);
      return true;
    }

    return false;
  }

  protected boolean processRightRight(int startX, int endX, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int targetX = targetBounds.x + targetBounds.width;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x + myBounds.width - targetBounds.width;
      addVerticalFeedback(feedback, myBounds, targetBounds, false);
      side(Side.right, Side.right);
      return true;
    }

    return false;
  }

  private boolean processTop(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startY = myBounds.y - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    return processTopTop(startY, endY, targetBounds, feedback) || processTopBottom(startY, endY, targetBounds, feedback);
  }

  protected boolean processTopTop(int startY, int endY, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int targetY = targetBounds.y;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y;
      addHorizontalFeedback(feedback, myBounds, targetBounds, true);
      side(Side.top, Side.top);
      return true;
    }

    return false;
  }

  protected boolean processTopBottom(int startY, int endY, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int targetY = targetBounds.y + targetBounds.height;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y - targetBounds.height;
      addHorizontalFeedback(feedback, myBounds, targetBounds, true);
      side(Side.top, Side.bottom);
      return true;
    }

    return false;
  }

  private boolean processBottom(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startY = myBounds.y + myBounds.height - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    return processBottomTop(startY, endY, targetBounds, feedback) || processBottomBottom(startY, endY, targetBounds, feedback);
  }

  protected boolean processBottomTop(int startY, int endY, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int targetY = targetBounds.y;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y + myBounds.height;
      addHorizontalFeedback(feedback, myBounds, targetBounds, false);
      side(Side.bottom, Side.top);
      return true;
    }

    return false;
  }

  protected boolean processBottomBottom(int startY, int endY, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int targetY = targetBounds.y + targetBounds.height;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y + myBounds.height - targetBounds.height;
      addHorizontalFeedback(feedback, myBounds, targetBounds, false);
      side(Side.bottom, Side.bottom);
      return true;
    }

    return false;
  }

  private void side(Side end, Side begin) {
    myEndSide = end;
    myBeginSide = begin;
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    final String attribute;

    if (myBeginSide == Side.top && myEndSide == Side.top) {
      attribute = "layout_alignTop";
    }
    else if (myBeginSide == Side.bottom && myEndSide == Side.bottom) {
      attribute = "layout_alignBottom";
    }
    else if (myBeginSide == Side.top && myEndSide == Side.bottom) {
      attribute = "layout_below";
    }
    else if (myBeginSide == Side.bottom && myEndSide == Side.top) {
      attribute = "layout_above";
    }
    else if (myBeginSide == Side.left && myEndSide == Side.left) {
      attribute = "layout_alignLeft";
    }
    else if (myBeginSide == Side.right && myEndSide == Side.right) {
      attribute = "layout_alignRight";
    }
    else if (myBeginSide == Side.left && myEndSide == Side.right) {
      attribute = "layout_toRightOf";
    }
    else if (myBeginSide == Side.right && myEndSide == Side.left) {
      attribute = "layout_toLeftOf";
    }
    else {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final String componentId = myComponent.ensureId();

        for (RadComponent component : components) {
          XmlTag tag = ((RadViewComponent)component).getTag();
          tag.setAttribute(attribute, SdkConstants.NS_RESOURCES, componentId);
        }
      }
    });
  }
}