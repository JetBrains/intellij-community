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
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeContainerSnapPoint extends ContainerSnapPoint {
  public ResizeContainerSnapPoint(RadViewComponent container, boolean horizontal) {
    super(container, horizontal);
  }

  @Override
  protected boolean processHorizontalCenter(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    return false;
  }

  @Override
  protected boolean processVerticalCenter(Rectangle targetBounds, SnapPointFeedbackHost feedback) {
    return false;
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    super.execute(components);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)components.get(0)).getTag();

        if (mySide == Side.top) {
          ModelParser.deleteAttribute(tag, "android:layout_alignTop");
          ModelParser.deleteAttribute(tag, "android:layout_below");
        }
        else if (mySide == Side.left) {
          ModelParser.deleteAttribute(tag, "android:layout_alignBottom");
          ModelParser.deleteAttribute(tag, "android:layout_above");
        }
        else if (mySide == Side.bottom) {
          ModelParser.deleteAttribute(tag, "android:layout_alignLeft");
          ModelParser.deleteAttribute(tag, "android:layout_toRightOf");
        }
        else if (mySide == Side.right) {
          ModelParser.deleteAttribute(tag, "android:layout_alignRight");
          ModelParser.deleteAttribute(tag, "android:layout_toLeftOf");
        }
      }
    });
  }
}