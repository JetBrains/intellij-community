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
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ContainerSnapPoint extends SnapPoint {
  private Side mySide;

  public ContainerSnapPoint(RadViewComponent container, boolean horizontal) {
    super(container, horizontal);
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    String attribute = getAttribute();
    if (attribute != null) {
      feedback.append(attribute, SnapPointFeedbackHost.SNAP_ATTRIBUTES);
    }
  }

  @Nullable
  private String getAttribute() {
    if (mySide == Side.top) {
      return "alignParentTop";
    }
    else if (mySide == Side.bottom) {
      return "alignParentBottom";
    }
    else if (mySide == Side.left) {
      return "alignParentLeft";
    }
    else if (mySide == Side.right) {
      return "alignParentRight";
    }
    else if (mySide == Side.center_horizontal) {
      return "centerHorizontal";
    }
    else if (mySide == Side.center_vertical) {
      return "centerVertical";
    }
    return null;
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, feedback);

    mySide = null;

    return processBounds(bounds, feedback);
  }

  protected boolean processBounds(Rectangle bounds, SnapPointFeedbackHost feedback) {
    if (myHorizontal) {
      return processLeft(bounds, feedback) || processRight(bounds, feedback) || processHorizontalCenter(bounds, feedback);
    }

    return processTop(bounds, feedback) || processBottom(bounds, feedback) || processVerticalCenter(bounds, feedback);
  }

  protected final boolean processLeft(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startX = myBounds.x - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    int targetX = targetBounds.x;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x;
      feedback.addVerticalLine(myBounds.x, myBounds.y, myBounds.height);
      mySide = Side.left;
      return true;
    }

    return false;
  }

  protected final boolean processRight(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startX = myBounds.x + myBounds.width - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    int targetX = targetBounds.x + targetBounds.width;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x + myBounds.width - targetBounds.width;
      feedback.addVerticalLine(myBounds.x + myBounds.width, myBounds.y, myBounds.height);
      mySide = Side.right;
      return true;
    }

    return false;
  }

  protected final boolean processTop(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startY = myBounds.y - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    int targetY = targetBounds.y;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y;
      feedback.addHorizontalLine(myBounds.x, myBounds.y, myBounds.width);
      mySide = Side.top;
      return true;
    }

    return false;
  }

  protected final boolean processBottom(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startY = myBounds.y + myBounds.height - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    int targetY = targetBounds.y + targetBounds.height;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y + myBounds.height - targetBounds.height;
      feedback.addHorizontalLine(myBounds.x, myBounds.y + myBounds.height, myBounds.width);
      mySide = Side.bottom;
      return true;
    }

    return false;
  }

  private boolean processHorizontalCenter(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int centerX = myBounds.x + myBounds.width / 2;

    int startX = centerX - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    int targetX = targetBounds.x + targetBounds.width / 2;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = centerX - targetBounds.width / 2;
      feedback.addVerticalLine(centerX - 1, myBounds.y, myBounds.height);
      mySide = Side.center_horizontal;
      return true;
    }

    return false;
  }

  private boolean processVerticalCenter(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int centerY = myBounds.y + myBounds.height / 2;

    int startY = centerY - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    int targetY = targetBounds.y + targetBounds.height / 2;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = centerY - targetBounds.height / 2;
      feedback.addHorizontalLine(myBounds.x, centerY - 1, myBounds.width);
      mySide = Side.center_vertical;
      return true;
    }

    return false;
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String attribute = getAttribute();
        if (attribute != null) {
          for (RadComponent component : components) {
            XmlTag tag = ((RadViewComponent)component).getTag();
            tag.setAttribute("layout_" + attribute, SdkConstants.NS_RESOURCES, "true");
          }
        }
      }
    });
  }
}