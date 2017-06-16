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

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class HeavyAwareExecutor {
  public static void executeOutOfHeavyProcessLater(@NotNull Runnable command, int delayMs) {
    HeavyProcessLatch.INSTANCE.executeOutOfHeavyProcess(() -> JobScheduler.getScheduler().schedule(() -> {
      if (HeavyProcessLatch.INSTANCE.isRunning()) {
        executeOutOfHeavyProcessLater(command, delayMs);
      }
      else {
        command.run();
      }
    }, delayMs, TimeUnit.MILLISECONDS));
  }

  public static void executeOutOfHeavyProcess(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator indicator) {
    HeavyProcessLatch.INSTANCE.executeOutOfHeavyProcess(() -> {
      if (HeavyProcessLatch.INSTANCE.isRunning()) {
        executeOutOfHeavyProcess(task, indicator);
      }
      else {
        Disposable disposable = Disposer.newDisposable();
        ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task, indicator,
                                                                                                  () -> Disposer.dispose(disposable));
        HeavyProcessLatch.INSTANCE.addListener(new CancellingOnHeavyProcessListener(indicator), disposable);
      }
    });
  }

  private static class CancellingOnHeavyProcessListener implements HeavyProcessLatch.HeavyProcessListener {
    @NotNull private final ProgressIndicator myIndicator;

    public CancellingOnHeavyProcessListener(@NotNull ProgressIndicator indicator) {
      myIndicator = indicator;
    }

    @Override
    public void processStarted() {
      myIndicator.cancel();
    }

    @Override
    public void processFinished() {
    }
  }
}
