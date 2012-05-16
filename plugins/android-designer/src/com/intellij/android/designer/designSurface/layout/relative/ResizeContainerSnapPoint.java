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
public class ResizeContainerSnapPoint extends ResizeSnapPoint {
  private Side mySide;
  private ContainerSnapPoint mySnapPoint;

  public ResizeContainerSnapPoint(RadViewComponent container, boolean horizontal) {
    super(container, horizontal);
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    mySnapPoint.addTextInfo(feedback);
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, Side resizeSide, SnapPointFeedbackHost feedback) {
    mySide = resizeSide;

    if (mySnapPoint == null) {
      mySnapPoint = new ContainerSnapPoint(myComponent, myHorizontal) {
        @Override
        protected boolean processBounds(Rectangle bounds, SnapPointFeedbackHost feedback) {
          if (myHorizontal) {
            return mySide == Side.left ? processLeft(bounds, feedback) : processRight(bounds, feedback);
          }
          return mySide == Side.top ? processTop(bounds, feedback) : processBottom(bounds, feedback);
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
          ModelParser.deleteAttribute(tag, "android:layout_alignTop");
          ModelParser.deleteAttribute(tag, "android:layout_below");
          ModelParser.deleteAttribute(tag, "android:layout_marginTop");
        }
        else if (mySide == Side.bottom) {
          ModelParser.deleteAttribute(tag, "android:layout_alignBottom");
          ModelParser.deleteAttribute(tag, "android:layout_above");
          ModelParser.deleteAttribute(tag, "android:layout_marginBottom");
        }
        else if (mySide == Side.left) {
          ModelParser.deleteAttribute(tag, "android:layout_alignLeft");
          ModelParser.deleteAttribute(tag, "android:layout_toRightOf");
          ModelParser.deleteAttribute(tag, "android:layout_marginLeft");
        }
        else if (mySide == Side.right) {
          ModelParser.deleteAttribute(tag, "android:layout_alignRight");
          ModelParser.deleteAttribute(tag, "android:layout_toLeftOf");
          ModelParser.deleteAttribute(tag, "android:layout_marginRight");
        }
      }
    });
  }
}