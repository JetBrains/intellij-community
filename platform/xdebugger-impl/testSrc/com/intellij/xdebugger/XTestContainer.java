/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.SmartList;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Semaphore;

public class XTestContainer<T> {
  private final List<T> myChildren = new SmartList<T>();
  private String myErrorMessage;
  private final Semaphore myFinished = new Semaphore(0);

  public void addChildren(List<? extends T> children, boolean last) {
    myChildren.addAll(children);
    if (last) myFinished.release();
  }

  public void tooManyChildren(int remaining) {
    myFinished.release();
  }

  public void setErrorMessage(@NotNull String errorMessage, @Nullable XDebuggerTreeNodeHyperlink link) {
    setErrorMessage(errorMessage);
  }

  public void setErrorMessage(@NotNull String errorMessage) {
    myErrorMessage = errorMessage;
    myFinished.release();
  }

  public boolean isObsolete() {
    return false;
  }

  public Pair<List<T>, String> waitFor(long timeout) throws InterruptedException {
    XDebuggerTestUtil.waitFor(myFinished, timeout);
    return new Pair<List<T>, String>(myChildren, myErrorMessage);
  }
}
