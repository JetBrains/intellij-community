// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PendingTasksRunner {
  private final List<Runnable> myPendingTasks = new ArrayList<>();
  private final AtomicBoolean myReady = new AtomicBoolean(false);
  private final Alarm myAlarm;

  public PendingTasksRunner(long awaitTimeout, @NotNull Project project) {
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    myAlarm.addRequest(() -> setReady(), awaitTimeout);
  }

  public void execute(@NotNull Runnable task) {
    if (myReady.get()) {
      task.run();
    }
    else {
      synchronized (myPendingTasks) {
        myPendingTasks.add(task);
      }
      if (myReady.get()) {
        runPendingTasks();
      }
    }
  }

  public void setReady() {
    if (myReady.compareAndSet(false, true)) {
      Disposer.dispose(myAlarm);
      runPendingTasks();
    }
  }

  private void runPendingTasks() {
    List<Runnable> tasks;
    synchronized (myPendingTasks) {
      tasks = new ArrayList<>(myPendingTasks);
      myPendingTasks.clear();
    }
    for (Runnable task : tasks) {
      task.run();
    }
  }
}
