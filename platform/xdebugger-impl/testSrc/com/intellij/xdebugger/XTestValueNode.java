package com.intellij.xdebugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValuePresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Semaphore;

public class XTestValueNode extends XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl {
  public String myName;
  public String myType;
  public String myValue;
  public boolean myHasChildren;

  public XFullValueEvaluator myFullValueEvaluator;

  private final Semaphore myFinished = new Semaphore(0);

  private final AsyncResult<XTestValueNode> result = new AsyncResult<XTestValueNode>();

  @Override
  public void applyPresentation(@Nullable Icon icon,
                                @NotNull XValuePresentation valuePresentation,
                                boolean hasChildren) {
    myType = valuePresentation.getType();
    myValue = XValuePresentationUtil.computeValueText(valuePresentation);
    myHasChildren = hasChildren;

    myFinished.release();
    result.setDone(this);
  }

  @Override
  public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
    myFullValueEvaluator = fullValueEvaluator;
  }

  @Override
  public boolean isObsolete() {
    return false;
  }

  @NotNull
  public AsyncResult<XTestValueNode> getResult() {
    return result;
  }

  public void waitFor(long timeoutInMillis) throws InterruptedException {
    if (!XDebuggerTestUtil.waitFor(myFinished, timeoutInMillis)) {
      throw new AssertionError("Waiting timed out");
    }
  }
}
