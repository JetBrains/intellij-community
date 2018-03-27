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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class LowMemoryWatcher {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.LowMemoryWatcher");

  private static final WeakList<Runnable> ourListeners = new WeakList<Runnable>();
  private final Runnable myRunnable;

  static void onLowMemorySignalReceived() {
    LOG.info("Low memory signal received.");
    for (Runnable watcher : ourListeners.toStrongList()) {
      try {
        watcher.run();
      }
      catch (Throwable e) {
        LOG.info(e);
      }
    }
  }

  /**
   * Registers a runnable to run on low memory events
   * @return a LowMemoryWatcher instance holding the runnable. This instance should be kept in memory while the
   * low memory notification functionality is needed. As soon as it's garbage-collected, the runnable won't receive any further notifications.
   * @param runnable the action which executes on low-memory condition. Can be executed:
   *                 - in arbitrary thread
   *                 - in unpredictable time
   *                 - multiple copies in parallel so please make it reentrant.
   */
  @Contract(pure = true)
  public static LowMemoryWatcher register(@NotNull Runnable runnable) {
    return new LowMemoryWatcher(runnable);
  }

  /**
   * Registers a runnable to run on low memory events. The notifications will be issued until parentDisposable is disposed.
   */
  public static void register(@NotNull Runnable runnable, @NotNull Disposable parentDisposable) {
    final LowMemoryWatcher watcher = new LowMemoryWatcher(runnable);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        watcher.stop();
      }
    });
  }

  private LowMemoryWatcher(@NotNull Runnable runnable) {
    myRunnable = runnable;
    ourListeners.add(runnable);
  }

  public void stop() {
    ourListeners.remove(myRunnable);
  }

  /**
   * LowMemoryWatcher maintains a background thread where all the handlers are invoked.
   * In server environments, this thread may run indefinitely and prevent the class loader from
   * being gc-ed. Thus it's necessary to invoke this method to stop that thread and let the classes be garbage-collected.
   */
  static void stopAll() {
    ourListeners.clear();
  }
}
