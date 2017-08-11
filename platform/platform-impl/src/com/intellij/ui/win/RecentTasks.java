/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.win;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.util.lang.UrlClassLoader;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecentTasks {
  private static final AtomicBoolean initialized = new AtomicBoolean(false);
  private static final WeakReference<Thread> openerThread;
  private static final String openerThreadName;

  static {
    Thread thread = Thread.currentThread();
    openerThread = new WeakReference<>(thread);
    openerThreadName = thread.getName();
    UrlClassLoader.loadPlatformLibrary("jumpListBridge");
  }

  private synchronized static void init() {
    if (initialized.compareAndSet(false, true)) {
      initialize(ApplicationInfoEx.getInstanceEx().getVersionName() + "." + PathManager.getConfigPath().hashCode());
    }
  }

  /**
   * COM initialization should be invoked once per process.
   * All invocations should be made from the same thread.
   */
  native private static void initialize(String applicationId);
  native private static void addTasksNativeForCategory(@SuppressWarnings("SameParameterValue") String category, Task[] tasks);
  native static String getShortenPath(String paths);
  native private static void clearNative();

  public synchronized static void clear() {
    init();
    checkThread();
    clearNative();
  }

  /**
   * Use #clearNative method instead of passing empty array of tasks.
   */
  public synchronized static void addTasks(final Task[] tasks) {
    if (tasks.length == 0) return;
    init();
    checkThread();
    addTasksNativeForCategory("Recent", tasks);
  }

  private static void checkThread() {
    Thread thread = Thread.currentThread();
    if (!thread.equals(openerThread.get())) {
      throw new RuntimeException("Current thread is '" + thread.getName() + "'; this class should be used from '" + openerThreadName + "'");
    }
  }
}