/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 */
public final class TimeoutUtil {
  public static void executeWithTimeout(long timeout, long sleep, @NotNull final Runnable run) {
    final long start = System.currentTimeMillis();
    final AtomicBoolean done = new AtomicBoolean(false);
    final Thread thread = new Thread("Fast Function Thread@" + run.hashCode()) {
      @Override
      public void run() {
        run.run();
        done.set(true);
      }
    };
    thread.start();

    while (!done.get() && System.currentTimeMillis() - start < timeout) {
      try {
        //noinspection BusyWait
        Thread.sleep(sleep);
      } catch (InterruptedException e) {
        break;
      }
    }
    if (!thread.isInterrupted()) {
      //noinspection deprecation
      thread.stop();
    }
  }

  public static void executeWithTimeout(long timeout, @NotNull final Runnable run) {
    executeWithTimeout(timeout, 50, run);
  }

  public static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ignored) { }
  }

  public static long getDurationMillis(long startNanoTime) {
    return (System.nanoTime() - startNanoTime) / 1000000;
  }
}
