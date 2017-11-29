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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.MissingResourceException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class LowMemoryWatcherManager implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.LowMemoryWatcherManager");

  private static final long MEM_THRESHOLD = 5 /*MB*/ * 1024 * 1024;
  @NotNull private final ExecutorService myExecutorService;

  private Future<?> mySubmitted; // guarded by ourJanitor
  private final AtomicBoolean myProcessing = new AtomicBoolean();
  private final Runnable myJanitor = new Runnable() {
    @Override
    public void run() {
      // null mySubmitted before all listeners called to avoid data race when listener added in the middle of the execution and is lost
      // this may however cause listeners to execute more than once (potentially even in parallel)
      synchronized (myJanitor) {
        mySubmitted = null;
      }
      LowMemoryWatcher.onLowMemorySignalReceived();
    }
  };

  public LowMemoryWatcherManager(@NotNull ExecutorService executorService) {
    myExecutorService = new SequentialTaskExecutor("LowMemoryWatcherManager", executorService);
    try {
      for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
        if (bean.getType() == MemoryType.HEAP && bean.isUsageThresholdSupported()) {
          long max = bean.getUsage().getMax();
          long threshold = Math.min((long) (max * getOccupiedMemoryThreshold()), max - MEM_THRESHOLD);
          if (threshold > 0) {
            bean.setUsageThreshold(threshold);
          }
        }
      }
      ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(myLowMemoryListener, null, null);
    }
    catch (Throwable e) {
      // should not happen normally
      LOG.info("Errors initializing LowMemoryWatcher: ", e);
    }
  }

  private final NotificationListener myLowMemoryListener = new NotificationListener() {
    @Override
    public void handleNotification(Notification notification, Object __) {
      if (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType())) {

        if (Registry.is("low.memory.watcher.sync", true)) {
          handleEventImmediately();
          return;
        }

        synchronized (myJanitor) {
          if (mySubmitted == null) {
            mySubmitted = myExecutorService.submit(myJanitor);
          }
        }
      }
    }
  };

  private static double getOccupiedMemoryThreshold() {
    try {
      return Registry.doubleValue("low.memory.watcher.notification.threshold");
    }
    catch (MissingResourceException e) {
      return 0.95;
    }
  }

  private void handleEventImmediately() {
    if (myProcessing.compareAndSet(false, true)) {
      try {
        myJanitor.run();
      }
      finally {
        myProcessing.set(false);
      }
    }
  }

  @Override
  public void dispose() {
    try {
      ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).removeNotificationListener(myLowMemoryListener);
    }
    catch (ListenerNotFoundException e) {
      LOG.error(e);
    }
    synchronized (myJanitor) {
      if (mySubmitted != null) {
        mySubmitted.cancel(false);
        mySubmitted = null;
      }
    }

    LowMemoryWatcher.stopAll();
  }
}
