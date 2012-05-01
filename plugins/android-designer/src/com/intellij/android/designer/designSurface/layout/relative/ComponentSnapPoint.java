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
public class ComponentSnapPoint extends SnapPoint {
  public ComponentSnapPoint(RadComponent component, boolean horizontal) {
    super(component, horizontal);
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, feedback);

    if (myHorizontal) {
      return processLeft(bounds, feedback) || processRight(bounds, feedback);
    }

    return processTop(bounds, feedback) || processBottom(bounds, feedback);
  }

  private boolean processLeft(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startX = myBounds.x - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    int targetX = targetBounds.x;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x;
      addVerticalFeedback(feedback, myBounds, targetBounds, true);
      return true;
    }

    targetX = targetBounds.x + targetBounds.width;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x - targetBounds.width;
      addVerticalFeedback(feedback, myBounds, targetBounds, true);
      return true;
    }

    return false;
  }

  private boolean processRight(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startX = myBounds.x + myBounds.width - SNAP_SIZE;
    int endX = startX + 2 * SNAP_SIZE;

    int targetX = targetBounds.x;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x + myBounds.width;
      addVerticalFeedback(feedback, myBounds, targetBounds, false);
      return true;
    }

    targetX = targetBounds.x + targetBounds.width;
    if (startX <= targetX && targetX <= endX) {
      targetBounds.x = myBounds.x + myBounds.width - targetBounds.width;
      addVerticalFeedback(feedback, myBounds, targetBounds, false);
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
      addHorizontalFeedback(feedback, myBounds, targetBounds, true);
      return true;
    }

    targetY = targetBounds.y + targetBounds.height;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y - targetBounds.height;
      addHorizontalFeedback(feedback, myBounds, targetBounds, true);
      return true;
    }

    return false;
  }

  private boolean processBottom(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    int startY = myBounds.y + myBounds.height - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    int targetY = targetBounds.y;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y + myBounds.height;
      addHorizontalFeedback(feedback, myBounds, targetBounds, false);
      return true;
    }

    targetY = targetBounds.y + targetBounds.height;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y + myBounds.height - targetBounds.height;
      addHorizontalFeedback(feedback, myBounds, targetBounds, false);
      return true;
    }

    return false;
  }
}