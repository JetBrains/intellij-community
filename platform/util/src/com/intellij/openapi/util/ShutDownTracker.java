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
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ShutDownTracker implements Runnable {
  private final List<Thread> myThreads = new ArrayList<Thread>();
  private final LinkedList<Runnable> myShutdownTasks = new LinkedList<Runnable>();
  private final Thread myThread;

  private ShutDownTracker() {
    myThread = new Thread(this, "Shutdown tracker");
    Runtime.getRuntime().addShutdownHook(myThread);
  }

  private static class ShutDownTrackerHolder {
    private static final ShutDownTracker ourInstance = new ShutDownTracker();
  }

  @NotNull
  public static ShutDownTracker getInstance() {
    return ShutDownTrackerHolder.ourInstance;
  }

  public static boolean isShutdownHookRunning() {
    return getInstance().myThread.isAlive();
  }

  @Override
  public void run() {
    ensureStopperThreadsFinished();

    Runnable task;
    while ((task = removeLast(myShutdownTasks)) != null) {
      // task can change myShutdownTasks
      try {
        task.run();
      }
      catch (Throwable e) {
        Logger.getInstance(ShutDownTracker.class).error(e);
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

  public final void ensureStopperThreadsFinished() {
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

  private synchronized boolean isRegistered(@NotNull Thread thread) {
    return myThreads.contains(thread);
  }

  @NotNull
  private synchronized Thread[] getStopperThreads() {
    return myThreads.toArray(new Thread[myThreads.size()]);
  }

  public synchronized void registerStopperThread(@NotNull Thread thread) {
    myThreads.add(thread);
  }

  public synchronized void unregisterStopperThread(@NotNull Thread thread) {
    myThreads.remove(thread);
  }

  public synchronized void registerShutdownTask(@NotNull Runnable task) {
    myShutdownTasks.addLast(task);
  }

  public synchronized void unregisterShutdownTask(@NotNull Runnable task) {
    myShutdownTasks.remove(task);
  }

  private synchronized <T> T removeLast(@NotNull LinkedList<T> list) {
    return list.isEmpty() ? null : list.removeLast();
  }

  /** @deprecated to be removed in IDEA 2018 */
  @SuppressWarnings("unused")
  public static void invokeAndWait(boolean returnOnTimeout, boolean runInEdt, @NotNull final Runnable runnable) {
    if (!runInEdt) {
      if (returnOnTimeout) {
        final Semaphore semaphore = new Semaphore();
        semaphore.down();
        new Thread(new Runnable() {
          @Override
          public void run() {
            runnable.run();
            semaphore.up();
          }
        }, "shutdown tracker invoker").start();
        semaphore.waitFor(1000);
      }
      else {
        runnable.run();
      }
      return;
    }

    if (returnOnTimeout) {
      final Semaphore semaphore = new Semaphore();
      semaphore.down();
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          runnable.run();
          semaphore.up();
        }
      });
      semaphore.waitFor(1000);
      return;
    }

    try {
      UIUtil.invokeAndWaitIfNeeded(runnable);
    }
    catch (Exception e) {
      Logger.getInstance(ShutDownTracker.class).error(e);
    }
  }
}