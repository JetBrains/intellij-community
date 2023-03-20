// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ProgressSuspenderTest extends LightPlatformTestCase {
  public void testSuspendResumeWork() {
    Semaphore mayStop = new Semaphore(1);
    ProgressIndicatorBase progress = new ProgressIndicatorBase();
    Future<?> task = startBackgroundProcess(progress,
                                            () -> ProgressSuspender.markSuspendable(progress, ""),
                                            () -> workUntilStopped(mayStop, progress));

    suspendProgressAndRun(progress, () -> {
      mayStop.up();
      letBackgroundThreadsSuspend();
      assertFalse(task.isDone());
    });

    assertThreadCompleted(task);
  }

  public void testForkedThreadsAreSuspendedIfCheckCanceledIsCalledOnSuspendedIndicator() {
    Semaphore mayStop1 = new Semaphore(1);
    Semaphore mayStop2 = new Semaphore(1);
    ProgressIndicatorBase progress = new ProgressIndicatorBase();
    Ref<Future<?>> innerTask = Ref.create();
    Future<?> task = startBackgroundProcess(
      progress,
      () -> {
        ProgressSuspender.markSuspendable(progress, "");
        innerTask.set(startBackgroundProcess(new ProgressIndicatorBase(),
                                             () -> {},
                                             () -> workUntilStopped(mayStop2, progress)));
      },
      () -> workUntilStopped(mayStop1, progress));

    suspendProgressAndRun(progress, () -> {
      mayStop1.up();
      mayStop2.up();

      letBackgroundThreadsSuspend();

      assertFalse(task.isDone());
      assertFalse(innerTask.get().isDone());
    });

    assertThreadCompleted(task);
    assertThreadCompleted(innerTask.get());
  }

  public void testUnrelatedThreadsAreNotSuspendedEvenWhenHavingSameNestedIndicator() {
    Semaphore mayStop1 = new Semaphore(1);
    Semaphore mayStop2 = new Semaphore(1);

    ProgressIndicatorBase taskProgress = new ProgressIndicatorBase();
    Future<?> task = startBackgroundProcess(
      taskProgress,
      () -> ProgressSuspender.markSuspendable(taskProgress, ""),
      () -> ProgressManager.getInstance().executeNonCancelableSection(() -> workUntilStopped(mayStop1, taskProgress)));

    Future<?> unrelated = startBackgroundProcess(
      new ProgressIndicatorBase(),
      () -> {},
      () -> ProgressManager.getInstance().executeNonCancelableSection(
        () -> workUntilStopped(mayStop2, ProgressManager.getInstance().getProgressIndicator())));

    suspendProgressAndRun(taskProgress, () -> {
      mayStop1.up();
      mayStop2.up();

      letBackgroundThreadsSuspend();

      assertFalse(task.isDone());
      assertThreadCompleted(unrelated);
    });

    assertThreadCompleted(task);
  }

  private static void assertThreadCompleted(Future<?> task) {
    try {
      task.get(1, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void letBackgroundThreadsSuspend() {
    TimeoutUtil.sleep(10);
  }

  private static void suspendProgressAndRun(ProgressIndicatorBase progress, Runnable runnable) {
    ProgressSuspender suspender = ProgressSuspender.getSuspender(progress);
    suspender.suspendProcess("");
    try {
      runnable.run();
    }
    finally {
      suspender.resumeProcess();
    }
  }

  private static void workUntilStopped(Semaphore mayStop, ProgressIndicator indicator) {
    while (true) {
      boolean released = mayStop.waitFor(1);
      maybeSuspend(indicator);
      if (released) {
        break;
      }
    }
  }

  private static Future<?> startBackgroundProcess(ProgressIndicator progress, Runnable beforeReturn, Runnable runnable) {
    Semaphore mayReturn = new Semaphore(1);
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(() -> {
      beforeReturn.run();
      mayReturn.up();
      runnable.run();
    }, progress));
    assertTrue(mayReturn.waitFor(1000));
    return future;
  }

  private static void maybeSuspend(ProgressIndicator indicator) {
    indicator.checkCanceled();
  }
}