/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * @author yole
 */
public class ThreadDumper {
  private ThreadDumper() {
  }

  public static String dumpThreadsToString() {
    StringWriter writer = new StringWriter();
    dumpThreadsToFile(ManagementFactory.getThreadMXBean(), writer);
    return writer.toString();
  }

  @Nullable
  static StackTraceElement[] dumpThreadsToFile(final ThreadMXBean threadMXBean, final Writer f) {
    StackTraceElement[] edtStack = null;
    boolean dumpSuccessful = false;

    try {
      ThreadInfo[] threads = threadMXBean.dumpAllThreads(false, false);
      for(ThreadInfo info: threads) {
        if (info != null) {
          if (info.getThreadName().equals("AWT-EventQueue-1")) {
            edtStack = info.getStackTrace();
          }
          dumpThreadInfo(info, f);
        }
      }
      dumpSuccessful = true;
    }
    catch (Exception ignored) {

    }

    if (!dumpSuccessful) {
      final long[] threadIds = threadMXBean.getAllThreadIds();
      final ThreadInfo[] threadInfo = threadMXBean.getThreadInfo(threadIds, Integer.MAX_VALUE);
      for (ThreadInfo info : threadInfo) {
        if (info != null) {
          if (info.getThreadName().equals("AWT-EventQueue-1")) {
            edtStack = info.getStackTrace();
          }
          dumpThreadInfo(info, f);
        }
      }
    }

    return edtStack;
  }

  private static void dumpThreadInfo(final ThreadInfo info, final Writer f) {
    dumpCallStack(info, f, info.getStackTrace());
  }

  private static void dumpCallStack(final ThreadInfo info, final Writer f, final StackTraceElement[] stackTraceElements) {
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
      e.printStackTrace();
    }
  }

  private static String getReadableState(Thread.State state) {
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
