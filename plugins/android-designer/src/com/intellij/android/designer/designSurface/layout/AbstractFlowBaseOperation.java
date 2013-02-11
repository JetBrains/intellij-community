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