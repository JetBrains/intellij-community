/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.LineInsertFeedback;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractFlowBaseOperation extends com.intellij.designer.designSurface.AbstractFlowBaseOperation {
  public AbstractFlowBaseOperation(RadComponent container,
                                   OperationContext context, boolean horizontal) {
    super(container, context, horizontal);
  }

  @Override
  protected void createInsertFeedback() {
    myInsertFeedback = new LineInsertFeedback(Color.green, !myHorizontal);
    myInsertFeedback.size(myBounds.width, myBounds.height);
  }

  @Override
  protected void createFirstInsertFeedback() {
    myFirstInsertFeedback = new RectangleFeedback(Color.green, 2);
    myFirstInsertFeedback.setBounds(myBounds);
  }
}