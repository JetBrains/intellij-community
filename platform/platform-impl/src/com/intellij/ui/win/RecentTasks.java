// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.loader.NativeLibraryLoader;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecentTasks {
  private static final AtomicBoolean initialized = new AtomicBoolean(false);
  private static final WeakReference<Thread> openerThread;
  private static final String openerThreadName;

  static {
    Thread thread = Thread.currentThread();
    openerThread = new WeakReference<>(thread);
    openerThreadName = thread.getName();
    NativeLibraryLoader.loadPlatformLibrary("jumpListBridge");
  }

  private synchronized static void init() {
    if (initialized.compareAndSet(false, true)) {
      initialize(ApplicationNamesInfo.getInstance().getFullProductName() + "." + PathManager.getConfigPath().hashCode());
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