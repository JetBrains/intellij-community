// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexImpl;
import com.intellij.testFramework.common.ThreadLeakTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ThreadTracker {
  private static final Logger LOG = Logger.getInstance(ThreadTracker.class);

  private final Map<String, Thread> before;
  private final boolean myDefaultProjectInitialized;

  @TestOnly
  public ThreadTracker() {
    before = ThreadLeakTracker.getThreads();
    myDefaultProjectInitialized = ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized();
  }

  @TestOnly
  public void checkLeak() throws AssertionError {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((FileBasedIndexEx)FileBasedIndex.getInstance()).waitUntilIndicesAreInitialized();
    ((StubIndexImpl)StubIndex.getInstance()).waitUntilStubIndexedInitialized();
    ThreadLeakTracker.awaitQuiescence();
    try {
      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceExIfCreated();
      if (projectManager != null && myDefaultProjectInitialized != projectManager.isDefaultProjectInitialized()) {
        return;
      }

      ThreadLeakTracker.checkLeak(before);
    }
    finally {
      before.clear();
    }
  }

  public static void awaitJDIThreadsTermination(int timeout, @NotNull TimeUnit unit) {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < start + unit.toMillis(timeout)) {
      Thread jdiThread = ContainerUtil.find(ThreadLeakTracker.getThreads().values(), thread -> {
        ThreadGroup group = thread.getThreadGroup();
        return group != null && group.getParent() != null && "JDI main".equals(group.getParent().getName());
      });

      if (jdiThread == null) {
        break;
      }
      try {
        long timeLeft = start + unit.toMillis(timeout) - System.currentTimeMillis();
        LOG.debug("Waiting for the "+jdiThread+" for " + timeLeft+"ms");
        jdiThread.join(timeLeft);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}