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
package com.intellij.android.designer.designSurface.layout;

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class FlowBaseOperation extends AbstractEditOperation {
  protected final boolean myHorizontal;
  protected RectangleFeedback myInsertFeedback;

  public FlowBaseOperation(RadViewComponent container, OperationContext context, boolean horizontal) {
    super(container, context);
    myHorizontal = horizontal;
  }

  @Override
  public void showFeedback() {
    if (myInsertFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      myInsertFeedback = new RectangleFeedback(Color.green, 2);
      myInsertFeedback.setBounds(myContainer.getBounds(layer));
      layer.add(myInsertFeedback);
      layer.repaint();
    }
    // TODO: Auto-generated method stub
  }

  @Override
  public void eraseFeedback() {
    if (myInsertFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myInsertFeedback);
      layer.repaint();
      myInsertFeedback = null;
    }
    // TODO: Auto-generated method stub
  }

  @Override
  public boolean canExecute() {
    return super.canExecute(); // TODO: Auto-generated method stub
  }
}