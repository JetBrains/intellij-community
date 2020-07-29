// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.util.concurrency.CountingThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.*;

/**
 * A thread pool for long-running workers needed for handling child processes or network requests,
 * to avoid occupying workers in the main application pool and constantly creating new threads there.
 *
 * @author peter
 */
public final class ProcessIOExecutorService extends ThreadPoolExecutor {
  public static final String POOLED_THREAD_PREFIX = "I/O pool ";
  public static final ExecutorService INSTANCE = new ProcessIOExecutorService();

  private ProcessIOExecutorService() {
    super(1, Integer.MAX_VALUE, 1, TimeUnit.MINUTES, new SynchronousQueue<>(), new MyCountingThreadFactory());
  }

  @TestOnly
  public int getThreadCounter() {
    return ((CountingThreadFactory)getThreadFactory()).getCount();
  }

  private static final class MyCountingThreadFactory extends CountingThreadFactory {
    // Ensure that we don't keep the classloader of the plugin which caused this thread to be created
    // in Thread.inheritedAccessControlContext
    private final ThreadFactory myThreadFactory = Executors.privilegedThreadFactory();

    @NotNull
    @Override
    public Thread newThread(@NotNull final Runnable r) {
      Thread thread = myThreadFactory.newThread(r);
      thread.setName(POOLED_THREAD_PREFIX + counter.incrementAndGet());
      thread.setPriority(Thread.NORM_PRIORITY - 1);
      return thread;
    }
  }
}