// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class ThreadDumper {
  private ThreadDumper() {
  }

  @NotNull
  public static String dumpThreadsToString() {
    StringWriter writer = new StringWriter();
    dumpThreadInfos(getThreadInfos(), writer);
    return writer.toString();
  }

  @NotNull
  public static String dumpEdtStackTrace(ThreadInfo @NotNull [] threadInfos) {
    StringWriter writer = new StringWriter();
    if (threadInfos.length > 0) {
      StackTraceElement[] trace = threadInfos[0].getStackTrace();
      printStackTrace(writer, trace);
    }
    return writer.toString();
  }

  public static @NotNull ThreadInfo @NotNull [] getThreadInfos() {
    return getThreadInfos(ManagementFactory.getThreadMXBean(), true);
  }

  @NotNull
  public static ThreadDump getThreadDumpInfo(ThreadInfo @NotNull [] threadInfos) {
    sort(threadInfos);
    StringWriter writer = new StringWriter();
    StackTraceElement[] edtStack = dumpThreadInfos(threadInfos, writer);
    return new ThreadDump(writer.toString(), edtStack, threadInfos);
  }

  public static ThreadInfo @NotNull [] getThreadInfos(@NotNull ThreadMXBean threadMXBean, boolean sort) {
    ThreadInfo[] threads;
    try {
      threads = threadMXBean.dumpAllThreads(false, false);
    }
    catch (Exception ignored) {
      threads = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), Integer.MAX_VALUE);
    }
    if (sort) {
      sort(threads);
    }
    return threads;
  }

  public static boolean isEDT(@NotNull ThreadInfo info) {
    return isEDT(info.getThreadName());
  }

  public static boolean isEDT(@Nullable String threadName) {
    return threadName != null && threadName.startsWith("AWT-EventQueue");
  }

  private static StackTraceElement[] dumpThreadInfos(ThreadInfo @NotNull [] threadInfo, @NotNull Writer f) {
    StackTraceElement[] edtStack = null;
    for (ThreadInfo info : threadInfo) {
      if (info != null) {
        if (isEDT(info)) {
          edtStack = info.getStackTrace();
        }
        dumpThreadInfo(info, f);
      }
    }
    return edtStack;
  }

  public static ThreadInfo @NotNull [] sort(@NotNull ThreadInfo @NotNull [] threads) {
    Arrays.sort(threads, Comparator
      .comparing((ThreadInfo threadInfo) -> !isEDT(threadInfo.getThreadName())) // show EDT first
      .thenComparing(threadInfo -> threadInfo.getThreadState() != Thread.State.RUNNABLE) // then all runnable
      .thenComparingInt(threadInfo -> -threadInfo.getStackTrace().length) // show meaningful stacktraces first
      .thenComparing(threadInfo -> threadInfo.getThreadName()) // sorted by name among same stacktraces
    );
    return threads;
  }

  private static void dumpThreadInfo(@NotNull ThreadInfo info, @NotNull Writer f) {
    dumpCallStack(info, f, info.getStackTrace());
  }

  private static void dumpCallStack(@NotNull ThreadInfo info, @NotNull Writer f, StackTraceElement @NotNull [] stackTraceElements) {
    try {
      @NonNls StringBuilder sb = new StringBuilder("\"").append(info.getThreadName()).append("\"");
      sb.append(" prio=0 tid=0x0 nid=0x0 ").append(getReadableState(info.getThreadState())).append("\n");
      sb.append("     java.lang.Thread.State: ").append(info.getThreadState()).append("\n");
      if (info.getLockName() != null) {
        sb.append(" on ").append(info.getLockName());
      }
      if (info.getLockOwnerName() != null) {
        sb.append(" owned by \"").append(info.getLockOwnerName()).append("\" Id=").append(info.getLockOwnerId());
      }
      if (info.isSuspended()) {
        sb.append(" (suspended)");
      }
      if (info.isInNative()) {
        sb.append(" (in native)");
      }

      f.write(sb + "\n");
      printStackTrace(f, stackTraceElements);
      f.write("\n");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void dumpCallStack(@NotNull Thread thread, @NotNull Writer f, StackTraceElement @NotNull [] stackTraceElements) {
    try {
      @NonNls StringBuilder sb = new StringBuilder("\"").append(thread.getName()).append("\"");
      sb.append(" prio=0 tid=0x0 nid=0x0 ").append(getReadableState(thread.getState())).append("\n");
      sb.append("     java.lang.Thread.State: ").append(thread.getState()).append("\n");

      f.write(sb + "\n");
      printStackTrace(f, stackTraceElements);
      f.write("\n");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void printStackTrace(@NotNull Writer f, StackTraceElement @NotNull [] stackTraceElements) {
    try {
      for (StackTraceElement element : stackTraceElements) {
        f.write("\tat " + element + "\n");
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  /**
   * Returns the EDT stack in a form that Google Crash understands, or null if the EDT stack cannot be determined.
   *
   * @param fullThreadDump lines comprising a thread dump as formatted by {@link #dumpCallStack(ThreadInfo, Writer, StackTraceElement[])}
   */
  @Nullable
  public static String getEdtStackForCrash(@NotNull String fullThreadDump, @NotNull String exceptionType) {
    // We know that the AWT-EventQueue-* thread is dumped out first (see #sort above), and for each thread, there are at the very least
    // 3 lines printed out before the stack trace. If we don't see any of this, then return early
    List<String> threadDump = Arrays.asList(fullThreadDump.split("\n"));

    if (threadDump.size() < 3) {
      return null;
    }

    String line = threadDump.get(0); // e.g. "AWT-EventQueue-0 ...
    int i = line.indexOf(' ');
    if (i <= 1) {
      return null;
    }

    StringBuilder sb = new StringBuilder(200);
    sb.append(exceptionType).append(": ");
    sb.append(line, 1, i); // append thread name (e.g. AWT-EventQueue-0)

    line = threadDump.get(1); // e.g. " java.lang.Thread.State: RUNNABLE"
    String[] words = line.trim().split(" ");
    if (words.length < 2) {
      return null;
    }

    sb.append(' ');
    sb.append(words[1]); // e.g. "RUNNABLE"

    // the 3rd line contains lock information (or is empty)
    line = threadDump.get(2);
    if (!line.trim().isEmpty()) {
      sb.append(' ');
      sb.append(line.trim());
    }

    sb.append('\n');

    // the rest of the lines correspond to the stack trace until we reach an empty line
    for (i = 3; i < threadDump.size(); i++) {
      line = threadDump.get(i);
      if (line.trim().isEmpty()) {
        break;
      }

      sb.append(line);
      sb.append('\n');
    }

    return sb.toString().trim();
  }

  private static String getReadableState(@NotNull Thread.State state) {
    switch (state) {
      case BLOCKED: return "blocked";
      case TIMED_WAITING:
      case WAITING: return "waiting on condition";
      case RUNNABLE: return "runnable";
      case NEW: return "new";
      case TERMINATED: return "terminated";
    }
    return null;
  }
}
