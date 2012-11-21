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
package com.intellij.android.designer.designSurface.layout.actions;

import com.android.SdkConstants;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;

import java.awt.*;
import java.text.DecimalFormat;

/**
 * @author Alexander Lobas
 */
public class LayoutWeightOperation extends LayoutMarginOperation {
  public static final String TYPE = "layout_weight";

  private float myWeight;

  public LayoutWeightOperation(OperationContext context) {
    super(context);
  }

  @Override
  public void setComponent(RadComponent component) {
    super.setComponent(component);

    try {
      myWeight = Float.parseFloat(myComponent.getTag().getAttributeValue("layout_weight", SdkConstants.NS_RESOURCES));
    }
    catch (Throwable e) {
      myWeight = 0;
    }
  }

  @Override
  protected Color getFeedbackColor() {
    return Color.cyan;
  }

  @Override
  protected void fillTextFeedback() {
    Dimension sizeDelta = myContext.getSizeDelta();
    int direction = myContext.getResizeDirection();

    if (direction == Position.EAST) { // right
      myTextFeedback.append(getWeight(sizeDelta.width));
    }
    else if (direction == Position.SOUTH) { // bottom
      myTextFeedback.append(getWeight(sizeDelta.height));
    }
  }

  private static final DecimalFormat FORMAT = new DecimalFormat("#.##");

  private String getWeight(int value) {
    double weight = myWeight + value / 100.0;
    if (weight <= 0) {
      return "0";
    }
    return FORMAT.format(weight);
  }

  @Override
  public void execute() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Dimension sizeDelta = myContext.getSizeDelta();
        int direction = myContext.getResizeDirection();

        if (direction == Position.EAST) { // right
          setWeight(sizeDelta.width);
        }
        else if (direction == Position.SOUTH) { // bottom
          setWeight(sizeDelta.height);
        }
      }
    });
  }

  private void setWeight(int value) {
    float weight = myWeight + value / 100f;
    if (weight <= 0) {
      ModelParser.deleteAttribute(myComponent, "layout_weight");
    }
    else {
      myComponent.getTag().setAttribute("layout_weight", SdkConstants.NS_RESOURCES, FORMAT.format(weight).replace(',', '.'));
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void point(ResizeSelectionDecorator decorator) {
    pointFeedback(decorator);
    pointRight(decorator, Color.cyan, 0.75, TYPE, "Change layout:weight");
    pointBottom(decorator, Color.cyan, 0.75, TYPE, "Change layout:weight");
  }
}