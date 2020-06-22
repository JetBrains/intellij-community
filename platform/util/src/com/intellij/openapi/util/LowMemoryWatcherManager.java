// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class LowMemoryWatcherManager implements Disposable {
  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(LowMemoryWatcherManager.class);
  }

  private static final long MEM_THRESHOLD = 5 /*MB*/ * 1024 * 1024;
  @NotNull private final ExecutorService myExecutorService;

  private Future<?> mySubmitted; // guarded by myJanitor
  private final Future<?> myMemoryPoolMXBeansFuture;
  private final Consumer<Boolean> myJanitor = new Consumer<Boolean>() {
    @Override
    public void accept(@NotNull Boolean afterGc) {
      // null mySubmitted before all listeners called to avoid data race when listener added in the middle of the execution and is lost
      // this may however cause listeners to execute more than once (potentially even in parallel)
      synchronized (myJanitor) {
        mySubmitted = null;
      }
      LowMemoryWatcher.onLowMemorySignalReceived(afterGc);
    }
  };

  public LowMemoryWatcherManager(@NotNull ExecutorService backendExecutorService) {
    // whether LowMemoryWatcher runnables should be executed on the same thread that the low memory events come
    myExecutorService = SystemProperties.getBooleanProperty("low.memory.watcher.sync", false) ?
      ConcurrencyUtil.newSameThreadExecutorService() :
      SequentialTaskExecutor.createSequentialApplicationPoolExecutor("LowMemoryWatcherManager", backendExecutorService);

    myMemoryPoolMXBeansFuture = initializeMXBeanListenersLater(backendExecutorService);
  }

  @NotNull
  private Future<?> initializeMXBeanListenersLater(@NotNull ExecutorService backendExecutorService) {
    // do it in the other thread to get it out of the way during startup
    return backendExecutorService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (bean.getType() == MemoryType.HEAP && bean.isCollectionUsageThresholdSupported() && bean.isUsageThresholdSupported()) {
              long max = bean.getUsage().getMax();
              long threshold = Math.min((long)(max * getOccupiedMemoryThreshold()), max - MEM_THRESHOLD);
              if (threshold > 0) {
                bean.setUsageThreshold(threshold);
                bean.setCollectionUsageThreshold(threshold);
              }
            }
          }
          ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(myLowMemoryListener, null, null);
        }
        catch (Throwable e) {
          // should not happen normally
          getLogger().info("Errors initializing LowMemoryWatcher: ", e);
        }
      }

      @Override
      public String toString() {
        return "initializeMXBeanListeners runnable";
      }
    });
  }

  private final NotificationListener myLowMemoryListener = new NotificationListener() {
    @Override
    public void handleNotification(Notification notification, Object __) {
      if (LowMemoryWatcher.notificationsSuppressed()) return;
      boolean memoryThreshold = MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType());
      boolean memoryCollectionThreshold = MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType());

      if (memoryThreshold || memoryCollectionThreshold) {
        synchronized (myJanitor) {
          if (mySubmitted == null) {
            mySubmitted = myExecutorService.submit(() -> myJanitor.accept(memoryCollectionThreshold));
            // maybe it's executed too fast or even synchronously
            if (mySubmitted.isDone()) {
              mySubmitted = null;
            }
          }
        }
      }
    }
  };

  private static float getOccupiedMemoryThreshold() {
    return SystemProperties.getFloatProperty("low.memory.watcher.notification.threshold", 0.95f);
  }

  @Override
  public void dispose() {
    try {
      myMemoryPoolMXBeansFuture.get();
      ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).removeNotificationListener(myLowMemoryListener);
    }
    catch (Exception e) {
      getLogger().error(e);
    }
    synchronized (myJanitor) {
      if (mySubmitted != null) {
        mySubmitted.cancel(false);
        mySubmitted = null;
      }
    }

    LowMemoryWatcher.stopAll();
  }

  @TestOnly
  public void waitForInitComplete(int timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    myMemoryPoolMXBeansFuture.get(timeout, unit);
  }
}
