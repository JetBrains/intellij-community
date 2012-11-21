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
public class BaselineSnapPoint extends SnapPoint {
  private final int myBaseline;

  public BaselineSnapPoint(RadViewComponent component) {
    super(component, false);
    myBaseline = component.getBaseline();
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    feedback.append("baseline", SnapPointFeedbackHost.SNAP_ATTRIBUTES);
    feedback.append(" to ");
    getComponentDecorator().decorate(myComponent, feedback, AttributeWrapper.DEFAULT, false);
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    if (myBaseline == -1 || components.size() != 1) {
      return false;
    }

    int targetBaseLine = ((RadViewComponent)components.get(0)).getBaseline();
    if (targetBaseLine == -1) {
      return false;
    }

    super.processBounds(components, targetBounds, feedback);

    int startY = myBounds.y + myBaseline - SNAP_SIZE;
    int endY = startY + 2 * SNAP_SIZE;

    int targetY = targetBounds.y + targetBaseLine;
    if (startY <= targetY && targetY <= endY) {
      targetBounds.y = myBounds.y + myBaseline - targetBaseLine;
      addHorizontalFeedback(feedback, myBounds, targetBounds, myBounds.y + myBaseline);
      return true;
    }

    return false;
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)components.get(0)).getTag();
        tag.setAttribute("layout_alignBaseline", SdkConstants.NS_RESOURCES, myComponent.ensureId());
      }
    });
  }
}