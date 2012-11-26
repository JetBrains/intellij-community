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
import com.intellij.android.designer.model.ModelParser;
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
public class AutoResizeSnapPoint extends ResizeSnapPoint {
  private int myMargin;
  private int mySize;
  private Side mySide;

  public AutoResizeSnapPoint(RadViewComponent container, boolean horizontal) {
    super(container, horizontal);
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    if (myMargin > 0) {
      feedback.append("alignParent" + (myHorizontal ? "Left " : "Top "), SnapPointFeedbackHost.SNAP_ATTRIBUTES);
      feedback.append(", margin" + (myHorizontal ? "Left " : "Top ") + Integer.toString(myMargin));
      feedback.dimension("dp");
      feedback.append(", ");
    }

    if (mySide == Side.left || mySide == Side.right) {
      feedback.append("layout:width ");
    }
    else {
      feedback.append("layout:height ");
    }

    feedback.append(Integer.toString(mySize));
    feedback.dimension("dp");
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, Side resizeSide, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, resizeSide, feedback);

    mySide = resizeSide;

    if (myHorizontal) {
      mySize = bounds.width;

      if (resizeSide == Side.left) {
        myMargin = bounds.x - myBounds.x;
        feedback.addVerticalLine(myBounds.x, myBounds.y, myBounds.height);
        feedback.addHorizontalArrow(myBounds.x, bounds.y + bounds.height / 2, myMargin);
      }
    }
    else {
      mySize = bounds.height;

      if (resizeSide == Side.top) {
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
          String attribute = myHorizontal ? "Left" : "Top";
          tag.setAttribute("layout_alignParent" + attribute, SdkConstants.NS_RESOURCES, "true");
          tag.setAttribute("layout_margin" + attribute, SdkConstants.NS_RESOURCES, Integer.toString(myMargin) + "dp");
          ModelParser.deleteAttribute(tag, "layout_align" + attribute);

          if (myHorizontal) {
            ModelParser.deleteAttribute(tag, "layout_toRightOf");
          }
          else {
            ModelParser.deleteAttribute(tag, "layout_below");
          }
        }
        else {
          String attribute = myHorizontal ? "Right" : "Bottom";
          ModelParser.deleteAttribute(tag, "layout_alignParent" + attribute);
          ModelParser.deleteAttribute(tag, "layout_align" + attribute);

          if (myHorizontal) {
            ModelParser.deleteAttribute(tag, "layout_toLeftOf");
          }
          else {
            ModelParser.deleteAttribute(tag, "layout_above");
          }
        }
        tag.setAttribute("layout_" + (myHorizontal ? "width" : "height"), SdkConstants.NS_RESOURCES, Integer.toString(mySize) + "dp");
      }
    });
  }
}