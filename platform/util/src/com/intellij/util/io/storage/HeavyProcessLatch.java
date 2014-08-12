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
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.concurrent.atomic.AtomicInteger;

public class HeavyProcessLatch {
  public static final HeavyProcessLatch INSTANCE = new HeavyProcessLatch();

  private final AtomicInteger myHeavyProcessCounter = new AtomicInteger();
  private final EventDispatcher<HeavyProcessListener> myEventDispatcher = EventDispatcher.create(HeavyProcessListener.class);

  private HeavyProcessLatch() {
  }

  public void processStarted() {
    myHeavyProcessCounter.incrementAndGet();
    myEventDispatcher.getMulticaster().processStarted();
  }

  public void processFinished() {
    myHeavyProcessCounter.decrementAndGet();
    myEventDispatcher.getMulticaster().processFinished();
  }

  public boolean isRunning() {
    return myHeavyProcessCounter.get() != 0;
  }

  public interface HeavyProcessListener extends EventListener {
    public void processStarted();

    public void processFinished();
  }

  @NotNull
  public Disposable addListener(@NotNull HeavyProcessListener listener) {
    Disposable disposable = Disposer.newDisposable();
    myEventDispatcher.addListener(listener, disposable);
    return disposable;
  }
}