/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import gnu.trove.THashSet;
import junit.framework.Assert;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author cdr
 */
public class ThreadTracker {
  private final Collection<Thread> before;
  private final boolean myDefaultProjectInitialized;

  public ThreadTracker() {
    before = getThreads();
    myDefaultProjectInitialized = ((ProjectManagerImpl)ProjectManager.getInstance()).isDefaultProjectInitialized();
  }

  private static Collection<Thread> getThreads() {
    //todo enable
    return Collections.emptyList(); //Thread.getAllStackTraces().keySet();
  }

  private static final Set<String> wellKnownOffenders = new THashSet<String>(){{
    add("Alarm pool(own)");
    add("Alarm pool(shared)");
    add("ApplicationImpl pooled thread");
    add("AWT-Shutdown");
    add("AWT-Windows");
    add("CompilerThread0");
    add("Finalizer");
    add("FS Synchronizer");
    add("IDEA Test Case Thread");
    add("Image Fetcher 0");
    add("Java2D Disposer");
    add("Low Memory Detector");
    add("main");
    add("Monitor Ctrl-Break");
    add("Periodic tasks thread");
    add("Reference Handler");
    add("Signal Dispatcher");
    add("SimpleTimer");
    add("timed reference disposer");
    add("timer-int"); //serverImpl
    add("timer-sys"); //clientimpl
    add("TimerQueue");
    add("UserActivityMonitor thread");
    add("VM Periodic Task Thread");
    add("VM Thread");
    add("YJPAgent-Telemetry");




    add("Change List Updater");
  }};
  public void checkLeak() throws AssertionError {
    try {
      if (myDefaultProjectInitialized != ((ProjectManagerImpl)ProjectManager.getInstance()).isDefaultProjectInitialized()) return;

      Collection<Thread> after = new THashSet<Thread>(getThreads());
      after.removeAll(before);

      for (Thread thread : after) {
        if (thread == Thread.currentThread()) continue;
        ThreadGroup group = thread.getThreadGroup();
        if (group != null && "system".equals(group.getName()))continue;
        String name = thread.getName();
        if (name.startsWith("AWT-EventQueue-0")) continue;
        if (name.startsWith("JobScheduler pool ")) continue;
        if (wellKnownOffenders.contains(name)) continue;

        String trace = "Thread leaked: " + thread+": "+ name +";\n ";
        for (final StackTraceElement stackTraceElement : thread.getStackTrace()) {
          trace += " at "+stackTraceElement +"\n";
        }
        Assert.fail(trace);
      }
    }
    finally {
      before.clear();
    }
  }
}