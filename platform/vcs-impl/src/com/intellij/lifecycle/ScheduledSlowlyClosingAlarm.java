/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lifecycle;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ScheduledSlowlyClosingAlarm extends SlowlyClosingAlarm {
  ScheduledSlowlyClosingAlarm(@NotNull Project project, @NotNull String name, ControlledAlarmFactory.MyExecutorWrapper executor, boolean executorIsShared) {
    super(project, name, executor, executorIsShared);
  }

  public void addRequest(final Runnable runnable, final int delayMillis) {
    if (myExecutorService.supportsScheduling()) {
      synchronized (myLock) {
        if (myDisposeStarted) return;
        final MyWrapper wrapper = new MyWrapper(runnable);
        final Future<?> future = myExecutorService.schedule(wrapper, delayMillis, TimeUnit.MILLISECONDS);
        wrapper.setFuture(future);
        myFutureList.add(future);
        debug("request scheduled");
      }
    } else {
      addRequest(runnable);
    }
  }
}
