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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeComponentSnapPoint extends ResizeSnapPoint {
  private Side mySide;
  private ComponentSnapPoint mySnapPoint;

  public ResizeComponentSnapPoint(RadViewComponent component, boolean horizontal) {
    super(component, horizontal);
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    mySnapPoint.addTextInfo(feedback);
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, Side resizeSide, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, resizeSide, feedback);

    mySide = resizeSide;

    if (mySnapPoint == null) {
      mySnapPoint = new ComponentSnapPoint(myComponent, myHorizontal) {
        @Override
        protected boolean processLeftLeft(int startX, int endX, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
          return mySide == Side.left && super.processLeftLeft(startX, endX, targetBounds, feedback);
        }

        @Override
        protected boolean processLeftRight(int startX, int endX, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
          return mySide == Side.right && super.processLeftRight(startX, endX, targetBounds, feedback);
        }

        @Override
        protected boolean processRightLeft(int startX, int endX, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
          return mySide == Side.left && super.processRightLeft(startX, endX, targetBounds, feedback);
        }

        @Override
        protected boolean processRightRight(int startX, int endX, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
          return mySide == Side.right && super.processRightRight(startX, endX, targetBounds, feedback);
        }

        @Override
        protected boolean processTopTop(int startY, int endY, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
          return mySide == Side.top && super.processTopTop(startY, endY, targetBounds, feedback);
        }

        @Override
        protected boolean processTopBottom(int startY, int endY, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
          return mySide == Side.bottom && super.processTopBottom(startY, endY, targetBounds, feedback);
        }

        @Override
        protected boolean processBottomTop(int startY, int endY, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
          return mySide == Side.top && super.processBottomTop(startY, endY, targetBounds, feedback);
        }

        @Override
        protected boolean processBottomBottom(int startY, int endY, Rectangle targetBounds, SnapPointFeedbackHost feedback) {
          return mySide == Side.bottom && super.processBottomBottom(startY, endY, targetBounds, feedback);
        }
      };
    }

    return mySnapPoint.processBounds(components, bounds, feedback);
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    mySnapPoint.execute(components);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)components.get(0)).getTag();

        if (mySide == Side.top) {
          ModelParser.deleteAttribute(tag, "android:layout_alignParentTop");
          ModelParser.deleteAttribute(tag, "android:layout_marginTop");
        }
        else if (mySide == Side.bottom) {
          ModelParser.deleteAttribute(tag, "android:layout_alignParentBottom");
          ModelParser.deleteAttribute(tag, "android:layout_marginBottom");
        }
        else if (mySide == Side.left) {
          ModelParser.deleteAttribute(tag, "android:layout_alignParentLeft");
          ModelParser.deleteAttribute(tag, "android:layout_marginLeft");
        }
        else if (mySide == Side.right) {
          ModelParser.deleteAttribute(tag, "android:layout_alignParentRight");
          ModelParser.deleteAttribute(tag, "android:layout_marginRight");
        }
      }
    });
  }
}