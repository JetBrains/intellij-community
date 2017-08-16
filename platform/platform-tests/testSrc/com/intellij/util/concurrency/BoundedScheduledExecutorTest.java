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
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.TimeoutUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedScheduledExecutorTest extends TestCase {
  private static final Logger LOG = Logger.getInstance(BoundedScheduledExecutorTest.class);
  public void testSchedulesAreReallyBound() throws InterruptedException, ExecutionException {
    ExecutorService backendExecutor = AppExecutorUtil.getAppExecutorService();
    for (int maxTasks=1; maxTasks<5;maxTasks++) {
      LOG.debug("maxTasks = " + maxTasks);
      BoundedScheduledExecutorService executor = createBoundedScheduledExecutor(backendExecutor, maxTasks);
      AtomicInteger running = new AtomicInteger();
      AtomicInteger max = new AtomicInteger();
      AtomicInteger executed = new AtomicInteger();
      int N = 10000;
      ScheduledFuture[] futures = new ScheduledFuture[N];
      for (int i = 0; i < N; i++) {
        futures[i] = executor.schedule(() -> {
          int r = running.incrementAndGet();
          try {
            TimeoutUtil.sleep(1);
            max.accumulateAndGet(r, Math::max);
            executed.incrementAndGet();
          }
          finally {
            running.decrementAndGet();
          }
        }, i%10, TimeUnit.MILLISECONDS);
      }
      for (ScheduledFuture future : futures) {
        future.get();
      }
      assertEquals(0, executor.shutdownNow().size());
      assertTrue(executor.awaitTermination(N + N + 100000, TimeUnit.MILLISECONDS));
      assertEquals(maxTasks, max.get());
      assertEquals(N, executed.get());
    }
  }

  @NotNull
  private BoundedScheduledExecutorService createBoundedScheduledExecutor(@NotNull ExecutorService backendExecutor, int maxTasks) {
    return new BoundedScheduledExecutorService(getName(), backendExecutor, maxTasks);
  }

  public void testSubmitsAreReallyBound() throws InterruptedException, ExecutionException {
    ExecutorService backendExecutor = AppExecutorUtil.getAppExecutorService();
    for (int maxTasks=1; maxTasks<5;maxTasks++) {
      LOG.debug("maxTasks = " + maxTasks);
      BoundedScheduledExecutorService executor = createBoundedScheduledExecutor(backendExecutor, maxTasks);
      AtomicInteger running = new AtomicInteger();
      AtomicInteger max = new AtomicInteger();
      AtomicInteger executed = new AtomicInteger();
      int N = 10000;
      Future[] futures = new Future[N];
      for (int i = 0; i < N; i++) {
        futures[i] = executor.submit(() -> {
          int r = running.incrementAndGet();
          try {
            TimeoutUtil.sleep(1);
            max.accumulateAndGet(r, Math::max);
            executed.incrementAndGet();
          }
          finally {
            running.decrementAndGet();
          }
        });
      }
      for (Future future : futures) {
        future.get();
      }
      assertEquals(0, executor.shutdownNow().size());
      assertTrue(executor.awaitTermination(N + N+100000, TimeUnit.MILLISECONDS));
      assertEquals(maxTasks, max.get());
      assertEquals(N, executed.get());
    }
  }

  public void testCallableReallyReturnsValue() throws Exception{
    BoundedScheduledExecutorService executor = createBoundedScheduledExecutor(AppExecutorUtil.getAppExecutorService(), 1);

    Future<Integer> f1 = executor.schedule(() -> 42, 1, TimeUnit.SECONDS);
    Integer result = f1.get();
    assertEquals(42, result.intValue());
    executor.shutdownNow();
    assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
  }

  public void testEarlyCancelPreventsRunning() throws InterruptedException {
    AtomicBoolean run = new AtomicBoolean();
    BoundedScheduledExecutorService executor = createBoundedScheduledExecutor(AppExecutorUtil.getAppExecutorService(), 1);

    int delayMs = 10*1000;
    Future<?> s1 = executor.schedule(EmptyRunnable.getInstance(), delayMs, TimeUnit.MILLISECONDS);
    Future<Integer> f1 = executor.schedule(() -> {
      run.set(true);
      return 42;
    }, delayMs, TimeUnit.MILLISECONDS);
    f1.cancel(false);
    TimeoutUtil.sleep(delayMs + 1000);
    assertTrue(f1.isDone());
    assertTrue(f1.isCancelled());
    assertFalse(run.get());
    assertTrue(s1.isDone());
    executor.shutdownNow();
    assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
  }

  public void testStressWhenSomeTasksCallOtherTasksGet() throws ExecutionException, InterruptedException {
    ExecutorService backendExecutor = AppExecutorUtil.getAppExecutorService();
    for (int maxSimultaneousTasks = 1; maxSimultaneousTasks<20; maxSimultaneousTasks++) {
      LOG.debug("maxSimultaneousTasks = " + maxSimultaneousTasks);
      BoundedScheduledExecutorService executor = createBoundedScheduledExecutor(backendExecutor, maxSimultaneousTasks);
      AtomicInteger running = new AtomicInteger();
      AtomicInteger maxThreads = new AtomicInteger();
      AtomicInteger availableThreads = new AtomicInteger(maxSimultaneousTasks); // to avoid deadlocks when trying to wait inside the pool thread

      try {
        int N = 1000;
        Future[] futures = new Future[N];
        Random random = new Random();
        for (int i = 0; i < N; i++) {
          final int finalI = i;
          final int finalMaxSimultaneousTasks = maxSimultaneousTasks;
          futures[i] = executor.schedule(() -> {
            maxThreads.accumulateAndGet(running.incrementAndGet(), Math::max);

            try {
              int r = random.nextInt(finalMaxSimultaneousTasks);
              int prev = finalI - r;
              if (prev < finalI && prev >= 0) {
                if (availableThreads.decrementAndGet() > 0) {
                  try {
                    futures[prev].get();
                  }
                  catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }
                availableThreads.incrementAndGet();
              }
              TimeoutUtil.sleep(r);
            }
            finally {
              running.decrementAndGet();
            }
          }, i % 10, TimeUnit.MILLISECONDS);
        }
        for (Future future : futures) {
          future.get();
        }
      }
      finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
      }

      assertTrue("Max threads was: "+maxThreads+" but bound was: "+maxSimultaneousTasks, maxThreads.get() <= maxSimultaneousTasks);
    }
  }

  public void testSequentialSchedulesMustExecuteSequentially() throws ExecutionException, InterruptedException {
    BoundedScheduledExecutorService executor = createBoundedScheduledExecutor(AppExecutorUtil.getAppExecutorService(), 1);
    int N = 100000;
    StringBuffer log = new StringBuffer(N*4);
    StringBuilder expected = new StringBuilder(N * 4);

    Future[] futures = new Future[N];
    for (int i = 0; i < N; i++) {
      final int finalI = i;
      //noinspection StringConcatenationInsideStringBufferAppend
      futures[i] = executor.schedule(() -> log.append(finalI+" "), 0, TimeUnit.MILLISECONDS);
    }
    for (int i = 0; i < N; i++) {
      expected.append(i).append(" ");
      futures[i].get();
    }

    String logs = log.toString();
    assertEquals(expected.toString(), logs);
    executor.shutdownNow();
    assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
  }


  public void testShutdownNowMustCancel() throws InterruptedException {
    BoundedScheduledExecutorService executor = createBoundedScheduledExecutor(AppExecutorUtil.getAppExecutorService(), 1);
    int N = 100000;
    StringBuffer log = new StringBuffer(N*4);

    Future[] futures = new Future[N];
    for (int i = 0; i < N; i++) {
      futures[i] = executor.schedule(() -> log.append(" "), 10, TimeUnit.SECONDS);
    }
    List<Runnable> runnables = executor.shutdownNow();
    assertTrue(executor.isShutdown());
    assertEquals(N, runnables.size());

    try {
      executor.schedule(EmptyRunnable.getInstance(), 10, TimeUnit.SECONDS);
      fail("Must reject");
    }
    catch (RejectedExecutionException ignored) {
    }
    try {
      executor.execute(EmptyRunnable.getInstance());
      fail("Must reject");
    }
    catch (RejectedExecutionException ignored) {
    }

    for (int i = 0; i < N; i++) {
      assertTrue(futures[i].isCancelled());
    }

    String logs = log.toString();
    assertEquals("", logs);
    assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
  }

  public void testShutdownMustDisableSubmit() throws InterruptedException {
    BoundedScheduledExecutorService executor = createBoundedScheduledExecutor(AppExecutorUtil.getAppExecutorService(), 1);
    int N = 100000;
    StringBuffer log = new StringBuffer(N*4);

    Future[] futures = new Future[N];
    for (int i = 0; i < N; i++) {
      futures[i] = executor.schedule(() -> log.append(" "), 10, TimeUnit.SECONDS);
    }
    executor.shutdown();
    assertTrue(executor.isShutdown());
    try {
      executor.schedule(EmptyRunnable.getInstance(), 10, TimeUnit.SECONDS);
      fail("Must reject");
    }
    catch (RejectedExecutionException ignored) {
    }
    try {
      executor.execute(EmptyRunnable.getInstance());
      fail("Must reject");
    }
    catch (RejectedExecutionException ignored) {
    }

    for (int i = 0; i < N; i++) {
      assertFalse(futures[i].isCancelled());
      futures[i].cancel(false);
    }

    String logs = log.toString();
    assertEquals("", logs);
    assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
  }

  public void testAwaitTerminationDoesWait() throws InterruptedException {
    for (int maxTasks=1; maxTasks<10;maxTasks++) {
      ExecutorService executor = createBoundedScheduledExecutor(PooledThreadExecutor.INSTANCE, maxTasks);
      int N = 100000;
      StringBuffer log = new StringBuffer(N*4);

      Future[] futures = new Future[N];
      for (int i = 0; i < N; i++) {
        futures[i] = executor.submit(() -> log.append(" "));
      }
      executor.shutdown();
      assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));

      String logs = log.toString();
      assertEquals(N, logs.length());
      for (Future future : futures) {
        assertTrue(future.isDone());
        assertTrue(!future.isCancelled());
      }
    }
  }

  public void testAwaitTerminationDoesNotCompletePrematurely() throws InterruptedException {
    ExecutorService executor2 = createBoundedScheduledExecutor(PooledThreadExecutor.INSTANCE, 1);
    Future<?> future = executor2.submit(() -> TimeoutUtil.sleep(10000));
    executor2.shutdown();
    assertFalse(executor2.awaitTermination(1, TimeUnit.SECONDS));
    assertFalse(future.isDone());
    assertFalse(future.isCancelled());
    assertTrue(executor2.awaitTermination(100, TimeUnit.SECONDS));
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());
  }
}
