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
package com.intellij.android.designer.designSurface.layout.caption;

import com.intellij.android.designer.designSurface.layout.AbstractFlowBaseOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.LineInsertFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class HorizontalCaptionFlowBaseOperation<T extends RadViewComponent> extends AbstractFlowBaseOperation {
  protected final T myMainContainer;
  private final EditableArea myMainArea;
  private LineInsertFeedback myMainInsertFeedback;
  private int myMainYLocation;

  public HorizontalCaptionFlowBaseOperation(T mainContainer,
                                            RadComponent container,
                                            OperationContext context,
                                            EditableArea mainArea) {
    super(container, context, true);
    myMainContainer = mainContainer;
    myMainArea = mainArea;
  }

  @Override
  protected void createFeedback() {
    super.createFeedback();

    if (myMainInsertFeedback == null) {
      FeedbackLayer layer = myMainArea.getFeedbackLayer();

      Rectangle bounds = myMainContainer.getBounds(layer);
      myMainYLocation = bounds.y;

      myMainInsertFeedback = new LineInsertFeedback(JBColor.GREEN, false);
      myMainInsertFeedback.size(0, getMainFeedbackHeight(layer, myMainYLocation));

      layer.add(myMainInsertFeedback);
      layer.repaint();
    }
  }

  protected int getMainFeedbackHeight(FeedbackLayer layer, int mainYLocation) {
    List<RadComponent> children = myMainContainer.getChildren();
    Rectangle lastChildBounds = children.get(children.size() - 1).getBounds(layer);
    return lastChildBounds.y + lastChildBounds.height - mainYLocation;
  }

  @Override
  public void showFeedback() {
    super.showFeedback();
    Point location = SwingUtilities.convertPoint(myInsertFeedback.getParent(),
                                                 myInsertFeedback.getLocation(),
                                                 myMainArea.getFeedbackLayer());
    myMainInsertFeedback.setLocation(location.x, myMainYLocation);
  }

  @Override
  public void eraseFeedback() {
    super.eraseFeedback();
    if (myMainInsertFeedback != null) {
      FeedbackLayer layer = myMainArea.getFeedbackLayer();
      layer.remove(myMainInsertFeedback);
      layer.repaint();
      myMainInsertFeedback = null;
    }
  }
}