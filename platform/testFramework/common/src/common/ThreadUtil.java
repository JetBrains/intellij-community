// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@TestOnly
@Internal
public final class ThreadUtil {

  private ThreadUtil() { }

  private static final Method getThreads = Objects.requireNonNull(ReflectionUtil.getDeclaredMethod(Thread.class, "getThreads"));

  public static @NotNull Map<String, Thread> getThreads() {
    Thread[] threads;
    try {
      // faster than Thread.getAllStackTraces().keySet()
      threads = (Thread[])getThreads.invoke(null);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    if (threads.length == 0) {
      return Collections.emptyMap();
    }

    Map<String, Thread> map = new HashMap<>(threads.length);
    for (Thread thread : threads) {
      map.put(thread.getName(), thread);
    }
    return map;
  }

  public static void printThreadDump() {
    PerformanceWatcher.dumpThreadsToConsole("Thread dump:");
  }
}
