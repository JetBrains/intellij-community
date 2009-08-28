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
