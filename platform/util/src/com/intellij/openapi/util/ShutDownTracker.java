// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/// Register tasks to run on JVM shutdown, on [Runtime#addShutdownHook(Thread)]. Usually used as a last chance
/// to close/flush/cleanup important resources.
///
/// Shutdown tasks are run sequentially, single-threaded, **in LIFO order**: the last task added is executed first.
///
/// If shutdown [`is started`][#isShutdownStarted()] – **no more tasks could be added or removed**, [ProcessCanceledException]
/// is thrown in an attempt to modify the shutdown tasks list. (Modifying the task list during shutdown makes an execution
/// order unpredictable and causes hard to debug issues – hence the restriction.)
///
/// **BEWARE:** Shutdown tasks are a low-level tool. They delay application shutdown, which could make an app 'not
/// responding' and/or cause OS to terminate an app forcibly. Shutdown tasks are also prone to all sorts of issues with
/// the order of deinitialization of dependent components/services. So use this class only as a last resort and prefer
/// regular ways of resource management where possible.
@ApiStatus.Internal
public final class ShutDownTracker {
  private final Deque<Runnable> myShutdownTasks = new ConcurrentLinkedDeque<>();
  private final Deque<Runnable> myCachesShutdownTasks = new ConcurrentLinkedDeque<>();
  private final Thread myThread;

  private ShutDownTracker() {
    myThread = new Thread(() -> run(), "Shutdown tracker");
    Runtime.getRuntime().addShutdownHook(myThread);
  }

  private static final class ShutDownTrackerHolder {
    private static final ShutDownTracker ourInstance = new ShutDownTracker();
  }

  public static @NotNull ShutDownTracker getInstance() {
    return ShutDownTrackerHolder.ourInstance;
  }

  /// @deprecated use [#isShutdownStarted()] – more correct and explicit 
  @Deprecated
  public static boolean isShutdownHookRunning() {
    return isShutdownStarted();
  }

  /// @return true if the shutdown hook is started – no more tasks could be added after that
  public static boolean isShutdownStarted() {
    return getInstance().shutdownSequenceIsStarted;
  }

  /// true if the shutdown thread is started: no shutdown tasks could be added after that
  private volatile boolean shutdownSequenceIsStarted = false;

  @VisibleForTesting
  @ApiStatus.Internal
  public void run() {
    shutdownSequenceIsStarted = true;
    while (true) {
      Runnable task = getNextTask();
      if (task == null) break;
      // task can change myShutdownTasks
      try {
        task.run();
      }
      catch (Throwable e) {
        try {
          Logger.getInstance(ShutDownTracker.class).error(e);
        }
        catch (AssertionError ignore) {
          // give a chance to execute all shutdown tasks in tests
        }
      }
    }
  }

  private @Nullable Runnable getNextTask() {
    Runnable task = myCachesShutdownTasks.pollLast();
    if (task != null) return task;
    return myShutdownTasks.pollLast();
  }

  /// If shutdown is started – waits for shutdown tasks termination, up to a specified amount of time.
  ///
  /// **BEWARE**: method terminates immediately if the shutdown thread is not started yet (`!isAlive`) – which
  /// makes its use unreliable, since you can't be sure the thread is already started, and without that the
  /// method could terminate immediately while the hook is not even started.
  ///
  /// @return `true` if terminated within the given timeout
  public boolean waitFor(long timeout, @NotNull TimeUnit unit) {
    if (myThread.isAlive()) {
      try {
        myThread.join(unit.toMillis(timeout));
      }
      catch (InterruptedException ignored) { }
      return !myThread.isAlive();
    }
    return false;
  }

  /// Tasks registered by this method are executed in LIFO order: i.e., the last-registered task is the first to be executed.
  public void registerShutdownTask(@NotNull Runnable task) {
    checkShutDownIsNotRunning();
    myShutdownTasks.addLast(task);
  }

  public void unregisterShutdownTask(@NotNull Runnable task) {
    checkShutDownIsNotRunning();
    myShutdownTasks.remove(task);
    myCachesShutdownTasks.remove(task);
  }

  /// Specially ordered queue for high-priority shutdown tasks, specifically for tasks related to VFS and indexes.
  ///
  /// This queue is designed to prioritize and flush these tasks as early as possible to minimize the risk
  /// of shutdown hook execution interrupted by OS.
  ///
  /// Tasks registered by this method are executed in **LIFO order**: i.e., the last-registered task is the first to be executed
  /// (same as with regular [#registerShutdownTask(Runnable)]).
  @ApiStatus.Internal
  public void registerCacheShutdownTask(@NotNull Runnable task) {
    checkShutDownIsNotRunning();
    myCachesShutdownTasks.addLast(task);
  }

  private void checkShutDownIsNotRunning() {
    // It is better to prohibit adding new shutdown tasks if the shutdown is already started.
    // Otherwise, it becomes much harder to reason about the order of shutdown tasks (which sometimes is important).
    // Racy scenarios (like "shutdown tasks added", "shutdown started" "some of the shutdown tasks executed", "new tasks added",
    // "new tasks executed") become hard to debug, too.
    if (shutdownSequenceIsStarted) {
      throw new ShutDownAlreadyRunningException("Shutdown tasks are running, can't change the list of tasks anymore");
    }
  }

  private static class ShutDownAlreadyRunningException extends ProcessCanceledException {
    protected ShutDownAlreadyRunningException(@NotNull String message) {
      super(message);
    }
  }
}
