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

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AppScheduledExecutorServiceTest extends TestCase {
  private static class LogInfo {
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
    service = new AppScheduledExecutorService(getName());
  }

  @Override
  protected void tearDown() throws Exception {
    service.shutdownAppScheduledExecutorService();
    assertTrue(service.awaitTermination(10, TimeUnit.SECONDS));
    service = null;
    super.tearDown();
  }

  public void testDelayedWorks() throws InterruptedException {
    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());

    service.invokeAll(Collections.nCopies(service.getBackendPoolCorePoolSize() + 1, Executors.callable(EmptyRunnable.getInstance()))); // pre-start all threads

    int delay = 1000;
    long start = System.currentTimeMillis();
    List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());
    List<ScheduledFuture<?>> f = IntStream.range(1, 4).mapToObj(i -> service.schedule(() -> {
      log.add(new LogInfo(i));
      TimeoutUtil.sleep(1000);
    }, delay, TimeUnit.MILLISECONDS)).collect(Collectors.toList());

    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());
    Future<?> f4 = service.submit((Runnable)() -> log.add(new LogInfo(4)));

    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());
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

    assertEquals(4, log.size());
    assertEquals(4, log.get(0).runnable);
    List<Thread> threads = Arrays.asList(log.get(1).currentThread, log.get(2).currentThread, log.get(3).currentThread);
    assertEquals(log.toString(), 3, new HashSet<>(threads).size()); // must be executed in parallel
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

  public void testDelayedTasksReusePooledThreadIfExecuteAtDifferentTimes() throws Exception {
    // pre-start one thread
    Future<?> future = service.submit(EmptyRunnable.getInstance());
    future.get();
    service.setBackendPoolCorePoolSize(1);
    assertEquals(1, service.getBackendPoolExecutorSize());

    long submitted = System.currentTimeMillis();
    AtomicBoolean agentOverloaded = new AtomicBoolean();
    int delay = 1000;
    List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());
    AtomicLong f1Start = new AtomicLong();
    AtomicLong f2Start = new AtomicLong();
    AtomicLong f3Start = new AtomicLong();
    ScheduledFuture<?> f1 = service.schedule(() -> {
      f1Start.set(System.currentTimeMillis());
      if (!log.isEmpty()) agentOverloaded.set(true);
      log.add(new LogInfo(1));
    }, delay, TimeUnit.MILLISECONDS);
    ScheduledFuture<?> f2 = service.schedule(() -> {
      f2Start.set(System.currentTimeMillis());
      if (!f1.isDone()) agentOverloaded.set(true);
      log.add(new LogInfo(2));
    }, delay + delay, TimeUnit.MILLISECONDS);
    ScheduledFuture<?> f3 = service.schedule(() -> {
      f3Start.set(System.currentTimeMillis());
      if (!f1.isDone()) agentOverloaded.set(true);
      if (!f2.isDone()) agentOverloaded.set(true);
      log.add(new LogInfo(3));
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
    if (f2Start.get() - f1Start.get() < delay/2 || f3Start.get() - f2Start.get() < delay/2) agentOverloaded.set(true);
    if (agentOverloaded.get()) {
      System.err.println("This agent is seriously thrashing. I give up.");
      return; // no no no no. something terribly wrong is happening right now. This agent is so crazily overloaded it makes no sense to test any further.
    }
    try {
      assertEquals(1, service.getBackendPoolExecutorSize());

      assertEquals(3, log.size());
      Set<Thread> usedThreads = new HashSet<>(Arrays.asList(log.get(0).currentThread, log.get(1).currentThread, log.get(2).currentThread));
      assertEquals(usedThreads.toString(), 1, usedThreads.size()); // must be executed in same thread
    }
    catch (AssertionError e) {
      System.err.println("ThreadDump: "+ThreadDumper.dumpThreadsToString());
      System.err.println("Process List: "+LogUtil.getProcessList());
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
    Set<Thread> usedThreads = ContainerUtil.map2Set(log, logInfo -> logInfo.currentThread);

    assertEquals(N, usedThreads.size());
  }

  public void testAwaitTerminationMakesSureTasksTransferredToBackendExecutorAreFinished() throws InterruptedException {
    final AppScheduledExecutorService service = new AppScheduledExecutorService(getName());
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
      String s = queuedTasks.stream().map(BoundedTaskExecutor::info).collect(Collectors.toList()).toString();
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
    while (System.currentTimeMillis() < start + 60000) {
      if (runnable.getAsBoolean()) return;
      TimeoutUtil.sleep(1);
    }
    throw new RuntimeException(new TimeoutException());
  }
}
