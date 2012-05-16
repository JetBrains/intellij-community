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

import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class AutoResizeSnapPoint extends SnapPoint {
  private final int myDirection;
  private int myMargin;
  private int mySize;

  public AutoResizeSnapPoint(RadViewComponent container, boolean horizontal, int direction) {
    super(container, horizontal);
    myDirection = direction;
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    if (myMargin > 0) {
      feedback.append("alignParent" + (myHorizontal ? "Left " : "Top "), SnapPointFeedbackHost.SNAP_ATTRIBUTES);
      feedback.append(", margin" + (myHorizontal ? "Left " : "Top ") + Integer.toString(myMargin));
      feedback.dimension("dp");
      feedback.append(", ");
    }

    if ((myDirection & Position.EAST_WEST) != 0) {
      feedback.append("layout:width ");
    }
    else {
      feedback.append("layout:height ");
    }

    feedback.append(Integer.toString(mySize));
    feedback.dimension("dp");
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, feedback);

    if (myHorizontal) {
      mySize = bounds.width;

      if ((myDirection & Position.WEST) != 0) {
        myMargin = bounds.x - myBounds.x;
        feedback.addVerticalLine(myBounds.x, myBounds.y, myBounds.height);
        feedback.addHorizontalArrow(myBounds.x, bounds.y + bounds.height / 2, myMargin);
      }
    }
    else {
      mySize = bounds.height;

      if ((myDirection & Position.NORTH) != 0) {
        myMargin = bounds.y - myBounds.y;
        feedback.addHorizontalLine(myBounds.x, myBounds.y, myBounds.width);
        feedback.addVerticalArrow(bounds.x + bounds.width / 2, myBounds.y, myMargin);
      }
    }

    return true;
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)components.get(0)).getTag();
        if (myMargin > 0) {
          tag.setAttribute("android:layout_alignParent" + (myHorizontal ? "Left" : "Top"), "true");
          tag.setAttribute("android:layout_margin" + (myHorizontal ? "Left" : "Top"), Integer.toString(myMargin) + "dp");
        }
        else {
          ModelParser.deleteAttribute(tag, "android:layout_alignParent" + (myHorizontal ? "Right" : "Bottom"));
        }
        tag.setAttribute("android:layout_" + (myHorizontal ? "width" : "height"), Integer.toString(mySize) + "dp");
      }
    });
  }
}