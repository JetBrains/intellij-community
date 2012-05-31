/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.util.containers.WeakList;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 24, 2010
 */
public class LowMemoryWatcher {
  private static final long MEM_THRESHOLD = 5 /*MB*/ * 1024 * 1024;
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.LowMemoryWatcher");
  
  private static final WeakList<LowMemoryWatcher> ourInstances = new WeakList<LowMemoryWatcher>();

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
    ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(new NotificationListener() {
      public void handleNotification(Notification n, Object hb) {
        if (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(n.getType()) || MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(n.getType())) {
          for (LowMemoryWatcher watcher : ourInstances) {
            try {
              watcher.myRunnable.run();
            }
            catch (Throwable e) {
              LOG.info(e);
            }
          }
        }
      }
    }, null, null);
  }
  
  public static LowMemoryWatcher register(Runnable runnable) {
    return new LowMemoryWatcher(runnable);
  }
  
  private LowMemoryWatcher(Runnable runnable) {
    myRunnable = runnable;
    ourInstances.add(this);
  }

  public void stop() {
    ourInstances.remove(this);
  }
  
}
