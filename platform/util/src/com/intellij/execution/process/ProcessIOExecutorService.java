// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.concurrency.CountingThreadFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.*;

/**
 * A thread pool for long-running workers needed for handling child processes or network requests,
 * to avoid occupying workers in the main application pool and constantly creating new threads there.
 */
public final class ProcessIOExecutorService extends ThreadPoolExecutor {
  public static final @NonNls String POOLED_THREAD_PREFIX = "I/O pool ";
  public static final ExecutorService INSTANCE = new ProcessIOExecutorService();

  private ProcessIOExecutorService() {
    super(1, Integer.MAX_VALUE, 1, TimeUnit.MINUTES, new SynchronousQueue<>(), new MyCountingThreadFactory());
  }

  @TestOnly
  public int getThreadCounter() {
    return ((CountingThreadFactory)getThreadFactory()).getCount();
  }

  @Override
  public void execute(@NotNull Runnable command) {
    super.execute(AppScheduledExecutorService.capturePropagationAndCancellationContext(command));
  }

  private static final class MyCountingThreadFactory extends CountingThreadFactory {
    // Ensure that we don't keep the classloader of the plugin which caused this thread to be created
    // in Thread.inheritedAccessControlContext
    private final ThreadFactory myThreadFactory = Executors.privilegedThreadFactory();

    @Override
    public @NotNull Thread newThread(final @NotNull Runnable r) {
      Thread thread = myThreadFactory.newThread(r);
      thread.setName(POOLED_THREAD_PREFIX + counter.incrementAndGet());
      thread.setPriority(Thread.NORM_PRIORITY - 1);
      return thread;
    }
  }
}