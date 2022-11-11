// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.diagnostic.PerformanceWatcher;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.TestOnly;

@TestOnly
@Internal
public final class ThreadUtil {

  private ThreadUtil() { }

  public static void printThreadDump() {
    PerformanceWatcher.dumpThreadsToConsole("Thread dump:");
  }
}
