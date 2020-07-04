// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.TripleFunction;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.IntUnaryOperator;

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

  public void testStressWhenSomeTasksCallOtherTasksGet() throws Exception {
    doTestBoundedExecutor(
      getName(),
      (backendExecutor, maxSimultaneousTasks) -> createBoundedScheduledExecutor(backendExecutor, maxSimultaneousTasks),
      maxSimultaneousTasks -> 1000,
      (executor, runnable, i)-> ((BoundedScheduledExecutorService)executor).schedule(runnable, i % 10, TimeUnit.MILLISECONDS));
  }

  static void doTestBoundedExecutor(String testName,
                                    BiFunction<ExecutorService, Integer, ? extends ExecutorService> executorCreator,
                                    IntUnaryOperator numberOfFuturesComputer,
                                    TripleFunction<ExecutorService, Runnable, Integer, Future<?>> executorScheduler) throws Exception {
    ExecutorService backendExecutor = Executors.newCachedThreadPool(ConcurrencyUtil.newNamedThreadFactory(testName));
    for (int maxSimultaneousTasks = 1; maxSimultaneousTasks < 20; maxSimultaneousTasks++) {
      LOG.debug("maxSimultaneousTasks = " + maxSimultaneousTasks);
      ExecutorService executor = executorCreator.apply(backendExecutor, maxSimultaneousTasks);
      AtomicInteger running = new AtomicInteger();
      AtomicInteger maxThreads = new AtomicInteger();
      AtomicInteger availableThreads =
        new AtomicInteger(maxSimultaneousTasks); // to avoid deadlocks when trying to wait inside the pool thread

      try {
        int N = numberOfFuturesComputer.applyAsInt(maxSimultaneousTasks);
        Future[] futures = new Future[N];
        Random random = new Random();
        for (int i = 0; i < N; i++) {
          final int finalI = i;
          int maxDelayMs = Math.min(5, maxSimultaneousTasks);
          Runnable runnable = () -> {
            maxThreads.accumulateAndGet(running.incrementAndGet(), Math::max);

            try {
              int r = random.nextInt(maxDelayMs);
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
          };
          futures[i] = executorScheduler.fun(executor, runnable, i);
        }
        for (Future future : futures) {
          future.get();
        }
      }
      finally {
        executor.shutdownNow();
        if (!executor.awaitTermination(100, TimeUnit.SECONDS)) fail(ThreadDumper.dumpThreadsToString());
      }

      assertTrue("Max threads was: " + maxThreads + " but bound was: " + maxSimultaneousTasks, maxThreads.get() <= maxSimultaneousTasks);
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
      assertTrue(futures[i].isCancelled());
    }

    String logs = log.toString();
    assertEquals("", logs);
    assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
  }

  public void testAwaitTerminationDoesWait() throws InterruptedException {
    for (int maxTasks=1; maxTasks<10;maxTasks++) {
      ExecutorService executor = createBoundedScheduledExecutor(AppExecutorUtil.getAppExecutorService(), maxTasks);
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
    ExecutorService executor2 = createBoundedScheduledExecutor(AppExecutorUtil.getAppExecutorService(), 1);
    Future<?> future = executor2.submit(() -> TimeoutUtil.sleep(10000));
    executor2.shutdown();
    assertFalse(executor2.awaitTermination(1, TimeUnit.SECONDS));
    assertFalse(future.isDone());
    assertFalse(future.isCancelled());
    assertTrue(executor2.awaitTermination(100, TimeUnit.SECONDS));
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());
  }

  public void testAwaitTerminationOfScheduledTask() throws InterruptedException {
    ScheduledExecutorService executor = createBoundedScheduledExecutor(AppExecutorUtil.getAppExecutorService(), 1);
    Future<?> future = executor.schedule(() -> TimeoutUtil.sleep(10000), 100, TimeUnit.MILLISECONDS);
    executor.shutdown();
    assertTrue(future.isDone());
    assertTrue(future.isCancelled());
    assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
    assertTrue(future.isDone());
    assertTrue(future.isCancelled());
  }
}
