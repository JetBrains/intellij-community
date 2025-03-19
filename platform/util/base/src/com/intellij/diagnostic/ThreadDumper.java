// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
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

import static com.intellij.diagnostic.CoroutineDumperKt.COROUTINE_DUMP_HEADER;
import static com.intellij.diagnostic.CoroutineDumperKt.COROUTINE_DUMP_HEADER_STRIPPED;

public final class ThreadDumper {
  private ThreadDumper() { }

  /**
   * Evaluate to get coroutine dump in debug sessions
   */
  @ApiStatus.Internal
  @SuppressWarnings("unused")
  public static @NotNull String dumpForDebug() {
    return getThreadDumpInfo(getThreadInfos(), true).getRawDump();
  }

  public static @NotNull String dumpThreadsToString() {
    StringWriter writer = new StringWriter();
    dumpThreadInfos(getThreadInfos(), writer);
    return writer.toString();
  }

  @ApiStatus.Internal
  public static @NotNull ThreadInfo @NotNull [] getThreadInfos() {
    return getThreadInfos(ManagementFactory.getThreadMXBean(), true);
  }

  /**
   * @param stripCoroutineDump whether to remove stack frames from a coroutine dump that has no useful debug information.
   *                           Enabling this flag should significantly reduce coroutine dump size.
   */
  @ApiStatus.Internal
  public static @NotNull ThreadDump getThreadDumpInfo(ThreadInfo @NotNull [] threadInfos, boolean stripCoroutineDump) {
    sort(threadInfos);
    StringWriter writer = new StringWriter();
    StackTraceElement[] edtStack = dumpThreadInfos(threadInfos, writer);
    String coroutineDump = CoroutineDumperKt.dumpCoroutines(null, stripCoroutineDump, true);
    if (coroutineDump != null) {
      if (stripCoroutineDump) {
        writer.write("\n" + COROUTINE_DUMP_HEADER_STRIPPED + "\n");
      }
      else {
        writer.write("\n" + COROUTINE_DUMP_HEADER + "\n");
      }
      writer.write(coroutineDump);
    }
    return new ThreadDump(writer.toString(), edtStack, threadInfos);
  }

  @ApiStatus.Internal
  public static ThreadInfo @NotNull [] getThreadInfos(@NotNull ThreadMXBean threadMXBean, boolean sort) {
    ThreadInfo[] threads;
    try {
      threads = threadMXBean.dumpAllThreads(false, false);
    }
    catch (Exception ignored) {
      threads = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), Integer.MAX_VALUE);
    }
    int o = 0;
    for (int i = 0; i < threads.length; i++) {
      ThreadInfo info = threads[i];
      if (info != null) {
        threads[o++] = info;
      }
    }
    threads = ArrayUtil.realloc(threads, o, ThreadInfo[]::new);
    if (sort) {
      sort(threads);
    }
    return threads;
  }

  @ApiStatus.Internal
  public static boolean isEDT(@NotNull ThreadInfo info) {
    return isEDT(info.getThreadName());
  }

  @ApiStatus.Internal
  public static boolean isEDT(@Nullable String threadName) {
    return threadName != null && (Boolean.getBoolean("jb.dispatching.on.main.thread")? threadName.contains("AppKit")
                                                                                     : threadName.startsWith("AWT-EventQueue"));
  }

  private static StackTraceElement [] dumpThreadInfos(ThreadInfo @NotNull [] threadInfo, @NotNull Writer f) {
    StackTraceElement[] edtStack = null;
    for (ThreadInfo info : threadInfo) {
      if (info != null) {
        String name = info.getThreadName();
        StackTraceElement[] stackTrace = info.getStackTrace();
        if (edtStack == null && isEDT(name)) {
          edtStack = stackTrace;
        }
        if (isIdleDefaultCoroutineDispatch(name, stackTrace)) {
          // avoid 64 coroutine dispatch idle threads littering thread dump
          continue;
        }
        dumpCallStack(info, f, stackTrace);
      }
    }
    return edtStack;
  }

  private static boolean isIdleDefaultCoroutineDispatch(String name, StackTraceElement @NotNull [] stackTrace) {
    return name != null && name.startsWith("DefaultDispatcher-worker-")
      && stackTrace.length == 6
      && stackTrace[0].isNativeMethod() && stackTrace[0].getMethodName().equals("park") && stackTrace[0].getClassName().equals("jdk.internal.misc.Unsafe")
      && stackTrace[1].getMethodName().equals("parkNanos") && stackTrace[1].getClassName().equals("java.util.concurrent.locks.LockSupport")
      && stackTrace[2].getMethodName().equals("park") && stackTrace[2].getClassName().equals("kotlinx.coroutines.scheduling.CoroutineScheduler$Worker")
      && stackTrace[3].getMethodName().equals("tryPark") && stackTrace[3].getClassName().equals("kotlinx.coroutines.scheduling.CoroutineScheduler$Worker")
      && stackTrace[4].getMethodName().equals("runWorker") && stackTrace[4].getClassName().equals("kotlinx.coroutines.scheduling.CoroutineScheduler$Worker")
      && stackTrace[5].getMethodName().equals("run") && stackTrace[5].getClassName().equals("kotlinx.coroutines.scheduling.CoroutineScheduler$Worker")
      ;
  }

  @ApiStatus.Internal
  public static ThreadInfo @NotNull [] sort(@NotNull ThreadInfo @NotNull [] threads) {
    Arrays.sort(threads, Comparator
      .comparing((ThreadInfo threadInfo) -> !isEDT(threadInfo.getThreadName())) // show EDT first
      .thenComparing(threadInfo -> threadInfo.getThreadState() != Thread.State.RUNNABLE) // then all runnable
      .thenComparingInt(threadInfo -> {
        StackTraceElement[] trace = threadInfo.getStackTrace();
        return trace == null ? 0 : -trace.length;
      }) // show meaningful stacktraces first
      .thenComparing(threadInfo -> StringUtilRt.notNullize(threadInfo.getThreadName())) // sorted by name among the same stacktraces
    );
    return threads;
  }

  @ApiStatus.Internal
  public static void dumpThreadInfo(@NotNull ThreadInfo info, @NotNull Writer f) {
    dumpCallStack(info, f, info.getStackTrace());
  }

  private static void dumpCallStack(@NotNull ThreadInfo info, @NotNull Writer f, StackTraceElement @NotNull [] stackTraceElements) {
    try {
      StringBuilder sb = new StringBuilder("\"").append(info.getThreadName()).append("\"");
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

  @ApiStatus.Internal
  public static void dumpCallStack(@NotNull Thread thread, @NotNull Writer f, StackTraceElement @NotNull [] stackTraceElements) {
    try {
      StringBuilder sb = new StringBuilder("\"").append(thread.getName()).append("\"");
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
  @ApiStatus.Internal
  public static @Nullable String getEdtStackForCrash(@NotNull String fullThreadDump, @NotNull String exceptionType) {
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
    sb.append(line, 1, i); // append thread name (e.g., AWT-EventQueue-0)

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
