// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public final class ShutDownTracker implements Runnable {
  private final List<Thread> myThreads = Collections.synchronizedList(new ArrayList<>());
  private final ConcurrentLinkedDeque<Runnable> myShutdownTasks = new ConcurrentLinkedDeque<>();
  private final Thread myThread;

  private ShutDownTracker() {
    myThread = new Thread(this, "Shutdown tracker");
    Runtime.getRuntime().addShutdownHook(myThread);
  }

  private static class ShutDownTrackerHolder {
    private static final ShutDownTracker ourInstance = new ShutDownTracker();
  }

  public static @NotNull ShutDownTracker getInstance() {
    return ShutDownTrackerHolder.ourInstance;
  }

  public static boolean isShutdownHookRunning() {
    return getInstance().myThread.isAlive();
  }

  @Override
  public void run() {
    ensureStopperThreadsFinished();

    while (true) {
      Runnable task = myShutdownTasks.pollLast();
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

  public void ensureStopperThreadsFinished() {
    Thread[] threads = getStopperThreads();
    final long started = System.currentTimeMillis();
    while (threads.length > 0) {
      Thread thread = threads[0];
      if (!thread.isAlive()) {
        if (isRegistered(thread)) {
          Logger.getInstance(ShutDownTracker.class).error("Thread '" + thread.getName() + "' did not unregister itself from ShutDownTracker");
          unregisterStopperThread(thread);
        }
      }
      else {
        final long totalTimeWaited = System.currentTimeMillis() - started;
        if (totalTimeWaited > 3000) {
          // okay, we are waiting fo too long already, lets stop everyone:
          // in some cases, stopper thread may try to run readAction while we are shutting down (under a writeAction) and deadlock.
          thread.interrupt();
        }

        try {
          thread.join(100);
        }
        catch (InterruptedException ignored) { }
      }
      threads = getStopperThreads();
    }
  }

  private boolean isRegistered(@NotNull Thread thread) {
    return myThreads.contains(thread);
  }

  private Thread @NotNull [] getStopperThreads() {
    return myThreads.toArray(new Thread[0]);
  }

  private void unregisterStopperThread(@NotNull Thread thread) {
    myThreads.remove(thread);
  }

  public void executeWithStopperThread(@NotNull Thread thread, @NotNull Runnable runnable) {
    computeWithStopperThread(thread, () -> {
      runnable.run();
      return null;
    });
  }

  public <T, E extends Throwable> T computeWithStopperThread(@NotNull Thread thread, @NotNull ThrowableComputable<T, E> runnable) throws E {
    myThreads.add(thread);
    try {
      return runnable.compute();
    }
    finally {
      myThreads.remove(thread);
    }
  }

  public void registerShutdownTask(@NotNull Runnable task, @NotNull Disposable parentDisposable) {
    registerShutdownTask(task);
    Disposer.register(parentDisposable, () -> unregisterShutdownTask(task));
  }

  public void registerShutdownTask(@NotNull Runnable task) {
    myShutdownTasks.addLast(task);
  }

  public void unregisterShutdownTask(@NotNull Runnable task) {
    myShutdownTasks.remove(task);
  }
}