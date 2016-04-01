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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.SingleAlarm;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class HeavyAwareExecutor implements Disposable {
  private static final int DELAY_MILLIS = 5000;
  @NotNull private final AtomicBoolean myIsExecuted = new AtomicBoolean(false);

  public HeavyAwareExecutor(@NotNull Disposable parentDisposable) {
    Disposer.register(parentDisposable, this);
  }

  public void execute(@NotNull Runnable command) {
    SingleAlarm alarm = new SingleAlarm(new Runnable() {
      @Override
      public void run() {
        if (!HeavyProcessLatch.INSTANCE.isRunning()) {
          if (myIsExecuted.compareAndSet(false, true)) {
            Disposer.dispose(HeavyAwareExecutor.this);
            command.run();
          }
        }
      }
    }, DELAY_MILLIS, Alarm.ThreadToUse.SWING_THREAD, this);

    HeavyProcessLatch.INSTANCE.addListener(alarm, new HeavyProcessLatch.HeavyProcessListener() {
      @Override
      public void processStarted() {
        alarm.cancel();
      }

      @Override
      public void processFinished() {
        if (!HeavyProcessLatch.INSTANCE.isRunning()) {
          alarm.request();
        }
      }
    });

    if (!HeavyProcessLatch.INSTANCE.isRunning()) {
      alarm.request();
    }
  }

  @Override
  public void dispose() {
  }
}
