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

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class AutoSnapPoint extends SnapPoint {
  private int myMargin;

  public AutoSnapPoint(RadViewComponent container, boolean horizontal) {
    super(container, horizontal);
  }

  private String getAttribute() {
    return myHorizontal ? "alignParentLeft" : "alignParentTop";
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    feedback.append(getAttribute(), SnapPointFeedbackHost.SNAP_ATTRIBUTES);
    if (myMargin > 0) {
      feedback.append(", margin" + (myHorizontal ? "Left " : "Top ") + Integer.toString(myMargin));
      feedback.dimension("dp");
    }
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, feedback);

    if (myHorizontal) {
      myMargin = bounds.x - myBounds.x;
      feedback.addVerticalLine(myBounds.x, myBounds.y, myBounds.height);
      feedback.addHorizontalArrow(myBounds.x, bounds.y + bounds.height / 2, myMargin);
    }
    else {
      myMargin = bounds.y - myBounds.y;
      feedback.addHorizontalLine(myBounds.x, myBounds.y, myBounds.width);
      feedback.addVerticalArrow(bounds.x + bounds.width / 2, myBounds.y, myMargin);
    }

    return true;
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String attribute = "layout_" + getAttribute();
        String marginAttribute = null;
        String marginValue = null;

        if (myMargin > 0) {
          marginAttribute = myHorizontal ? "layout_marginLeft" : "layout_marginTop";
          marginValue = Integer.toString(myMargin) + "dp";
        }

        for (RadComponent component : components) {
          XmlTag tag = ((RadViewComponent)component).getTag();
          tag.setAttribute(attribute, SdkConstants.NS_RESOURCES, "true");
          if (marginValue != null) {
            tag.setAttribute(marginAttribute, SdkConstants.NS_RESOURCES, marginValue);
          }
        }
      }
    });
  }
}