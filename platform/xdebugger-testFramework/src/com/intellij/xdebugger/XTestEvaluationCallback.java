/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertTrue;

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
    assertTrue("timed out", XDebuggerTestUtil.waitFor(myFinished, timeoutInMilliseconds));
    return Pair.create(myResult, myErrorMessage);
  }
}
