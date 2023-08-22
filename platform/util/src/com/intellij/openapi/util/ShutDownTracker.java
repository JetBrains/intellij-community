// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

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

  public static boolean isShutdownHookRunning() {
    return getInstance().myThread.isAlive();
  }

  @ApiStatus.Internal
  public void run() {
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

  @Nullable
  private Runnable getNextTask() {
    Runnable task = myCachesShutdownTasks.pollLast();
    if (task != null) return task;
    return myShutdownTasks.pollLast();
  }

  // returns true if terminated
  public boolean waitFor(long timeout, @NotNull TimeUnit unit) {
    if (isShutdownHookRunning()) {
      try {
        myThread.join(unit.toMillis(timeout));
      }
      catch (InterruptedException ignored) { }
      return !myThread.isAlive();
    }
    return false;
  }

  public void registerShutdownTask(@NotNull Runnable task) {
    myShutdownTasks.addLast(task);
  }

  public void unregisterShutdownTask(@NotNull Runnable task) {
    myShutdownTasks.remove(task);
    myCachesShutdownTasks.remove(task);
  }

  /**
   * Special ordered queue for cache-related tasks, specifically for tasks related to:
   * <p>
   * * VFS (Virtual File System),
   * <p>
   * * Indexes.
   * <p>
   * This queue is designed to prioritize and flush these tasks as early as possible to minimize
   * the risk of shutdown hook execution interruption by OS.
   */
  @ApiStatus.Internal
  public void registerCacheShutdownTask(@NotNull Runnable task) {
    myCachesShutdownTasks.addLast(task);
  }
}