// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AppScheduledExecutorServiceTest extends TestCase {
  private static final class LogInfo {
    private final int runnable;
    private final Thread currentThread;

    private LogInfo(int runnable) {
      this.runnable = runnable;
      currentThread = Thread.currentThread();
    }

    @Override
    public String toString() {
      return "LogInfo{" +
             "runnable=" + runnable +
             ", currentThread=" + currentThread +
             '}';
    }
  }

  private AppScheduledExecutorService service;
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    service = new AppScheduledExecutorService(getName(), 1, TimeUnit.HOURS);
    // LowMemoryWatcherManager submits something immediately
    service.waitForLowMemoryWatcherManagerInit(1, TimeUnit.MINUTES);
  }

  @Override
  protected void tearDown() throws Exception {
    //noinspection SSBasedInspection
    try {
      service.shutdownAppScheduledExecutorService();
      assertTrue(service.awaitTermination(10, TimeUnit.SECONDS));
    }
    finally {
      service = null;

      super.tearDown();
    }
  }

  public void testDelayedWorks() throws Exception {
    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());
    // avoid conflicts when thread are timed out/restarted, since we are inclined to count them all in this test
    ((AppScheduledExecutorService.BackendThreadPoolExecutor)service.backendExecutorService).superSetKeepAliveTime(1, TimeUnit.MINUTES);
    int N = 3;
    CountDownLatch c = new CountDownLatch(1);
    // pre-start all threads
    List<Future<Boolean>> futures = ContainerUtil.map(Collections.nCopies(N, null), __ -> service.submit(() -> c.await(1, TimeUnit.MINUTES)));
    c.countDown();
    for (Future<Boolean> future : futures) {
      future.get();
    }

    int size = service.getBackendPoolExecutorSize();
    Assume.assumeTrue("Too low pool parallelism: " + size + " (required at least " + N + ")", size == N);

    int delay = 1000;
    long start = System.currentTimeMillis();
    List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());
    List<ScheduledFuture<?>> f = IntStream.range(1, N+1).mapToObj(i -> service.schedule(() -> {
      log.add(new LogInfo(i));
      TimeoutUtil.sleep(1000);
    }, delay, TimeUnit.MILLISECONDS)).collect(Collectors.toList());

    Future<?> f4 = service.submit((Runnable)() -> log.add(new LogInfo(0)));

    assertTrue(f.stream().noneMatch(Future::isDone));

    TimeoutUtil.sleep(delay/2);
    Stream<Pair<? extends ScheduledFuture<?>, Boolean>> done = f.stream().map(f1 -> Pair.create(f1, f1.isDone()));
    long elapsed = System.currentTimeMillis() - start; // can be > delay/2 on overloaded agent
    if (elapsed < delay) {
      // before delay tasks must not be completed for sure.
      // after that - who knows if the task started execution but didn't finish yet
      done.forEach(pair -> assertFalse(pair.first+": done: "+pair.first.isDone(), pair.second));
    }

    assertTrue(f4.isDone());

    f.forEach(f1->waitFor(f1::isDone));

    assertEquals(N+1, log.size());
    assertEquals(0, log.get(0).runnable); // first executed must be not-delayed task
    Set<Thread> threads = ContainerUtil.map2Set(log, l->l.currentThread);
    assertEquals(log.toString(), N, threads.size()); // must be executed in parallel
  }

  public void testMustNotBeAbleToShutdown() {
    try {
      service.shutdown();
      fail();
    }
    catch (Exception ignored) {
    }
    try {
      service.shutdownNow();
      fail();
    }
    catch (Exception ignored) {
    }
  }

  public void testMustNotBeAbleToShutdownGlobalPool() {
    ExecutorService service = AppExecutorUtil.getAppExecutorService();
    try {
      service.shutdown();
      fail();
    }
    catch (Exception ignored) {
    }
    try {
      service.shutdownNow();
      fail();
    }
    catch (Exception ignored) {
    }
    try {
      ((ThreadPoolExecutor)service).setThreadFactory(Thread::new);
      fail();
    }
    catch (Exception ignored) {
    }
    try {
      ((ThreadPoolExecutor)service).setCorePoolSize(0);
      fail();
    }
    catch (Exception ignored) {
    }
  }

  public void testDelayedTasksReusePooledThreadIfExecuteAtDifferentTimes() {
    service.setBackendPoolCorePoolSize(1);
    ((ThreadPoolExecutor)service.backendExecutorService).prestartCoreThread();
    assertEquals(1, service.getBackendPoolExecutorSize());

    service.setNewThreadListener((thread, runnable) -> {
      Runnable firstTask = ReflectionUtil.getField(runnable.getClass(), runnable, Runnable.class, "firstTask");
      System.err.println("Unexpected new thread created: " + thread + "; for first task "+firstTask+"; thread dump:\n" + ThreadDumper.dumpThreadsToString());
      fail();
    });

    long submitted = System.currentTimeMillis();
    AtomicBoolean agentOverloaded = new AtomicBoolean();
    int delay = 1000;
    List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());
    AtomicLong f1Start = new AtomicLong();
    AtomicLong f2Start = new AtomicLong();
    AtomicLong f3Start = new AtomicLong();
    ScheduledFuture<?> f1 = service.schedule(new Runnable() {
      @Override
      public void run() {
        f1Start.set(System.currentTimeMillis());
        if (!log.isEmpty()) agentOverloaded.set(true);
        log.add(new LogInfo(1));
      }

      @Override
      public String toString() {
        return "f1";
      }
    }, delay, TimeUnit.MILLISECONDS);
    ScheduledFuture<?> f2 = service.schedule(new Runnable() {
      @Override
      public void run() {
        f2Start.set(System.currentTimeMillis());
        if (!f1.isDone()) agentOverloaded.set(true);
        log.add(new LogInfo(2));
      }

      @Override
      public String toString() {
        return "f2";
      }
    }, delay + delay, TimeUnit.MILLISECONDS);
    ScheduledFuture<?> f3 = service.schedule(new Runnable() {
      @Override
      public void run() {
        f3Start.set(System.currentTimeMillis());
        if (!f1.isDone()) agentOverloaded.set(true);
        if (!f2.isDone()) agentOverloaded.set(true);
        log.add(new LogInfo(3));
      }

      @Override
      public String toString() {
        return "f3";
      }
    }, delay + delay + delay, TimeUnit.MILLISECONDS);

    assertEquals(1, service.getBackendPoolExecutorSize());
    long now = System.currentTimeMillis();
    if (now > submitted + delay - 100) agentOverloaded.set(true);
    if (!agentOverloaded.get()) {
      assertFalse(f1.isDone());
      assertFalse(f2.isDone());
      assertFalse(f3.isDone());
    }

    waitFor(f1::isDone);
    waitFor(f2::isDone);
    waitFor(f3::isDone);
    if (f2Start.get() - f1Start.get() < delay/2 || f3Start.get() - f2Start.get() < delay/2) {
      agentOverloaded.set(true);
    }
    if (agentOverloaded.get()) {
      System.err.println("This agent is seriously thrashing. I give up.");
      return; // no no no no. something terribly wrong is happening right now. This agent is so crazily overloaded it makes no sense to test any further.
    }
    try {
      assertEquals(1, service.getBackendPoolExecutorSize());

      assertEquals(3, log.size());
      Set<Thread> usedThreads = ContainerUtil.map2Set(log, l->l.currentThread);
      assertEquals(usedThreads.toString(), 1, usedThreads.size()); // must be executed in same thread
    }
    catch (AssertionError e) {
      System.err.println("ThreadDump: " + ThreadDumper.dumpThreadsToString());
      System.err.println("Process List: " + LogUtil.getProcessList());
      throw e;
    }
  }

  public void testDelayedTasksThatFiredAtTheSameTimeAreExecutedConcurrently() throws InterruptedException, ExecutionException {
    final List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());
    int delay = 500;

    int N = 20;
    List<? extends Future<?>> futures =
      ContainerUtil.map(Collections.nCopies(N, ""), __ -> service.schedule(()-> {
        log.add(new LogInfo(0));
        TimeoutUtil.sleep(10 * 1000);
      }
        , delay, TimeUnit.MILLISECONDS
      ));

    for (Future<?> future : futures) {
      future.get();
    }

    assertEquals(N, log.size());
    Set<Thread> usedThreads = ContainerUtil.map2Set(log, l -> l.currentThread);

    assertEquals(N, usedThreads.size());
  }

  public void testAwaitTerminationMakesSureTasksTransferredToBackendExecutorAreFinished() throws InterruptedException {
    final AppScheduledExecutorService service = new AppScheduledExecutorService(getName(), 1, TimeUnit.HOURS);
    final List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());

    int N = 20;
    int delay = 500;
    List<? extends Future<?>> futures =
      ContainerUtil.map(Collections.nCopies(N, ""), s -> service.schedule(() -> {
          TimeoutUtil.sleep(5000);
          log.add(new LogInfo(0));
        }, delay, TimeUnit.MILLISECONDS
      ));
    TimeoutUtil.sleep(delay);
    long start = System.currentTimeMillis();
    while (!service.delayQueue.isEmpty()) {
      // wait till all tasks transferred to backend
      if (System.currentTimeMillis() > start + 20000) throw new AssertionError("Not transferred after 20 seconds");
    }
    List<SchedulingWrapper.MyScheduledFutureTask> queuedTasks = new ArrayList<>(service.delayQueue);
    if (!queuedTasks.isEmpty()) {
      String s = ContainerUtil.map(queuedTasks, BoundedTaskExecutor::info).toString();
      fail("Queued tasks left: "+s + ";\n"+queuedTasks);
    }
    service.shutdownAppScheduledExecutorService();
    assertTrue(service.awaitTermination(20, TimeUnit.SECONDS));

    for (Future<?> future : futures) {
      assertTrue(future.isDone());
    }
    assertEquals(log.toString(), N, log.size());
  }

  private static void waitFor(@NotNull BooleanSupplier runnable) throws RuntimeException {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < start + 60_000) {
      if (runnable.getAsBoolean()) return;
      TimeoutUtil.sleep(1);
    }
    throw new RuntimeException(new TimeoutException());
  }
}
