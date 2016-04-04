/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.io.NettyUtil;
import org.junit.Assert;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author cdr
 */
public class ThreadTracker {
  private final Collection<Thread> before;
  private final boolean myDefaultProjectInitialized;

  @TestOnly
  public ThreadTracker() {
    before = getThreads();
    myDefaultProjectInitialized = ((ProjectManagerImpl)ProjectManager.getInstance()).isDefaultProjectInitialized();
  }

  private static final Method getThreads = ReflectionUtil.getDeclaredMethod(Thread.class, "getThreads");

  @NotNull
  private static Collection<Thread> getThreads() {
    Thread[] threads;
    try {
      // faster than Thread.getAllStackTraces().keySet()
      threads = (Thread[])getThreads.invoke(null);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return ContainerUtilRt.newArrayList(threads);
  }

  private static final Set<String> wellKnownOffenders = new THashSet<String>();
  static {
        wellKnownOffenders.add("Action Updater"); // todo remove
        wellKnownOffenders.add("Alarm pool(own)");
        wellKnownOffenders.add("Alarm pool(shared)");
    wellKnownOffenders.add("AWT-EventQueue-");
    wellKnownOffenders.add("AWT-Shutdown");
    wellKnownOffenders.add("AWT-Windows");
        wellKnownOffenders.add("Change List Updater"); //todo remove
    wellKnownOffenders.add("CompilerThread0");
    wellKnownOffenders.add("Finalizer");
    wellKnownOffenders.add("IDEA Test Case Thread");
    wellKnownOffenders.add("Image Fetcher ");
    wellKnownOffenders.add("Java2D Disposer");
    wellKnownOffenders.add("JobScheduler FJ pool ");
    wellKnownOffenders.add("JPS thread pool");
    wellKnownOffenders.add("Keep-Alive-Timer");
        wellKnownOffenders.add("Low Memory Detector");
    wellKnownOffenders.add("main");
    wellKnownOffenders.add("Monitor Ctrl-Break");
    wellKnownOffenders.add("Reference Handler");
    wellKnownOffenders.add("RMI TCP Connection");
    wellKnownOffenders.add("Signal Dispatcher");
    wellKnownOffenders.add("Netty ");
        wellKnownOffenders.add("timed reference disposer");
    wellKnownOffenders.add("timer-int"); //serverImpl
    wellKnownOffenders.add("timer-sys"); //clientimpl
    wellKnownOffenders.add("TimerQueue");
    wellKnownOffenders.add("UserActivityMonitor thread");
    wellKnownOffenders.add("VM Periodic Task Thread");
    wellKnownOffenders.add("VM Thread");
    wellKnownOffenders.add("YJPAgent-Telemetry");

    longRunningThreadCreated(ApplicationManager.getApplication(), "Periodic tasks thread", "ApplicationImpl pooled thread");
  }

  // marks Thread with this name as long-running, which should be ignored from the thread-leaking checks
  public static void longRunningThreadCreated(@NotNull Disposable parentDisposable,
                                              @NotNull final String... threadNamePrefixes) {
    wellKnownOffenders.addAll(Arrays.asList(threadNamePrefixes));
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        wellKnownOffenders.removeAll(Arrays.asList(threadNamePrefixes));
      }
    });
  }

  @TestOnly
  public void checkLeak() throws AssertionError {
    NettyUtil.awaitQuiescenceOfGlobalEventExecutor(100, TimeUnit.SECONDS);
    try {
      if (myDefaultProjectInitialized != ((ProjectManagerImpl)ProjectManager.getInstance()).isDefaultProjectInitialized()) return;

      Collection<Thread> after = new THashSet<Thread>(getThreads());
      after.removeAll(before);

      for (final Thread thread : after) {
        if (thread == Thread.currentThread()) continue;
        ThreadGroup group = thread.getThreadGroup();
        if (group != null && "system".equals(group.getName()))continue;
        final String name = thread.getName();
        if (ContainerUtil.exists(wellKnownOffenders, new Condition<String>() {
          @Override
          public boolean value(String pattern) {
            return name.contains(pattern);
          }
        })) {
          continue;
        }

        if (!thread.isAlive()) continue;
        if (thread.getStackTrace().length == 0) {
          thread.interrupt();
          if (new WaitFor(10000){
            @Override
            protected boolean condition() {
              return !thread.isAlive();
            }
          }.isConditionRealized()) {
            continue;
          }
        }
        StackTraceElement[] stackTrace = thread.getStackTrace();
        if (stackTrace.length == 0) {
          continue; // ignore threads with empty stack traces for now. Seems they are zombies unwilling to die.
        }

        String trace = "Thread leaked: " + thread+"; " + thread.getState()+" ("+ thread.isAlive()+")\n--- its stacktrace:\n";
        for (final StackTraceElement stackTraceElement : stackTrace) {
          trace += " at "+stackTraceElement +"\n";
        }
        trace += "---\n";
        Assert.fail(trace);
      }
    }
    finally {
      before.clear();
    }
  }

  public static void awaitThreadTerminationWithParentParentGroup(@NotNull final String grandThreadGroup, int timeout, @NotNull TimeUnit unit) {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < start + unit.toMillis(timeout)) {
      Thread jdiThread = ContainerUtil.find(getThreads(), new Condition<Thread>() {
        @Override
        public boolean value(Thread thread) {
          ThreadGroup group = thread.getThreadGroup();
          return group != null && group.getParent() != null && grandThreadGroup.equals(group.getParent().getName());
        }
      });

      if (jdiThread == null) {
        break;
      }
      try {
        long timeLeft = start + unit.toMillis(timeout) - System.currentTimeMillis();
        System.out.println("Waiting for the "+jdiThread+" for " + timeLeft+"ms");
        jdiThread.join(timeLeft);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}