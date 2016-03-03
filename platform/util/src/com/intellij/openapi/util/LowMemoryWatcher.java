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
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 24, 2010
 */
public class LowMemoryWatcher {
  private static final long MEM_THRESHOLD = 5 /*MB*/ * 1024 * 1024;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.LowMemoryWatcher");

  private static final List<LowMemoryWatcher> ourInstances = new WeakList<LowMemoryWatcher>();
  private static Future<?> ourSubmitted;
  private static final Runnable ourJanitor = new Runnable() {
    @Override
    public void run() {
      LOG.info("Low memory signal received.");
      try {
        for (LowMemoryWatcher watcher : ourInstances) {
          try {
            watcher.myRunnable.run();
          }
          catch (Throwable e) {
            LOG.info(e);
          }
        }
      }
      finally {
        synchronized (ourJanitor) {
          ourSubmitted = null;
        }
      }
    }
  };
  private static final NotificationListener ourLowMemoryListener = new NotificationListener() {
    @Override
    public void handleNotification(Notification n, Object hb) {
      if (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(n.getType()) ||
          MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(n.getType())) {
        synchronized (ourJanitor) {
          if (ourSubmitted == null) {
            ourSubmitted = AppExecutorUtil.getAppExecutorService().submit(ourJanitor);
          }
        }
      }
    }
  };

  private final Runnable myRunnable;

  static {
    for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
      if (bean.getType() == MemoryType.HEAP && bean.isUsageThresholdSupported()) {
        long threshold = bean.getUsage().getMax() - MEM_THRESHOLD;
        if (threshold > 0) {
          bean.setUsageThreshold(threshold);
          bean.setCollectionUsageThreshold(threshold);
        }
      }
    }
    ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(ourLowMemoryListener, null, null);
  }

  /**
   * Registers a runnable to run on low memory events
   * @return a LowMemoryWatcher instance holding the runnable. This instance should be kept in memory while the
   * low memory notification functionality is needed. As soon as it's garbage-collected, the runnable won't receive any further notifications.
   */
  public static LowMemoryWatcher register(@NotNull Runnable runnable) {
    return new LowMemoryWatcher(runnable);
  }

  /**
   * Registers a runnable to run on low memory events. The notifications will be issued until parentDisposable is disposed.
   */
  public static void register(@NotNull Runnable runnable, @NotNull Disposable parentDisposable) {
    final Ref<LowMemoryWatcher> watcher = Ref.create(new LowMemoryWatcher(runnable));
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        watcher.get().stop();
        watcher.set(null);
      }
    });
  }

  private LowMemoryWatcher(@NotNull Runnable runnable) {
    myRunnable = runnable;
    ourInstances.add(this);
  }

  public void stop() {
    ourInstances.remove(this);
  }

  /**
   * LowMemoryWatcher maintains a background thread where all the handlers are invoked.
   * In server environments, this thread may run indefinitely and prevent the class loader from
   * being gc-ed. Thus it's necessary to invoke this method to stop that thread and let the classes be garbage-collected.
   */
  public static void stopAll() {
    synchronized (ourJanitor) {
      if (ourSubmitted != null) {
        ourSubmitted.cancel(false);
        ourSubmitted = null;
      }
    }
    ourInstances.clear();
    try {
      ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).removeNotificationListener(ourLowMemoryListener);
    }
    catch (ListenerNotFoundException e) {
      LOG.error(e);
    }
  }
}
