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
package com.intellij.concurrency;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.TestTimeOut;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.runner.RunWith;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("SuspiciousPackagePrivateAccess")
@RunWith(JUnit38AssumeSupportRunner.class)
public class JobLauncherImplTest extends LightPlatformTestCase {
  private static final AtomicInteger COUNT = new AtomicInteger();
  private TestTimeOut t;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    COUNT.set(0);
    t = TestTimeOut.setTimeout(2, TimeUnit.MINUTES);
    int parallelism = JobSchedulerImpl.getJobPoolParallelism();
    Assume.assumeTrue("Too low parallelism: I need at least 4, not this pathetic " + parallelism, parallelism >= 4);
  }

  public void testProcessQueueWorksWithSingleWorker() {
    var tombStone = "tombStone";
    var queue = new ArrayBlockingQueue<String>(2);
    queue.add("item");
    queue.add(tombStone);
    var processed = new ArrayList<String>();

    boolean result = new JobLauncherImpl(ForkJoinPool.commonPool(), 1).processQueue(
      queue,
      new ConcurrentLinkedQueue<>(),
      new EmptyProgressIndicator(),
      tombStone,
      item -> {
        processed.add(item);
        return true;
      });

    assertTrue(result);
    assertEquals(List.of("item"), processed);
  }

  public void testProcessQueueCancellationStopsWorkersWaitingForElements() throws Exception {
    var workerWaiting = new CountDownLatch(1);
    var things = new LinkedBlockingQueue<>() {
      @Override
      public @NotNull Object take() throws InterruptedException {
        workerWaiting.countDown();
        return super.take();
      }

      @Override
      public Object poll(long timeout, TimeUnit unit) throws InterruptedException {
        workerWaiting.countDown();
        return super.poll(timeout, unit);
      }
    };
    var item = new Object();
    var tombStone = new Object();
    things.add(item);
    var indicator = new DaemonProgressIndicator();
    indicator.start();
    var cancellationObserved = new AtomicBoolean();
    var launcher = new JobLauncherImpl(ForkJoinPool.commonPool(), 4);
    var job = launcher.submitToJobThread(() -> {
      try {
        launcher.processQueue(things, new ConcurrentLinkedQueue<>(), indicator, tombStone, _ -> false);
      }
      catch (ProcessCanceledException _) {
        cancellationObserved.set(true);
      }
    }, null);

    try {
      assertTrue("A worker did not start waiting for an element", workerWaiting.await(10, TimeUnit.SECONDS));
      indicator.cancel();
      assertTrue("The queue processing did not stop after cancellation", job.waitForCompletion(10_000));
      assertTrue("The queue processing did not report cancellation", cancellationObserved.get());
    }
    finally {
      things.put(tombStone);
      try {
        cancelAndWait(List.of(job));
      }
      finally {
        indicator.stop();
      }
    }
  }

  public void testConcurrentProcessingDoesNotStarveForkJoinPool() throws Exception {
    assertCompletesWithSaturatedPool(launcher -> {
      var processed = new AtomicInteger();
      assertTrue(ProgressManager.getInstance().runProcess(() ->
        launcher.invokeConcurrentlyUnderContextProgress(List.of(1, 2), _ -> {
          processed.incrementAndGet();
          return true;
        }), new EmptyProgressIndicator()));
      assertEquals(2, processed.get());
    });
  }

  public void testJobWaitDoesNotStarveForkJoinPool() throws Exception {
    assertCompletesWithSaturatedPool(launcher -> {
      var ran = new AtomicBoolean();
      var job = launcher.submitToJobThread(() -> ran.set(true), null);
      assertTrue("The job did not finish", job.waitForCompletion(5_000));
      assertTrue(ran.get());
    });
  }

  public void testQueueProcessingDoesNotStarveForkJoinPool() throws Exception {
    assertCompletesWithSaturatedPool(launcher -> {
      var tombStone = new Object();
      var queue = new LinkedBlockingQueue<>(List.of(new Object(), new Object(), tombStone));
      var processed = new AtomicInteger();
      assertTrue(launcher.processQueue(queue, new ConcurrentLinkedQueue<>(), new EmptyProgressIndicator(), tombStone, _ -> {
        processed.incrementAndGet();
        return true;
      }));
      assertEquals(2, processed.get());
    });
  }

  public void testConcurrentProcessingDoesNotStarveForkJoinPoolWithBlockedWorkers() throws Exception {
    assertCompletesWithBlockedPoolWorkers(launcher -> {
      var processed = new AtomicInteger();
      assertTrue(ProgressManager.getInstance().runProcess(() ->
        launcher.invokeConcurrentlyUnderContextProgress(List.of(1, 2), _ -> {
          processed.incrementAndGet();
          return true;
        }), new EmptyProgressIndicator()));
      assertEquals(2, processed.get());
    });
  }

  public void testJobWaitDoesNotStarveForkJoinPoolWithBlockedWorkers() throws Exception {
    assertCompletesWithBlockedPoolWorkers(launcher -> {
      var ran = new AtomicBoolean();
      var job = launcher.submitToJobThread(() -> ran.set(true), null);
      assertTrue("The job did not finish", job.waitForCompletion(500));
      assertTrue(ran.get());
    });
  }

  public void testQueueProcessingDoesNotStarveForkJoinPoolWithBlockedWorkers() throws Exception {
    assertCompletesWithBlockedPoolWorkers(launcher -> {
      var tombStone = new Object();
      var queue = new LinkedBlockingQueue<>(List.of(new Object(), new Object(), tombStone));
      var processed = new AtomicInteger();
      assertTrue(launcher.processQueue(queue, new ConcurrentLinkedQueue<>(), new EmptyProgressIndicator(), tombStone, _ -> {
        processed.incrementAndGet();
        return true;
      }));
      assertEquals(2, processed.get());
    });
  }

  public void testJobWaitDoesNotStarveCompletelyBlockedForkJoinPool() throws Exception {
    assertCompletesWithBlockedPoolWorkers(launcher -> {
      var job = launcher.submitToJobThread(() -> assertTrue(Thread.currentThread().isVirtual()), null);
      assertTrue("The job did not finish", job.waitForCompletion(500));
    }, false);
  }

  public void testConcurrentProcessingPreservesCallerInterruptWithBlockedPoolWorkers() throws Exception {
    assertCompletesWithBlockedPoolWorkers(launcher -> {
      Thread.currentThread().interrupt();
      try {
        var processed = new AtomicInteger();
        assertTrue(ProgressManager.getInstance().runProcess(() ->
          launcher.invokeConcurrentlyUnderContextProgress(List.of(1, 2), _ -> {
            processed.incrementAndGet();
            return true;
          }), new EmptyProgressIndicator()));
        assertEquals(2, processed.get());
        assertTrue("The caller lost its interrupt status", Thread.currentThread().isInterrupted());
      }
      finally {
        var _ = Thread.interrupted();
      }
    }, false);
  }

  public void testQueueProcessingDoesNotStarveCompletelyBlockedForkJoinPool() throws Exception {
    assertCompletesWithBlockedPoolWorkers(launcher -> {
      var tombStone = new Object();
      var queue = new LinkedBlockingQueue<>(List.of(new Object(), new Object(), tombStone));
      var processed = new AtomicInteger();
      assertTrue(launcher.processQueue(queue, new ConcurrentLinkedQueue<>(), new EmptyProgressIndicator(), tombStone, _ -> {
        processed.incrementAndGet();
        return true;
      }));
      assertEquals(2, processed.get());
    }, false);
  }

  public void testQueueJoinResumesVirtualThreadWithBlockedPoolWorkers() throws Exception {
    var childStarted = new CountDownLatch(1);
    var releaseChild = new CountDownLatch(1);
    var child = new AtomicReference<Thread>();
    var future = AppExecutorUtil.getAppExecutorService().submit(() -> {
      assertCompletesWithBlockedPoolWorkers(launcher -> {
        var tombStone = 2;
        var queue = new LinkedBlockingQueue<>(List.of(0, 1, tombStone));
        assertTrue(launcher.processQueue(queue, new ConcurrentLinkedQueue<>(), new EmptyProgressIndicator(), tombStone, item -> {
          if (item == 1) {
            assertTrue(Thread.currentThread().isVirtual());
            child.set(Thread.currentThread());
            childStarted.countDown();
            awaitOrFail(releaseChild);
          }
          return true;
        }));
      });
      return null;
    });
    try {
      assertTrue("The child did not start", childStarted.await(5, TimeUnit.SECONDS));
      var timeout = TestTimeOut.setTimeout(5, TimeUnit.SECONDS);
      while (child.get().getState() != Thread.State.TIMED_WAITING) {
        if (timeout.isTimedOut()) {
          fail(ThreadDumper.dumpThreadsToString());
        }
        Thread.yield();
      }
    }
    finally {
      releaseChild.countDown();
      future.get(10, TimeUnit.SECONDS);
    }
  }

  private static void assertCompletesWithBlockedPoolWorkers(@NotNull ThrowableConsumer<? super JobLauncherImpl, ? extends Exception> action)
    throws Exception {
    assertCompletesWithBlockedPoolWorkers(action, true);
  }

  private static void assertCompletesWithBlockedPoolWorkers(@NotNull ThrowableConsumer<? super JobLauncherImpl, ? extends Exception> action,
                                                          boolean callerInPool) throws Exception {
    try (var pool = new ForkJoinPool(3)) {
      int blockedWorkers = callerInPool ? 2 : 3;
      var workersStarted = new CountDownLatch(blockedWorkers);
      var releaseWorkers = new CountDownLatch(1);
      try {
        for (int i = 0; i < blockedWorkers; i++) {
          pool.submit(() -> {
            workersStarted.countDown();
            releaseWorkers.await();
            return null;
          });
        }
        assertTrue("The blocked workers did not start", workersStarted.await(10, TimeUnit.SECONDS));
        var launcher = new JobLauncherImpl(pool, pool.getParallelism());
        var executor = callerInPool ? pool : AppExecutorUtil.getAppExecutorService();
        executor.submit(() -> {
          var unrelatedRan = new AtomicBoolean();
          pool.execute(() -> unrelatedRan.set(true));
          action.consume(launcher);
          assertTrue("The caller did not help the unrelated task", unrelatedRan.get());
          return null;
        }).get(10, TimeUnit.SECONDS);
      }
      finally {
        releaseWorkers.countDown();
      }
    }
  }

  private static void assertCompletesWithSaturatedPool(@NotNull ThrowableConsumer<? super JobLauncherImpl, ? extends Exception> action)
    throws Exception {
    try (var pool = new ForkJoinPool(3)) {
      var workersStarted = new CountDownLatch(pool.getParallelism());
      var launcher = new JobLauncherImpl(pool, pool.getParallelism());
      var callers = new ArrayList<Future<?>>();
      try {
        for (int i = 0; i < pool.getParallelism(); i++) {
          callers.add(pool.submit(() -> {
            assertFalse(Thread.currentThread().isVirtual());
            workersStarted.countDown();
            awaitOrFail(workersStarted);
            action.consume(launcher);
            return null;
          }));
        }
        for (var caller : callers) {
          caller.get(10, TimeUnit.SECONDS);
        }
      }
      finally {
        // Let queued virtual threads finish if a caller times out.
        pool.setParallelism(4);
        pool.execute(() -> {});
      }
    }
  }

  @PerformanceUnitTest
  public void testUnbalancedTaskJobUtilPerformance() {
    int N = 10_000;
    List<Integer> things = new ArrayList<>(N);
    int sum = 0;
    for (int i = 0; i < N; i++) {
      int v = i < N-50 ? 1 : 1000;
      things.add(v);
      sum += v;
    }
    //noinspection PointlessArithmeticExpression
    assertEquals((N-50)*1 + 50*1000, sum);

    long elapsed = TimeoutUtil.measureExecutionTime(() ->
      assertTrue(ProgressManager.getInstance().runProcess(
        () -> JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(things, o -> {
          if (o <= 1) {
            busySleepAndIncrement(o);
          }
          else {
            longSleep(o);
          }
          return true;
        }),
        new ProgressIndicatorBase())));
    long expected = sum / JobSchedulerImpl.getJobPoolParallelism();
    String message = "Elapsed: " + elapsed + "; expected: " + expected + "; parallelism=" + JobSchedulerImpl.getJobPoolParallelism() + "; current cores=" + Runtime.getRuntime().availableProcessors();
    assertTrue(message, elapsed <= 2 * expected);
  }

  private static void longSleep(int o) {
    busySleepAndIncrement(o);
  }

  private static int busySleepAndIncrement(int ms) {
    return busySleepAndIncrement(ms, EmptyRunnable.getInstance());
  }
  private static int busySleepAndIncrement(int ms, @NotNull Runnable doWhileWait) {
    long deadline = System.currentTimeMillis() + ms;
    int nap = Math.max(1, ms / 100);
    while (System.currentTimeMillis() < deadline)  {
      TimeoutUtil.sleep(nap);
      doWhileWait.run();
    }
    return COUNT.incrementAndGet();
  }

  private volatile Throwable exception;
  public void testCorrectlySplitsUpHugeWorkAndFinishesStress() throws Throwable {
    int N = Timings.adjustAccordingToMySpeed(20_000, true);
    AtomicBoolean finished = new AtomicBoolean();

    boolean ok = ProgressManager.getInstance().runProcess(
      () -> JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(Collections.<String>nCopies(N, null), _ -> {
        try {
          if (finished.get()) {
            throw new RuntimeException();
          }
          busySleepAndIncrement(1);
          if (finished.get()) {
            throw new RuntimeException();
          }
        }
        catch (Exception e) {
          exception = e;
        }
        return true;
      }),
      new EmptyProgressIndicator());
    assertTrue(ok);
    finished.set(true);
    if (exception != null) throw exception;
    assertEquals(N, COUNT.get());
  }

  public void testJobUtilProcessesAllItemsStress() throws Throwable {
    List<String> list = Collections.nCopies(Timings.adjustAccordingToMySpeed(1000, true), null);
    for (int i = 0; i<10 && checkTestTimeout(i); i++) {
      COUNT.set(0);
      logElapsed(() ->
        ProgressManager.getInstance().runProcess(() -> {
          JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(list, _ -> {
            busySleepAndIncrement(1);
            return true;
          });
        }, new EmptyProgressIndicator()));
      if (exception != null) throw exception;
      assertEquals(list.size(), COUNT.get());
    }
  }

  private static void logElapsed(@NotNull Runnable r) {
    long ms = TimeoutUtil.measureExecutionTime(() -> r.run());
    LOG.debug("Elapsed: " + ms + "ms");
  }

  public void testJobUtilRecursiveStress() {
    int N = Timings.adjustAccordingToMySpeed(40, true);
    List<String> list = Collections.nCopies(N, null);
    for (int i = 0; i<10 && checkTestTimeout(i); i++) {
      COUNT.set(0);
      logElapsed(() ->
        ProgressManager.getInstance().runProcess(() -> {
          JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(list, _ -> {
            JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(list, _ -> {
              COUNT.incrementAndGet();
              return true;
            });
            return true;
          });
        }, new EmptyProgressIndicator()));
      assertEquals(list.size()*list.size(), COUNT.get());
    }
  }

  public void testCorrectProgressAndReadAction() throws Throwable {
    checkProgressAndReadAction(Collections.singletonList(null), new DaemonProgressIndicator(), true);
    checkProgressAndReadAction(Collections.singletonList(null), new DaemonProgressIndicator(), false);
    checkProgressAndReadAction(Collections.emptyList(), new DaemonProgressIndicator(), true);
    checkProgressAndReadAction(Collections.emptyList(), new DaemonProgressIndicator(), false);
    checkProgressAndReadAction(List.of(new Object(), new Object()), new DaemonProgressIndicator(), true);
    checkProgressAndReadAction(List.of(new Object(), new Object()), new DaemonProgressIndicator(), false);
    checkProgressAndReadAction(List.of(new Object(), new Object()), new DaemonProgressIndicator(), false);
  }

  private void checkProgressAndReadAction(@NotNull List<Object> objects,
                                          @NotNull DaemonProgressIndicator progress,
                                          boolean runInReadAction) throws Throwable {
    ReadAction.runBlocking(()->ProgressManager.getInstance().runProcess(() -> {
      JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(objects, _ -> {
        ThrowableRunnable<RuntimeException> runnable = () -> {
            try {
              // in general, we know nothing about the current thread since FJP can help other tasks to execute while in the current context
              ProgressIndicator original = ProgressWrapper.unwrapAll(ProgressManager.getInstance().getProgressIndicator());
              assertSame(progress, original);
              // there can be read access even if we didn't ask for it (e.g., when the task under read action steals others work)
              assertTrue(!runInReadAction || ApplicationManager.getApplication().isReadAccessAllowed());
            }
            catch (Throwable e) {
              exception = e;
            }
        };
        if (runInReadAction) {
          ReadAction.runBlocking(runnable);
        }
        else {
          runnable.run();
        }

        return true;
      });
    }, progress));
    if (exception != null) throw exception;
  }

  private static class MyException extends RuntimeException {
    private MyException(String msg) {
      super(msg);
    }
  }
  public void testThrowExceptionInProcessorMustBubbleUpToInvokeConcurrently() {
    checkExceptionBubblesUp(new RuntimeException("myMsg"));
    checkExceptionBubblesUp(new MyException("myMsg"));
    checkExceptionBubblesUp(new Error("myMsg"));
    checkExceptionBubblesUp(new IncorrectOperationException("myMsg"));
    //checkExceptionBubblesUp(new ProcessCanceledException());
  }

  private static void checkExceptionBubblesUp(@NotNull Throwable ex) {
    COUNT.set(0);
    assertTrue(ex.getMessage().contains("myMsg"));
    List<Object> objects = Collections.nCopies(100_000, null);
    assertThrows(ex.getClass(), "myMsg", () ->
      ProgressManager.getInstance().runProcess(() -> {
        JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(objects, _ -> {
          if (COUNT.incrementAndGet() == 10_000) {
            LOG.debug("PCE");
            if (ex instanceof Error) {
              throw (Error)ex;
            }
            else {
              throw (RuntimeException)ex;
            }
          }
          return true;
        });
      }, new EmptyProgressIndicator()));
  }

  public void testIndicatorCancelMustEnsuePCE() {
    ProgressIndicator progress = new DaemonProgressIndicator();
    assertThrows(ProcessCanceledException.class, () ->
      ProgressManager.getInstance().runProcess(() -> {
        JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(Collections.nCopies(100_000, null), _ -> {
          if (COUNT.incrementAndGet() == 10_000) {
            progress.cancel();
          }
          return true;
        });
      }, progress));
    assertTrue(progress.isCanceled());
  }

  @SuppressWarnings("SuspiciousPackagePrivateAccess")
  public void testApplierCompleterPreservesProcessCancellation() {
    var cancellation = new ProcessCanceledException();
    @SuppressWarnings("unchecked")
    var completers = (ApplierCompleter<Object>[])new ApplierCompleter<?>[1];
    completers[0] = new ApplierCompleter<>(
      completers, 0, new AtomicReference<>(), false, false, new EmptyProgressIndicator(),
      List.of(new Object()), new boolean[1], 0, 1, new ArrayList<>(), _ -> {
        throw cancellation;
      });

    ThreadContext.resetThreadContext(() -> {
      completers[0].run();
      return null;
    });

    var failure = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class, completers[0]::get);
    assertSame(cancellation, failure.getCause());
  }

  public void testReturnFalseFromProcessorMustLeadToReturningFalseFromInvokeConcurrently() {
    List<Object> objects = Collections.nCopies(100_000, null);
    boolean success = ProgressManager.getInstance().runProcess(
      () -> JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(objects, _ -> COUNT.incrementAndGet() != 10_000),
      new EmptyProgressIndicator());
    assertFalse(success);
  }

  public void testCompletesEvenIfCannotGrabReadAction() {
    List<Object> objects = Collections.nCopies(1_000_000, null);
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProgressManager.getInstance().runProcess(() -> {
        boolean success =
          JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(objects, _ -> {
            COUNT.incrementAndGet();
            return true;
          });
        assertTrue(success);
        assertEquals(objects.size(), COUNT.get());
      }, new EmptyProgressIndicator());
    });
  }

  public void testRecursiveCancel() {
    List<Integer> list = IntStream.range(0, 100).boxed().collect(Collectors.toList());
    for (int i = 0; i<10 && checkTestTimeout(i); i++) {
      int fingerPrint = i;
      COUNT.set(0);
      LOG.debug("--- " + i+"; fingerPrint="+fingerPrint+"; COUNT="+COUNT);
      boolean[] success = new boolean[1];
      logElapsed(() ->
        assertThrows(MyException.class, "myMsg", () -> {
          success[0] = ProgressManager.getInstance().runProcess(
            () -> JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(list, ind -> {
              boolean nestedSuccess = JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(list, nestedInd -> {
                                if (busySleepAndIncrement(1) % 1024 == 0) {
                                  LOG.debug("throw myMsg ind=" + ind + "; nestedInd=" + nestedInd+"; fingerPrint="+fingerPrint+"; COUNT="+COUNT);
                                  throw new MyException("myMsg"+fingerPrint+"; COUNT="+COUNT);
                                }
                                return true;
                              });
              LOG.debug("nestedSuccess: " + nestedSuccess + "; ind:" + ind+"; fingerPrint="+fingerPrint+"; COUNT="+COUNT);
              return true;
            }),
            new EmptyProgressIndicator());
        }));
      assertFalse(success[0]);
    }
  }

  public void testBlockingJobsDoNotSaturateThreadPool() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<Job> jobs = new ArrayList<>();
    for (int i = 0; i<100 && checkTestTimeout(i); i++) {
      jobs.add(((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(() -> {
        try {
          latch.await();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }, null));
    }
    jobs.add(((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(() -> latch.countDown(), null));

    try {
      boolean scheduled = latch.await(3, TimeUnit.SECONDS);
      assertTrue(scheduled);
    }
    finally {
      latch.countDown();
      cancelAndWait(jobs);
    }
  }

  private static void cancelAndWait(@NotNull List<? extends Job> jobs) throws Exception {
    for (Job job : jobs) {
      job.cancel();
    }
    for (Job job : jobs) {
      try {
        job.waitForCompletion(100_000);
      }
      catch (ProcessCanceledException ignored) {
      }
    }
  }

  public void testProcessorReturningFalseDoesNotCrashTheOtherThreadStress() {
    AtomicInteger processed = new AtomicInteger();
    Processor<String> processor = _ -> {
      int next = processed.incrementAndGet();
      busySleepAndIncrement(next % 4, ProgressManager::checkCanceled);
      return next % 100 != 0;
    };
    int N = Timings.adjustAccordingToMySpeed(10_000, true);
    for (int i = 0; i<100 && checkTestTimeout(i); i++) {
      processed.set(0);
      ProgressIndicator indicator = new ProgressIndicatorBase();
      boolean result = ProgressManager.getInstance().runProcess(
        () -> JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(Collections.nCopies(N, ""), processor), indicator);
      assertFalse(indicator.isCanceled());
      assertFalse(result);
    }
  }

  public void testTasksRunEvenWhenReadActionIsHardToGetStress() throws Exception {
    Processor<String> processor = _ -> {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      return true;
    };
    int N = Timings.adjustAccordingToMySpeed(300, true);
    for (int i = 0; i<10 && checkTestTimeout(i); i++) {
      COUNT.set(0);
      ProgressIndicator indicator = new EmptyProgressIndicator();
      AtomicBoolean runReads = new AtomicBoolean(true);
      Semaphore startedReads = new Semaphore(1);
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        startedReads.up();
        while (runReads.get() && checkTestTimeout(this)) {
          ReadAction.runBlocking(() ->
            ProgressManager.getInstance().runProcess(() -> {
              JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(Collections.nCopies(N, ""), processor);
            }, indicator));
          assertFalse(indicator.isCanceled());
        }
      });
      startedReads.waitFor();
      for (int k=0; k<N; k++) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          COUNT.incrementAndGet();
        });
      }
      runReads.set(false);
      future.get();
      assertEquals(N, COUNT.get());
    }
  }

  // create bounded queue and spawn producer which fills this queue with elements
  // spawn consumer which calls JobLauncherImpl.processQueue() for that queue,
  // stress EDT with periodic write actions and check that processQueue() doesn't lose elements due to
  // constant PCEs and multiple restarts
  public void testProcessInOrderWorksEvenWhenReadActionIsHardToGetStress() throws Exception {
    String TOMB_STONE = "TOMB_STONE";
    // schedule huge number of write actions interrupting processQueue
    Future<?> interrupts = EdtScheduledExecutorService.getInstance().scheduleWithFixedDelay(() ->
        WriteAction.run(() -> { }), 1, 1, TimeUnit.MICROSECONDS);
    for (int i = 0; i<10 && checkTestTimeout(i); i++) {
      BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
      int N_ELEMENTS = 10000;
      COUNT.set(0);
      Future<?> supplyFuture = supplyElementsInBatchesInBackground(queue, N_ELEMENTS, TOMB_STONE);
      Queue<String> failedQueue = new LinkedBlockingQueue<>();
      Future<?> background = AppExecutorUtil.getAppExecutorService().submit(() -> processQueueInBackground(TOMB_STONE, queue, failedQueue));
      while (!background.isDone() && checkTestTimeout(i)) {
        UIUtil.dispatchAllInvocationEvents();
      }
      supplyFuture.get();
      assertEquals(COUNT.get(), N_ELEMENTS);
    }
    interrupts.cancel(true);
    while (!interrupts.isDone() && checkTestTimeout(this)) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  private static void processQueueInBackground(@NotNull String TOMB_STONE, @NotNull BlockingQueue<String> queue, @NotNull Queue<String> failedQueue) {
    while (true) {
      Disposable disposable = Disposer.newDisposable();
      ProgressIndicator wrapper = new DaemonProgressIndicator();
      try {
        // avoid "attach listener"/"write action" race
        ReadAction.runBlocking(() -> {
          wrapper.start();
          ProgressIndicatorUtils.forceWriteActionPriority(wrapper, disposable);
          // there is a chance we are racing with write action, in which case just registered listener might not be called, retry.
          if (ApplicationManagerEx.getApplicationEx().isWriteActionPending()) {
            throw new ProcessCanceledException();
          }
        });
        // use wrapper here to cancel early when write action start but do not affect the original indicator
        ((JobLauncherImpl)JobLauncher.getInstance()).processQueue(queue, failedQueue, wrapper, TOMB_STONE, _ -> {
          ReadAction.runBlocking(() -> {
            ProgressManager.checkCanceled();
            //TimeoutUtil.sleep(1);
            ProgressManager.checkCanceled();
            COUNT.incrementAndGet();
          });
          return true;
        });
        break;
      }
      catch (ProcessCanceledException e) {
        // wait for write action to complete
        ApplicationManager.getApplication().runReadAction(EmptyRunnable.getInstance());
      }
      finally {
        Disposer.dispose(disposable);
      }
    }
  }

  @NotNull
  private static Future<?> supplyElementsInBatchesInBackground(@NotNull BlockingQueue<? super String> queue,
                                                               int nElements,
                                                               @NotNull String TOMB_STONE) {
    return AppExecutorUtil.getAppExecutorService().submit(() -> {
      int batch = 10;
      for (int i=0; i<nElements; i+=batch) {
        try {
          for (String s : Collections.nCopies(batch, "")) {
            queue.put(s);
          }
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      try {
        queue.put(TOMB_STONE);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void testAfterCancelInTheMiddleOfTheExecutionTaskIsDoneReturnsFalseUntilFinishedStress() throws Exception {
    Random random = new Random();
    for (int i = 0; i<100 && checkTestTimeout(i); i++) {
      AtomicBoolean finished = new AtomicBoolean();
      AtomicBoolean started = new AtomicBoolean();
      Job job = ((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(() -> {
        started.set(true);
        TimeoutUtil.sleep(100);
        finished.set(true);
      }, null);
      assertFalse(job.isDone());
      TimeoutUtil.sleep(random.nextInt(100));
      job.cancel();
      TestTimeOut waitForStart = TestTimeOut.setTimeout(1, TimeUnit.SECONDS);

      while (!started.get() && !waitForStart.isTimedOut() && checkTestTimeout(i)) {
        // need to test cancel() after started
      }
      if (!started.get()) {
        // we are lucky to be able to cancel future before it started executing, nothing interesting
        continue;
      }
      while (checkTestTimeout(i)) {
        boolean isDone = job.isDone();
        boolean isFinished = finished.get();

        // the only forbidden state is isDone == 1, isFinished == 0
        assertFalse("isDone:"+isDone+"; isFinished:"+isFinished, isDone && !isFinished);
        if (isDone) {
          break;
        }
      }
      cancelAndWait(Collections.singletonList(job));
    }
  }

  // returns true if the current test is still ok
  private boolean checkTestTimeout(Object progress) {
    return !t.timedOut(progress);
  }

  public void testJobWaitForTerminationAfterCancelInTheMiddleOfTheExecutionWaitsUntilFinished() throws Exception {
    for (int i = 0; i<100 && checkTestTimeout(i); i++) {
      AtomicBoolean finished = new AtomicBoolean();
      AtomicBoolean started = new AtomicBoolean();
      Job job = ((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(() -> {
        started.set(true);
        TimeoutUtil.sleep(100);
        finished.set(true);
      }, null);
      assertFalse(job.isDone());
      while (!started.get()) {
        assertTrue(checkTestTimeout(i));
      }
      assertTrue(started.get());
      job.cancel();
      assertTrue(job.waitForCompletion(100_000));
      assertTrue(finished.get());
    }
  }

  public void testDaemonDoesNotPauseWhenEventDispatcherHasEventsInTheQueueStress() throws Throwable {
    assertTrue(SwingUtilities.isEventDispatchThread());

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    AtomicInteger jobsStarted = new AtomicInteger();
    final int N_EVENTS = 50;
    int N_JOBS = 10_000 * JobSchedulerImpl.getJobPoolParallelism();
    ProgressIndicator indicator = new DaemonProgressIndicator();

    Job job = ((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(
      () -> ProgressManager.getInstance().runProcess(() -> {
        ReadAction.runBlocking(()->JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(Collections.nCopies(N_JOBS, null), _ -> {
          jobsStarted.incrementAndGet();
          TimeoutUtil.sleep(10);
          return true;
        }));
      }, indicator), null);

    for (int i = 0; i < N_EVENTS; i++) {
      TestTimeOut n = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
      int finalI = i;
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        int jobs0 = jobsStarted.get();
        while (jobsStarted.get() < jobs0 + JobSchedulerImpl.getJobPoolParallelism() && jobsStarted.get() < N_JOBS) {
          if (n.timedOut(finalI)) {
            fail(ThreadDumper.dumpThreadsToString());
            break;
          }
        }
      });
      UIUtil.dispatchAllInvocationEvents();
    }
    indicator.cancel();
    job.cancel();
    while (!job.isDone()) {
      UIUtil.dispatchAllInvocationEvents();
      job.waitForCompletion(1000);
    }
  }

  public void testInvokeConcurrentlyMustBeResponsiveToTheIndicatorCancelWhenWaitsForTheOtherTasksToComplete() {
    ProgressIndicator indicator = new DaemonProgressIndicator();
    int N = 100_000;
    AtomicInteger counter = new AtomicInteger();
    // run lengthy process in FJP,
    // in which call invokeConcurrentlyUnderProgress() which normally takes 100s
    // and cancel the indicator in the meantime
    // check that invokeConcurrentlyUnderProgress() gets canceled immediately
    CountDownLatch cancelCalled = new CountDownLatch(1);
    Job job = ((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(() ->
      ReadAction.runBlocking(()->ProgressManager.getInstance().runProcess(() ->
        assertFalse(JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(Collections.nCopies(N, null), _ -> {
          TimeoutUtil.sleep(1);
          counter.incrementAndGet();
          try {
            cancelCalled.await();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          return true;
        })), indicator)), null);
    TimeoutUtil.sleep(10); // all tasks distributed among all cpus
    indicator.cancel();
    cancelCalled.countDown();
    assertThrows(ProcessCanceledException.class, ()-> {
      long start = System.currentTimeMillis();
      boolean result = job.waitForCompletion(10_000);
      LOG.debug("wtf? counter: "+counter+"; indicator:"+indicator+"; result:"+result+"; job:"+job+
                "; elapsed="+(System.currentTimeMillis()-start)+"ms");
    });
    assertTrue(job.isDone());
    assertTrue(counter.toString(), counter.get() < N);
  }

  public void testInvokeConcurrentlyMustBeResponsiveToTheIndicatorCancelWhenWaitsEvenForExtraCoarseGranularTasksStress() throws Exception {
    boolean testedCancellation = false;
    for (int attempt = 0; attempt < 1000 && !testedCancellation; attempt++) {
      var indicator = new DaemonProgressIndicator();
      var coarseTaskStarted = new CountDownLatch(1);
      var releaseCoarseTask = new CountDownLatch(1);
      var coarseTaskFinished = new CountDownLatch(1);
      var callerRanCoarseTask = new AtomicBoolean();
      var cancellationObserved = new AtomicBoolean();
      int coarseTask = 100_000;
      Job job = ((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(() -> {
        Thread callingThread = Thread.currentThread();
        try {
          ReadAction.runBlocking(() -> ProgressManager.getInstance().runProcess(() ->
            JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(List.of(1, 1, 1, coarseTask), delay -> {
              if (delay == coarseTask) {
                boolean ranOnCaller = Thread.currentThread() == callingThread;
                callerRanCoarseTask.set(ranOnCaller);
                coarseTaskStarted.countDown();
                if (!ranOnCaller) {
                  try {
                    releaseCoarseTask.await();
                  }
                  catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  finally {
                    coarseTaskFinished.countDown();
                  }
                }
              }
              return true;
            }), indicator));
        }
        catch (ProcessCanceledException _) {
          cancellationObserved.set(true);
        }
      }, null);

      boolean childRanCoarseTask = false;
      boolean workerFinished = true;
      try {
        assertTrue("The coarse task did not start", coarseTaskStarted.await(10, TimeUnit.SECONDS));
        childRanCoarseTask = !callerRanCoarseTask.get();
        if (childRanCoarseTask) {
          indicator.cancel();
          assertTrue("The outer job did not finish", job.waitForCompletion(10_000));
          assertTrue("The outer job did not observe cancellation", cancellationObserved.get());
          testedCancellation = true;
        }
        else {
          assertTrue("The outer job did not finish", job.waitForCompletion(10_000));
        }
      }
      finally {
        releaseCoarseTask.countDown();
        if (childRanCoarseTask) {
          workerFinished = coarseTaskFinished.await(10, TimeUnit.SECONDS);
        }
        cancelAndWait(List.of(job));
      }
      assertTrue("The coarse task did not finish", workerFinished);
    }
    assertTrue("No child thread executed the coarse task", testedCancellation);
  }

  public void testInvokeConcurrentlyMustExecuteMultipleTasksConcurrentlyEvenIfOneOfThemIsWildlySlowStress() {
    int parallelism = JobSchedulerImpl.getJobPoolParallelism();
    // Use enough items to distribute the short tasks across all workers.
    int N = 1 << parallelism;
    Integer[] times = new Integer[N];
    Arrays.fill(times, 0);
    int longDelay = (int)TimeUnit.MINUTES.toMillis(2);
    for (int i=0; i<100; i++) {
      times[(i + N - 1) % N] = 0;
      times[i % N] = longDelay;

      AtomicInteger executed = new AtomicInteger();
      DaemonProgressIndicator progress = new DaemonProgressIndicator();
      assertThrows(MyException.class, "myMsg", () -> {
        TestTimeOut deadline = TestTimeOut.setTimeout(2, TimeUnit.SECONDS);
        ProgressManager.getInstance().runProcess(() -> {
          JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(List.of(times), time -> {
            while ((time -= 100) >= 0) {
              ProgressManager.checkCanceled();
              TimeoutUtil.sleep(100);
              if (deadline.isTimedOut()) {
                String s = ThreadDumper.dumpThreadsToString();
                throw new AssertionError("Timed out at " + time + "; parallelism:"+parallelism+"; executed:"+executed+"; threads:\n" + s);
              }
            }

            if (executed.incrementAndGet() >= times.length - 1) {
              // executed all but the slowest one
              throw new MyException("myMsg");
            }
            return true;
          });
        }, progress);
      });
    }
  }

  public void testReturningFalseWaitsForOtherProcessors() throws Exception {
    assertReturningFalseWaitsForOtherProcessors(false);
  }

  public void testNestedReturningFalseWaitsForOtherProcessors() throws Exception {
    assertReturningFalseWaitsForOtherProcessors(true);
  }

  public void testReturningFalseWaitsForIncompleteRange() throws Exception {
    var blockedStarted = new CountDownLatch(1);
    var releaseBlocked = new CountDownLatch(1);
    var blockedFinished = new AtomicBoolean();
    var falseWorkerStarted = new CountDownLatch(1);
    var falseWorkerFinished = new CountDownLatch(1);
    var falseWorker = new AtomicReference<Thread>();
    var launcher = new JobLauncherImpl(ForkJoinPool.commonPool(), 2);
    var future = AppExecutorUtil.getAppExecutorService().submit(() -> ProgressManager.getInstance().runProcess(() ->
      launcher.processConcurrentlyAsync(List.of(0, 1, 2, 3), item -> {
        if (item == 0) {
          blockedStarted.countDown();
          awaitOrFail(releaseBlocked);
          blockedFinished.set(true);
        }
        else if (item == 2) {
          awaitOrFail(blockedStarted);
          falseWorker.set(Thread.currentThread());
          falseWorkerStarted.countDown();
        }
        return item < 2;
      }, () -> awaitOrFail(falseWorkerFinished)), new EmptyProgressIndicator()));
    try {
      assertTrue("The false worker did not start", falseWorkerStarted.await(10, TimeUnit.SECONDS));
      var worker = falseWorker.get();
      worker.join(10_000);
      assertFalse("The false worker did not finish", worker.isAlive());
      falseWorkerFinished.countDown();
      assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
    }
    finally {
      releaseBlocked.countDown();
      falseWorkerFinished.countDown();
      assertFalse(future.get(10, TimeUnit.SECONDS));
    }
    assertTrue(blockedFinished.get());
  }

  private static void assertReturningFalseWaitsForOtherProcessors(boolean nested) throws Exception {
    var launcher = new JobLauncherImpl(ForkJoinPool.commonPool(), 2);
    var otherStarted = new CountDownLatch(1);
    var falseReturned = new CountDownLatch(1);
    var releaseOther = new CountDownLatch(1);
    var otherFinished = new AtomicBoolean();
    var future = AppExecutorUtil.getAppExecutorService().submit(() -> ProgressManager.getInstance().runProcess(() -> {
      BooleanSupplier process = () -> launcher.processConcurrentlyAsync(List.of(false, true), item -> {
        if (item) {
          otherStarted.countDown();
          awaitOrFail(releaseOther);
          otherFinished.set(true);
          return true;
        }
        awaitOrFail(otherStarted);
        falseReturned.countDown();
        return false;
      }, () -> awaitOrFail(falseReturned));
      return nested
             ? launcher.invokeConcurrentlyUnderContextProgress(List.of(0, 1), item -> item != 0 || process.getAsBoolean())
             : process.getAsBoolean();
    }, new EmptyProgressIndicator()));
    try {
      assertTrue("The processor did not return false", falseReturned.await(10, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
    }
    finally {
      releaseOther.countDown();
      assertFalse(future.get(10, TimeUnit.SECONDS));
    }
    assertTrue(otherFinished.get());
  }
  public void testPCEThrownFromProcessorMustPropagateOutwards() {
    List<Integer> ints = IntStream.range(1, 123_271).boxed().collect(Collectors.toList());
    int N = 1000;
    for (int i=0; i<N; i++) {
      assertThrows(ProcessCanceledException.class, () ->
        ProgressManager.getInstance().runProcess(() -> {
          JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(ints, _ -> {
            throw new ProcessCanceledException(new RuntimeException("xxx"));
          });
        }, new DaemonProgressIndicator()));
    }
  }
  public void testExceptionThrownFromProcessorMustPropagateOutwards() {
    List<Integer> ints = IntStream.range(1, 123_271).boxed().collect(Collectors.toList());
    int N = 100;
    for (int i=0; i<N; i++) {
      assertThrows(RuntimeException.class, "xxx", () ->
        ProgressManager.getInstance().runProcess(() -> {
          JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(ints, _ -> {
            throw new RuntimeException("xxx");
          });
        }, new DaemonProgressIndicator()));
    }
  }
  public void testEvenTwoJobsAreParallelized() throws Exception {
    ThreadingAssertions.assertEventDispatchThread();
    TestTimeOut w = TestTimeOut.setTimeout(100, TimeUnit.SECONDS);
    JobLauncherImpl launcher = new JobLauncherImpl(ForkJoinPool.commonPool(), 4);
    for (int i = 0; i < 1000; i++) {
      LOG.debug(String.valueOf(i));
      int finali = i;
      record R(@NotNull AtomicBoolean hold, @NotNull Job future, @NotNull AtomicBoolean started) {
      }
      R[] r = new R[3];
      // Keep workers busy. The launcher must still process both items.
      for (int k = 0; k < r.length; k++) {
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean hold = new AtomicBoolean(true);
        Job f = launcher.submitToJobThread(() -> {
          started.set(true);
          while (hold.get()) Thread.yield();
        }, _ -> {});
        r[k] = new R(hold, f, started);
        while (!started.get()) {
          Thread.yield();
        }
      }

      try {
        AtomicBoolean ready1 = new AtomicBoolean();
        AtomicBoolean ready2 = new AtomicBoolean();
        AtomicBoolean invokeConcResult = new AtomicBoolean();
        Job future = launcher.submitToJobThread(() -> {
          LOG.debug("F: finali=" + finali + "; r=" + Arrays.toString(r));
          invokeConcResult.set(ReadAction.computeBlocking(()->ProgressManager.getInstance().runProcess(()->
              launcher.invokeConcurrentlyUnderContextProgress(List.of(ready1, ready2), item -> {
                item.set(true);
                while (!ready1.get() || !ready2.get()) {
                  r[2].hold().set(false);
                  // wait
                  if (w.timedOut()) throw new RuntimeException(ThreadDumper.dumpThreadsToString());
                }
                return true;
              }), new DaemonProgressIndicator())));
            }, _->{});
        try {
          assertTrue(future.waitForCompletion(20_000));
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
        assertTrue(invokeConcResult.get());
      }
      finally {
        for (R value : r) {
          value.hold().set(false);
          assertTrue(value.future().waitForCompletion(100_000));
        }
      }
    }
  }

  public void testConcurrentTasksDoNotSeeRecursionGuardsOfCaller() throws Exception {
    var guard = RecursionManager.<String>createGuard(getName());
    var workersStarted = new CountDownLatch(2);
    var releaseWorkers = new CountDownLatch(1);
    var launcher = new JobLauncherImpl(ForkJoinPool.commonPool(), 2);
    var future = AppExecutorUtil.getAppExecutorService().submit(() -> ProgressManager.getInstance().runProcess(() -> {
      var caller = Thread.currentThread();
      var result = guard.doPreventingRecursion("key", false, () -> {
        boolean processed = launcher.processConcurrentlyAsync(List.of(1, 2), _ -> {
          workersStarted.countDown();
          awaitOrFail(releaseWorkers);
          assertNotSame(caller, Thread.currentThread());
          assertEquals("computed", guard.doPreventingRecursion("key", false, () -> "computed"));
          return true;
        }, () -> {
          try {
            awaitOrFail(workersStarted);
            assertNull(guard.doPreventingRecursion("key", false, () -> "computed"));
          }
          finally {
            releaseWorkers.countDown();
          }
        });
        assertNull(guard.doPreventingRecursion("key", false, () -> "computed"));
        return processed;
      });
      assertEquals("computed", guard.doPreventingRecursion("key", false, () -> "computed"));
      return result;
    }, new DaemonProgressIndicator()));
    assertEquals(Boolean.TRUE, future.get(30, TimeUnit.SECONDS));
  }

  private static void awaitOrFail(@NotNull CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        fail(ThreadDumper.dumpThreadsToString());
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  // a restart after a write action re-enters processQueue() with `things` drained down to the tombstone
  // and the real work sitting in `failedToProcess` - it must still process that work in parallel
  public void testProcessQueueAccountsForFailedQueueWhenChoosingParallelism() throws Exception {
    Assume.assumeTrue(JobSchedulerImpl.getJobPoolParallelism() > 2);
    String TOMB_STONE = "TOMB_STONE";
    BlockingQueue<String> things = new ArrayBlockingQueue<>(100);
    things.put(TOMB_STONE);                                  // producer already finished
    Queue<String> failed = new LinkedBlockingQueue<>();
    for (int i = 0; i < 50; i++) failed.add("e" + i);        // left over from the interrupted pass
    Set<Thread> threads = ContainerUtil.newConcurrentSet();
    ProgressIndicator wrapper = new DaemonProgressIndicator();
    wrapper.start();
    ((JobLauncherImpl)JobLauncher.getInstance()).processQueue(things, failed, wrapper, TOMB_STONE, _ -> {
      threads.add(Thread.currentThread());
      TimeoutUtil.sleep(20);                                 // keep several elements in flight
      return true;
    });
    assertTrue("Expected parallel processing. Threads: " + threads, threads.size() > 1);
    assertTrue("Some elements remain unprocessed: " + failed, failed.isEmpty());
  }

  public void testSynchronizedBlockMustAllowOtherThreadsToPreempt() throws Exception {
    int parallelism = Math.max(JobSchedulerImpl.getCPUCoresCount(), JobSchedulerImpl.getJobPoolParallelism());
    int contendingJobCount = Math.max(100, parallelism * 2);
    CountDownLatch contendingJobsStarted = new CountDownLatch(contendingJobCount);
    CountDownLatch followingJobRan = new CountDownLatch(1);
    AtomicInteger enteredMonitorCount = new AtomicInteger();
    List<Job> jobs = new ArrayList<>(contendingJobCount + 1);
    Object monitor = new Object();

    try {
      synchronized (monitor) {
        for (int i = 0; i < contendingJobCount; i++) {
          jobs.add(((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(() -> {
            contendingJobsStarted.countDown();
            synchronized (monitor) {
              enteredMonitorCount.incrementAndGet();
            }
          }, null));
        }

        assertTrue("Jobs did not yield while waiting to enter the synchronized block", contendingJobsStarted.await(10, TimeUnit.SECONDS));
        assertEquals(0, enteredMonitorCount.get());
        jobs.add(((JobLauncherImpl)JobLauncher.getInstance()).submitToJobThread(() -> {
          followingJobRan.countDown();
          assertEquals(0, enteredMonitorCount.get());
        }, null));
        assertTrue("No thread took over while the synchronized block was held",
                   followingJobRan.await(10, TimeUnit.SECONDS));
        assertEquals(0, enteredMonitorCount.get());
      }
    }
    finally {
      cancelAndWait(jobs);
    }
    assertEquals(contendingJobCount, enteredMonitorCount.get());
  }

  public void testNestedJobLauncherWorkIsStolenFromBlockedWorker() throws Exception {
    JobLauncherImpl launcher = new JobLauncherImpl(ForkJoinPool.commonPool(), 2);
    CountDownLatch blockedItemStarted = new CountDownLatch(1);
    CountDownLatch stolenItemProcessed = new CountDownLatch(1);
    Set<Thread> blockedRangeThreads = ContainerUtil.newConcurrentSet();
    AtomicBoolean result = new AtomicBoolean();

    Job job = launcher.submitToJobThread(() ->
      result.set(ProgressManager.getInstance().runProcess(
        () -> launcher.invokeConcurrentlyUnderContextProgress(List.of(0, 1, 2, 3), item -> {
          try {
            if (item == 2) {
              blockedRangeThreads.add(Thread.currentThread());
              blockedItemStarted.countDown();
              assertTrue(stolenItemProcessed.await(10, TimeUnit.SECONDS));
            }
            else if (item == 3) {
              blockedRangeThreads.add(Thread.currentThread());
              assertTrue(blockedItemStarted.await(10, TimeUnit.SECONDS));
              stolenItemProcessed.countDown();
            }
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          return true;
        }),
        new EmptyProgressIndicator())), null);

    try {
      assertTrue("Nested JobLauncher work was not stolen from the blocked worker", job.waitForCompletion(10_000));
      assertTrue(result.get());
      assertEquals(2, blockedRangeThreads.size());
    }
    finally {
      blockedItemStarted.countDown();
      stolenItemProcessed.countDown();
      cancelAndWait(List.of(job));
    }
  }
}
