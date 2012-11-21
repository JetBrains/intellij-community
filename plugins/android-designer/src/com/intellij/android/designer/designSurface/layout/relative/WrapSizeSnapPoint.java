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
public class WrapSizeSnapPoint extends ResizeSnapPoint {
  private final Dimension myWrapSize;

  public WrapSizeSnapPoint(RadViewComponent component, boolean horizontal, Dimension wrapSize) {
    super(component, horizontal);
    myWrapSize = wrapSize;
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    feedback.append("layout:" + (myHorizontal ? "width " : "height "));
    feedback.snap("wrap_content");
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, Side resizeSide, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, resizeSide, feedback);

    if (myHorizontal) {
      if (Math.abs(bounds.width - myWrapSize.width) < SNAP_SIZE) {
        bounds.width = myWrapSize.width;
        return true;
      }
    }
    else {
      if (Math.abs(bounds.height - myWrapSize.height) < SNAP_SIZE) {
        bounds.height = myWrapSize.height;
        return true;
      }
    }

    return false;
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)components.get(0)).getTag();
        ModelParser.deleteAttribute(tag, "layout_alignParent" + (myHorizontal ? "Right" : "Bottom"));
        tag.setAttribute("layout_" + (myHorizontal ? "width" : "height"), SdkConstants.NS_RESOURCES, "wrap_content");
      }
    });
  }
}