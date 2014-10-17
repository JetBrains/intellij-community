/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.Stack;

public class HeavyProcessLatch {
  public static final HeavyProcessLatch INSTANCE = new HeavyProcessLatch();

  private final Stack<String> myHeavyProcesses = new Stack<String>();
  private final EventDispatcher<HeavyProcessListener> myEventDispatcher = EventDispatcher.create(HeavyProcessListener.class);

  private HeavyProcessLatch() {
  }

  /**
   * @deprecated use {@link #processStarted(java.lang.String)} instead
   */
  @Deprecated
  public void processStarted() {
    processStarted("");
  }

  public void processStarted(@NotNull String operationName) {
    myHeavyProcesses.push(operationName);
    myEventDispatcher.getMulticaster().processStarted();
  }

  public void processFinished() {
    myHeavyProcesses.pop();
    myEventDispatcher.getMulticaster().processFinished();
  }

  public boolean isRunning() {
    return !myHeavyProcesses.isEmpty();
  }

  public String getRunningOperationName() {
    synchronized (myHeavyProcesses) {
      return myHeavyProcesses.isEmpty() ? null : myHeavyProcesses.peek();
    }
  }


  public interface HeavyProcessListener extends EventListener {
    public void processStarted();

    public void processFinished();
  }

  public void addListener(@NotNull Disposable parentDisposable,
                          @NotNull HeavyProcessListener listener) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }
}