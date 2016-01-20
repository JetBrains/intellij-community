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
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

public class AppScheduledExecutorServiceTest extends TestCase {
  private static class LogInfo {
    private final long time;
    private final int runnable;
    private final Thread currentThread;

    private LogInfo(int runnable) {
      time = System.currentTimeMillis();
      this.runnable = runnable;
      currentThread = Thread.currentThread();
    }
  }

  public void testDelayedWorks() throws InterruptedException {
    final AppScheduledExecutorService service = new AppScheduledExecutorService();
    final List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());
    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());

    service.invokeAll(Collections.nCopies(getBackendTPE(service).getCorePoolSize() + 1, Executors.callable(EmptyRunnable.getInstance()))); // pre-start all threads

    int delay = 1000;

    ScheduledFuture<?> f1 = service.schedule((Runnable)() -> {
      log.add(new LogInfo(1));
      TimeoutUtil.sleep(10);
    }, delay, TimeUnit.MILLISECONDS);
    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());
    assertFalse(getBackendTPE(service).isTerminating());
    ScheduledFuture<?> f2 = service.schedule((Runnable)() -> {
      log.add(new LogInfo(2));
      TimeoutUtil.sleep(10);
    }, delay, TimeUnit.MILLISECONDS);
    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());
    assertFalse(getBackendTPE(service).isTerminating());
    ScheduledFuture<?> f3 = service.schedule((Runnable)() -> {
      log.add(new LogInfo(3));
      TimeoutUtil.sleep(10);
    }, delay, TimeUnit.MILLISECONDS);

    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());
    assertFalse(getBackendTPE(service).isTerminating());
    Future<?> f4 = service.submit((Runnable)() -> log.add(new LogInfo(4)));

    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());
    assertFalse(getBackendTPE(service).isTerminating());
    assertFalse(f1.isDone());
    assertFalse(f2.isDone());
    assertFalse(f3.isDone());

    TimeoutUtil.sleep(delay/2);
    assertFalse(f1.isDone());
    assertFalse(f2.isDone());
    assertFalse(f3.isDone());
    assertTrue(f4.isDone());

    TimeoutUtil.sleep(delay/2+500);
    assertTrue(f1.isDone());
    assertTrue(f2.isDone());
    assertTrue(f3.isDone());
    assertTrue(f4.isDone());


    assertEquals(4, log.size());
    assertEquals(4, log.get(0).runnable);
    Set<Thread> usedThreads = new HashSet<>(Arrays.asList(log.get(1).currentThread, log.get(2).currentThread, log.get(3).currentThread));
    assertEquals(3, usedThreads.size()); // must be executed in parallel

    service.doShutdown();
    assertTrue(service.awaitTermination(10, TimeUnit.SECONDS));
  }

  @NotNull
  private static ThreadPoolExecutor getBackendTPE(@NotNull AppScheduledExecutorService service) {
    return (ThreadPoolExecutor)service.backendExecutorService;
  }

  public void testMustNotBeAbleToShutdown() {
    final AppScheduledExecutorService service = new AppScheduledExecutorService();
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
    service.doShutdown();
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

  public void testDelayedTasksReusePooledThreadIfExecuteAtDifferentTimes() throws InterruptedException, ExecutionException {
    final AppScheduledExecutorService service = new AppScheduledExecutorService();
    final List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());
    // pre-start one thread
    Future<?> future = service.submit(EmptyRunnable.getInstance());
    future.get();
    assertEquals(1, getBackendTPE(service).getPoolSize());

    int delay = 500;

    ScheduledFuture<?> f1 = service.schedule((Runnable)() -> log.add(new LogInfo(1)), delay, TimeUnit.MILLISECONDS);
    ScheduledFuture<?> f2 = service.schedule((Runnable)() -> log.add(new LogInfo(2)), delay + 100, TimeUnit.MILLISECONDS);
    ScheduledFuture<?> f3 = service.schedule((Runnable)() -> log.add(new LogInfo(3)), delay + 200, TimeUnit.MILLISECONDS);

    assertEquals(1, getBackendTPE(service).getPoolSize());

    assertFalse(f1.isDone());
    assertFalse(f2.isDone());
    assertFalse(f3.isDone());

    TimeoutUtil.sleep(delay+200+300);
    assertTrue(f1.isDone());
    assertTrue(f2.isDone());
    assertTrue(f3.isDone());
    assertEquals(1, getBackendTPE(service).getPoolSize());

    assertEquals(3, log.size());
    Set<Thread> usedThreads = new HashSet<>(Arrays.asList(log.get(0).currentThread, log.get(1).currentThread, log.get(2).currentThread));
    if (usedThreads.size() != 1) {
      System.err.println(ThreadDumper.dumpThreadsToString());
    }
    assertEquals(usedThreads.toString(), 1, usedThreads.size()); // must be executed in same thread

    service.doShutdown();
    assertTrue(service.awaitTermination(10, TimeUnit.SECONDS));
  }

  public void testDelayedTasksThatFiredAtTheSameTimeAreExecutedConcurrently() throws InterruptedException, ExecutionException {
    final AppScheduledExecutorService service = new AppScheduledExecutorService();
    final List<LogInfo> log = Collections.synchronizedList(new ArrayList<>());
    int delay = 500;

    int N = 20;
    List<? extends Future<?>> futures =
      ContainerUtil.map(Collections.nCopies(N, ""), s -> service.schedule((Runnable)()-> {
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
    service.doShutdown();
    assertTrue(service.awaitTermination(10, TimeUnit.SECONDS));
  }
}
