// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 */
public final class LowMemoryWatcher {
  private static final Logger LOG = Logger.getInstance(LowMemoryWatcher.class);

  public enum LowMemoryWatcherType {
    ALWAYS,
    ONLY_AFTER_GC
  }

  private static final WeakList<LowMemoryWatcher> ourListeners = new WeakList<>();
  private final Runnable myRunnable;
  private final LowMemoryWatcherType myType;
  private static final AtomicBoolean ourNotificationsSuppressed = new AtomicBoolean();

  public static <T> T runWithNotificationsSuppressed(Computable<T> runnable) {
    if (ourNotificationsSuppressed.getAndSet(true)) {
      throw new IllegalStateException("runWithNotificationsSuppressed does not support reentrancy");
    }
    try {
      return runnable.compute();
    }
    finally {
      ourNotificationsSuppressed.set(false);
    }
  }

  public static void onLowMemorySignalReceived(boolean afterGc) {
    LOG.info("Low memory signal received: afterGc=" + afterGc);
    for (LowMemoryWatcher watcher : ourListeners.toStrongList()) {
      try {
        if (watcher.myType == LowMemoryWatcherType.ALWAYS
            || watcher.myType == LowMemoryWatcherType.ONLY_AFTER_GC && afterGc) {
          watcher.myRunnable.run();
        }
      }
      catch (Throwable e) {
        LOG.info(e);
      }
    }
  }

  static boolean notificationsSuppressed() {
    return ourNotificationsSuppressed.get();
  }

  /**
   * Registers a runnable to run on low memory events
   * @return a LowMemoryWatcher instance holding the runnable. This instance should be kept in memory while the
   * low memory notification functionality is needed. As soon as it's garbage-collected, the runnable won't receive any further notifications.
   * @param runnable the action which executes on low-memory condition. Can be executed:
   *                 - in arbitrary thread
   *                 - in unpredictable time
   *                 - multiple copies in parallel so please make it reentrant.
   * @param notificationType When ONLY_AFTER_GC, then the runnable will be invoked only if the low-memory condition still exists after GC.
   *                         When ALWAYS, then the runnable also will be invoked when the low-memory condition is detected before GC.
   *
   */
  @Contract(pure = true) // to avoid ignoring the result
  public static LowMemoryWatcher register(@NotNull Runnable runnable, @NotNull LowMemoryWatcherType notificationType) {
    return new LowMemoryWatcher(runnable, notificationType);
  }

  @Contract(pure = true) // to avoid ignoring the result
  public static LowMemoryWatcher register(@NotNull Runnable runnable) {
    return new LowMemoryWatcher(runnable, LowMemoryWatcherType.ALWAYS);
  }

  /**
   * Registers a runnable to run on low memory events. The notifications will be issued until parentDisposable is disposed.
   */
  public static void register(@NotNull Runnable runnable, @NotNull LowMemoryWatcherType notificationType,
                              @NotNull Disposable parentDisposable) {
    final LowMemoryWatcher watcher = new LowMemoryWatcher(runnable, notificationType);
    Disposer.register(parentDisposable, () -> watcher.stop());
  }

  public static void register(@NotNull Runnable runnable, @NotNull Disposable parentDisposable) {
    register(runnable, LowMemoryWatcherType.ALWAYS, parentDisposable);
  }

  private LowMemoryWatcher(@NotNull Runnable runnable, @NotNull LowMemoryWatcherType type) {
    myRunnable = runnable;
    myType = type;
    ourListeners.add(this);
  }

  public void stop() {
    ourListeners.remove(this);
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
