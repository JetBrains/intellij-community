// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;

public class XTestEvaluationCallback extends XEvaluationCallbackBase {
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
    return waitFor(timeoutInMilliseconds, XDebuggerTestHelper::waitFor);
  }

  public Pair<XValue, String> waitFor(long timeoutInMilliseconds, BiFunction<Semaphore, Long, Boolean> waitFunction) {
    assert(waitFunction.apply(myFinished, timeoutInMilliseconds));
    return Pair.create(myResult, myErrorMessage);
  }
}
