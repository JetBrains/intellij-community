/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

public class SingleAlarm extends Alarm {
  private final Runnable task;
  private final int delay;

  public SingleAlarm(@NotNull Runnable task, int delay) {
    this.task = task;
    this.delay = delay;
  }

  public SingleAlarm(@NotNull Runnable task, int delay, @NotNull Disposable parentDisposable) {
    this(task, delay, Alarm.ThreadToUse.SWING_THREAD, parentDisposable);
  }

  public SingleAlarm(@NotNull Runnable task, int delay, @NotNull ThreadToUse threadToUse, @NotNull Disposable parentDisposable) {
    super(threadToUse, parentDisposable);

    this.task = task;
    this.delay = delay;
  }

  public void request() {
    request(false);
  }

  public void request(boolean forceRun) {
    if (isEmpty()) {
      addRequest(forceRun ? 0 : delay);
    }
  }

  public void cancel() {
    cancelAllRequests();
  }

  public void cancelAndRequest() {
    if (!isDisposed()) {
      cancel();
      addRequest(delay);
    }
  }

  private void addRequest(int delay) {
    _addRequest(task, delay, myThreadToUse == ThreadToUse.SWING_THREAD ? ModalityState.NON_MODAL : null);
  }
}