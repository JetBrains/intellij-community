package com.intellij.xdebugger;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.frame.XValue;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Semaphore;

import static org.junit.Assert.*;

public class XTestEvaluationCallback extends com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase {
  private XValue myResult;
  private String myErrorMessage;
  private final Semaphore myFinished = new Semaphore(0);

  @Override
  public void evaluated(@NotNull XValue result) {
    myResult = result;
    myFinished.release();
  }

  @Override
  public void errorOccurred(@NotNull String errorMessage) {
    myErrorMessage = errorMessage;
    myFinished.release();
  }

  public Pair<XValue, String> waitFor(long timeoutInMilliseconds) {
    assertTrue("timed out", XDebuggerTestUtil.waitFor(myFinished, timeoutInMilliseconds));
    return Pair.create(myResult, myErrorMessage);
  }
}
