/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author yole
 */
public class ThreadDumper {
  private ThreadDumper() {
  }

  @NotNull
  public static String dumpThreadsToString() {
    StringWriter writer = new StringWriter();
    dumpThreadsToFile(ManagementFactory.getThreadMXBean(), writer);
    return writer.toString();
  }
  
  @NotNull
  public static String dumpEdtStackTrace(ThreadInfo[] threadInfos) {
    StringWriter writer = new StringWriter();
    if (threadInfos.length > 0) {
      StackTraceElement[] trace = threadInfos[0].getStackTrace();
      printStackTrace(writer, trace);
    }
    return writer.toString();
  }

  @NotNull
  public static ThreadInfo[] getThreadInfos() {
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    return sort(threadMXBean.dumpAllThreads(false, false));
  }

  @NotNull
  public static ThreadDump getThreadDumpInfo(@NotNull final ThreadMXBean threadMXBean) {
    StringWriter writer = new StringWriter();
    StackTraceElement[] edtStack = dumpThreadsToFile(threadMXBean, writer);
    return new ThreadDump(writer.toString(), edtStack);
  }

  @Nullable
  private static StackTraceElement[] dumpThreadsToFile(@NotNull ThreadMXBean threadMXBean, @NotNull Writer f) {
    StackTraceElement[] edtStack = null;
    boolean dumpSuccessful = false;

    try {
      ThreadInfo[] threads = sort(threadMXBean.dumpAllThreads(false, false));
      edtStack = dumpThreadInfos(threads, f);
      dumpSuccessful = true;
    }
    catch (Exception ignored) {

    }

    if (!dumpSuccessful) {
      final long[] threadIds = threadMXBean.getAllThreadIds();
      final ThreadInfo[] threadInfo = sort(threadMXBean.getThreadInfo(threadIds, Integer.MAX_VALUE));
      edtStack = dumpThreadInfos(threadInfo, f);
    }

    return edtStack;
  }

  private static StackTraceElement[] dumpThreadInfos(@NotNull ThreadInfo[] threadInfo, @NotNull Writer f) {
    StackTraceElement[] edtStack = null;
    for (ThreadInfo info : threadInfo) {
      if (info != null) {
        if (info.getThreadName().equals("AWT-EventQueue-1")) {
          edtStack = info.getStackTrace();
        }
        dumpThreadInfo(info, f);
      }
    }
    return edtStack;
  }

  @NotNull
  private static ThreadInfo[] sort(@NotNull ThreadInfo[] threads) {
    Arrays.sort(threads, (o1, o2) -> {
      boolean awt1 = o1.getThreadName().startsWith("AWT-EventQueue");
      boolean awt2 = o2.getThreadName().startsWith("AWT-EventQueue");
      if (awt1 && !awt2) return -1;
      if (awt2 && !awt1) return 1;
      boolean r1 = o1.getThreadState() == Thread.State.RUNNABLE;
      boolean r2 = o2.getThreadState() == Thread.State.RUNNABLE;
      if (r1 && !r2) return -1;
      if (r2 && !r1) return 1;
      return 0;
    });

    return threads;
  }

  private static void dumpThreadInfo(@NotNull ThreadInfo info, @NotNull Writer f) {
    dumpCallStack(info, f, info.getStackTrace());
  }

  private static void dumpCallStack(@NotNull ThreadInfo info, @NotNull Writer f, @NotNull StackTraceElement[] stackTraceElements) {
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

  public static void dumpCallStack(@NotNull Thread thread, @NotNull Writer f, @NotNull StackTraceElement[] stackTraceElements) {
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

  private static void printStackTrace(@NotNull Writer f, @NotNull StackTraceElement[] stackTraceElements) {
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
    sb.append(exceptionType + ": ");
    sb.append(line.substring(1, i)); // append thread name (e.g. AWT-EventQueue-0)

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
