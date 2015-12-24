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
    Arrays.sort(threads, new Comparator<ThreadInfo>() {
      @Override
      public int compare(ThreadInfo o1, ThreadInfo o2) {
        final String t1 = o1.getThreadName();
        final String t2 = o2.getThreadName();
        if (t1.startsWith("AWT-EventQueue")) return -1;
        if (t2.startsWith("AWT-EventQueue")) return 1;
        final boolean r1 = o1.getThreadState() == Thread.State.RUNNABLE;
        final boolean r2 = o2.getThreadState() == Thread.State.RUNNABLE;
        if (r1 && !r2) return -1;
        if (r2 && !r1) return 1;
        return 0;
      }
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
      for (StackTraceElement element : stackTraceElements) {
        f.write("\tat " + element.toString() + "\n");
      }
      f.write("\n");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
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
