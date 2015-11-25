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
package com.intellij.testFramework;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Condition;
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
    wellKnownOffenders.add("ApplicationImpl pooled thread");
    wellKnownOffenders.add("AWT-EventQueue-");
    wellKnownOffenders.add("AWT-Shutdown");
    wellKnownOffenders.add("AWT-Windows");
        wellKnownOffenders.add("Change List Updater"); //todo remove
    wellKnownOffenders.add("CompilerThread0");
        wellKnownOffenders.add("Document commit thread");
    wellKnownOffenders.add("Finalizer");
        wellKnownOffenders.add("FS Synchronizer"); //todo remove
    wellKnownOffenders.add("IDEA Test Case Thread");
    wellKnownOffenders.add("Image Fetcher ");
    wellKnownOffenders.add("Java2D Disposer");
    wellKnownOffenders.add("JobScheduler FJ pool ");
    wellKnownOffenders.add("JPS thread pool");
    wellKnownOffenders.add("Keep-Alive-Timer");
        wellKnownOffenders.add("Low Memory Detector");
    wellKnownOffenders.add("main");
    wellKnownOffenders.add("Monitor Ctrl-Break");
    wellKnownOffenders.add("Periodic tasks thread");
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
  }

  @TestOnly
  public void checkLeak() throws AssertionError {
    BaseOSProcessHandler.awaitQuiescence(100, TimeUnit.SECONDS);
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

        String trace = "Thread leaked: " + thread+"; " + thread.getState()+" ("+ thread.isAlive()+")\n--- its stacktrace:\n";
        for (final StackTraceElement stackTraceElement : thread.getStackTrace()) {
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
}