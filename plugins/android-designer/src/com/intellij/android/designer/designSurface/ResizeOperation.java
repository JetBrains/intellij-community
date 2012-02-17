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
package com.intellij.android.designer.designSurface;

import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.designSurface.feedbacks.AlphaComponent;
import com.intellij.designer.utils.Position;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeOperation implements EditOperation {
  private final OperationContext myContext;
  private RadComponent myComponent;
  private JComponent myFeedback;

  public ResizeOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = component;
  }

  @Override
  public void setComponents(List<RadComponent> component) {
  }

  @Override
  public void showFeedback() {
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

    if (myFeedback == null) {
      myFeedback = new AlphaComponent(Color.GREEN, Color.LIGHT_GRAY);
      layer.add(myFeedback);
    }

    Rectangle bounds = myComponent.getBounds();
    Point location = myComponent.convertPoint(bounds.x, bounds.y, layer);
    myFeedback.setBounds(myContext.getTransformedRectangle(location.x, location.y, bounds.width, bounds.height));
    layer.repaint();
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      layer.repaint();
      myFeedback = null;
    }
  }

  @Override
  public boolean canExecute() {
    return myContext.getResizeDirection() != Position.SOUTH;
  }

  @Override
  public void execute() throws Exception {
  }
}