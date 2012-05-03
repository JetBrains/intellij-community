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

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class SnapPoint {
  protected static final int SNAP_SIZE = 5;
  protected static final int SNAP_LINE_BORDER = 15;

  protected final RadViewComponent myComponent;
  protected final boolean myHorizontal;
  protected Rectangle myBounds;

  protected SnapPoint(RadViewComponent component, boolean horizontal) {
    myComponent = component;
    myHorizontal = horizontal;
  }

  public abstract void addTextInfo(TextFeedback feedback);

  protected final TreeComponentDecorator getComponentDecorator() {
    return myComponent.getRoot().getClientProperty(TreeComponentDecorator.KEY);
  }

  public boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    if (myBounds == null) {
      myBounds = myComponent.getBounds(feedback.getParent());
    }
    return false;
  }

  protected static void addHorizontalFeedback(SnapPointFeedbackHost feedback, Rectangle bounds, Rectangle target, boolean top) {
    addHorizontalFeedback(feedback, bounds, target, top ? bounds.y : bounds.y + bounds.height);
  }

  protected static void addHorizontalFeedback(SnapPointFeedbackHost feedback, Rectangle bounds, Rectangle target, int y) {
    if (bounds.x < target.x) {
      feedback.addHorizontalLine(bounds.x - SNAP_LINE_BORDER, y,
                                 target.x + target.width - bounds.x + 2 * SNAP_LINE_BORDER);
    }
    else {
      feedback.addHorizontalLine(target.x - SNAP_LINE_BORDER, y,
                                 bounds.x + bounds.width - target.x + 2 * SNAP_LINE_BORDER);
    }
  }

  protected static void addVerticalFeedback(SnapPointFeedbackHost feedback, Rectangle bounds, Rectangle target, boolean left) {
    if (bounds.y < target.y) {
      feedback.addVerticalLine(left ? bounds.x : bounds.x + bounds.width, bounds.y - SNAP_LINE_BORDER,
                               target.y + target.height - bounds.y + 2 * SNAP_LINE_BORDER);
    }
    else {
      feedback.addVerticalLine(left ? bounds.x : bounds.x + bounds.width, target.y - SNAP_LINE_BORDER,
                               bounds.y + bounds.height - target.y + 2 * SNAP_LINE_BORDER);
    }
  }

  protected final String getComponentId() {
    // TODO: ensure id
    String idValue = myComponent.getTag().getAttributeValue("android:id");
    return "@id/" + idValue.substring(idValue.indexOf('/') + 1);
  }

  public abstract void execute(List<RadComponent> components) throws Exception;
}