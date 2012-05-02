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

import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ContainerSnapPoint extends SnapPoint {
  public ContainerSnapPoint(RadComponent container, boolean horizontal) {
    super(container, horizontal);
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, feedback);

    if (myHorizontal) {
      return processLeft(bounds, feedback) || processRight(bounds, feedback) || processHorizontalCenter(bounds, feedback);
    }

    return processTop(bounds, feedback) || processBottom(bounds, feedback) || processVerticalCenter(bounds, feedback);
  }

  private boolean processLeft(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startX = myBounds.x - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    int targetX = targetBounds.x;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x;
      feedback.addVerticalLine(myBounds.x, myBounds.y, myBounds.height);
      return true;
    }

    return false;
  }

  private boolean processRight(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startX = myBounds.x + myBounds.width - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    int targetX = targetBounds.x + targetBounds.width;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x + myBounds.width - targetBounds.width;
      feedback.addVerticalLine(myBounds.x + myBounds.width, myBounds.y, myBounds.height);
      return true;
    }

    return false;
  }

  private boolean processTop(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startY = myBounds.y - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    int targetY = targetBounds.y;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y;
      feedback.addHorizontalLine(myBounds.x, myBounds.y, myBounds.width);
      return true;
    }

    return false;
  }

  private boolean processBottom(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startY = myBounds.y + myBounds.height - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    int targetY = targetBounds.y + targetBounds.height;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y + myBounds.height - targetBounds.height;
      feedback.addHorizontalLine(myBounds.x, myBounds.y + myBounds.height, myBounds.width);
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
      return true;
    }

    return false;
  }
}