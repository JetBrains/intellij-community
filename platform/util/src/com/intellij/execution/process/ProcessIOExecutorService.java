package com.intellij.execution.process;

import com.intellij.util.concurrency.CountingThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A thread pool for long-running workers needed for handling child processes or network requests,
 * to avoid occupying workers in the main application pool and constantly creating new threads there.
 * @author peter
 */
public class ProcessIOExecutorService extends ThreadPoolExecutor {
  public static final String POOLED_THREAD_PREFIX = "I/O pool ";
  public static final ExecutorService INSTANCE = new ProcessIOExecutorService();

  private ProcessIOExecutorService() {
    super(1, Integer.MAX_VALUE, 1, TimeUnit.MINUTES, new SynchronousQueue<>(), new CountingThreadFactory() {
      @NotNull
      @Override
      public Thread newThread(@NotNull final Runnable r) {
        Thread thread = new Thread(r, POOLED_THREAD_PREFIX + counter.incrementAndGet());
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
      }
    });
  }

  @TestOnly
  public int getThreadCounter() {
    return ((CountingThreadFactory)getThreadFactory()).getCount();
  }
}
