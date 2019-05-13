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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class JobUtilTest extends LightPlatformTestCase {
  private static final AtomicInteger COUNT = new AtomicInteger();
  private long timeOutMs;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setTimeout(TimeUnit.MINUTES.toMillis(2));
  }

  private void setTimeout(long ms) {
    timeOutMs = System.currentTimeMillis() + ms;
  }

  private boolean timeOut(int workProgress) {
    if (System.currentTimeMillis() > timeOutMs) {
      System.err.println("Timed out. Stopped at "+workProgress);
      return true;
    }
    return false;
  }

  public void testUnbalancedTaskJobUtilPerformance() {
    List<Integer> things = new ArrayList<>(Collections.nCopies(10_000, null));
    int sum = 0;
    for (int i = 0; i < things.size(); i++) {
      int v = i < 9950 ? 1 : 1000;
      things.set(i, v);
      sum += things.get(i);
    }
    assertEquals(59950, sum);

    long start = System.currentTimeMillis();
    boolean b = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(things, new ProgressIndicatorBase(), o -> {
      busySleepAndIncrement(o);
      return true;
    });
    assertTrue(b);
    long elapsed = System.currentTimeMillis() - start;
    long expected = (9950 + (things.size() - 9950) * 1000L) / JobSchedulerImpl.getJobPoolParallelism();
    String message = "Elapsed: " + elapsed + "; expected: " + expected + "; parallelism=" + JobSchedulerImpl.getJobPoolParallelism() + "; current cores=" + Runtime.getRuntime().availableProcessors();
    assertTrue(message, elapsed <= 2 * expected);
  }

  private static int busySleepAndIncrement(int ms) {
    return busySleepAndIncrement(ms, EmptyRunnable.getInstance());
  }
  private static int busySleepAndIncrement(int ms, @NotNull Runnable doWhileWait) {
    long end = System.currentTimeMillis() + ms;
    int nap = Math.max(1, ms / 100);
    while (System.currentTimeMillis() < end)  {
      TimeoutUtil.sleep(nap);
      doWhileWait.run();
    }
    return COUNT.incrementAndGet();
  }

  private volatile Throwable exception;
  public void testJobUtilCorrectlySplitsUpHugeWorkAndFinishesStress() throws Throwable {
    COUNT.set(0);
    int N = Timings.adjustAccordingToMySpeed(20_000, true);
    final AtomicBoolean finished = new AtomicBoolean();

    boolean ok = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.<String>nCopies(N, null), null, __ -> {
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
    });
    assertTrue(ok);
    finished.set(true);
    if (exception != null) throw exception;
    assertEquals(N, COUNT.get());
  }

  public void testJobUtilProcessesAllItemsStress() throws Throwable {
    List<String> list = Collections.nCopies(Timings.adjustAccordingToMySpeed(1000, true), null);
    for (int i=0; i<10 && !timeOut(i); i++) {
      COUNT.set(0);
      logElapsed(()->
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, __ -> {
        busySleepAndIncrement(1);
        return true;
      }));
      if (exception != null) throw exception;
      assertEquals(list.size(), COUNT.get());
    }
  }

  private static void logElapsed(Runnable r) {
    LOG.debug("Elapsed: " + PlatformTestUtil.measure(r) + "ms");
  }

  public void testJobUtilRecursiveStress() {
    int N = Timings.adjustAccordingToMySpeed(40, true);
    List<String> list = Collections.nCopies(N, null);
    for (int i=0; i<10 && !timeOut(i); i++) {
      COUNT.set(0);
      logElapsed(()->
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, __ -> {
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, ___ -> {
          COUNT.incrementAndGet();
          return true;
        });
        return true;
      }));
      assertEquals(list.size()*list.size(), COUNT.get());
    }
  }

  public void testCorrectProgressAndReadAction() throws Throwable {
    checkProgressAndReadAction(Collections.singletonList(null), new DaemonProgressIndicator(), true);
    checkProgressAndReadAction(Collections.singletonList(null), new DaemonProgressIndicator(), false);
    checkProgressAndReadAction(Collections.emptyList(), new DaemonProgressIndicator(), true);
    checkProgressAndReadAction(Collections.emptyList(), new DaemonProgressIndicator(), false);
    checkProgressAndReadAction(Arrays.asList(new Object(), new Object()), new DaemonProgressIndicator(), true);
    checkProgressAndReadAction(Arrays.asList(new Object(), new Object()), new DaemonProgressIndicator(), false);
    checkProgressAndReadAction(Arrays.asList(new Object(), new Object()), null, false);
  }

  private void checkProgressAndReadAction(final List<Object> objects,
                                          final DaemonProgressIndicator progress,
                                          final boolean runInReadAction) throws Throwable {
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, progress, __ -> {
      ThrowableRunnable<RuntimeException> runnable = () -> {
          try {
            if (objects.size() <= 1 || JobSchedulerImpl.getJobPoolParallelism() <= JobLauncherImpl.CORES_FORK_THRESHOLD) {
              assertTrue(ApplicationManager.getApplication().isDispatchThread());
            }
            else {
              // generally we know nothing about current thread since FJP can help others task to execute while in current context
            }
            ProgressIndicator actualIndicator = ProgressManager.getInstance().getProgressIndicator();
            if (progress == null) {
              assertNotNull(actualIndicator);
              assertTrue(actualIndicator instanceof AbstractProgressIndicatorBase);
            }
            else {
              assertTrue(actualIndicator instanceof SensitiveProgressWrapper);
              ProgressIndicator original = ((SensitiveProgressWrapper)actualIndicator).getOriginalProgressIndicator();
              assertSame(progress, original);
            }
            // there can be read access even if we didn't ask for it (e.g. when task under read action steals others work)
            assertTrue(!runInReadAction || ApplicationManager.getApplication().isReadAccessAllowed());
          }
          catch (Throwable e) {
            exception = e;
          }
      };
      if (runInReadAction) {
        ReadAction.run(runnable);
      }
      else {
        runnable.run();
      }

      return true;
    });
    if (exception != null) throw exception;
  }

  private static class MyException extends RuntimeException {}
  public void testThrowExceptionMustBubbleUp() {
    checkExceptionBubblesUp(new RuntimeException());
    checkExceptionBubblesUp(new MyException());
    checkExceptionBubblesUp(new Error());
    //checkExceptionBubblesUp(new ProcessCanceledException());
  }

  private static void checkExceptionBubblesUp(Throwable ex) {
    COUNT.set(0);
    try {
      final List<Object> objects = Collections.nCopies(100_000_000, null);
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, __ -> {
        if (COUNT.incrementAndGet() == 100_000) {
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
      fail("exception must have been thrown");
    }
    catch (Throwable e) {
      assertSame(ex, e);
    }
  }

  public void testNotNormalCompletion() {
    COUNT.set(0);
    final List<Object> objects = Collections.nCopies(100_000_000, null);
    boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, __ -> {
      if (COUNT.incrementAndGet() == 100_000) {
        LOG.debug("PCE");
        return false;
      }
      return true;
    });
    assertFalse(success);
  }

  public void testJobUtilCompletesEvenIfCannotGrabReadAction() {
    COUNT.set(0);
    final List<Object> objects = Collections.nCopies(1_000_000, null);
    ApplicationManager.getApplication().runWriteAction(() -> {
      boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, true, false, __ -> {
        COUNT.incrementAndGet();
        return true;
      });
      assertTrue(success);
      assertEquals(objects.size(), COUNT.get());
    });
  }

  public void testJobUtilRecursiveCancel() {
    final List<String> list = Collections.nCopies(100, "");
    final List<Integer> ilist = Collections.nCopies(100, 0);
    for (int i=0; i<10 && !timeOut(i); i++) {
      COUNT.set(0);
      boolean[] success = new boolean[1];
      logElapsed(()-> {
        try {
          success[0] = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, __ -> {
            boolean nestedSuccess = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(ilist, null, ___ -> {
              if (busySleepAndIncrement(1) == 1000) {
                LOG.debug("PCE");
                throw new MyException();
              }
              return true;
            });
            //System.out.println("nestedSuccess = " + nestedSuccess);
            return true;
          });
          fail("exception must have been thrown");
        }
        catch (MyException ignored) {
        }
      });
      //assertEquals(list.size()*list.size(), COUNT.get());
      assertFalse(success[0]);
    }
  }

  public void testSaturation() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    for (int i=0; i<100 && !timeOut(i); i++) {
      JobLauncher.getInstance().submitToJobThread(() -> {
        try {
          latch.await();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }, null);
    }
    JobLauncher.getInstance().submitToJobThread(latch::countDown, null);

    try {
      boolean scheduled = latch.await(3, TimeUnit.SECONDS);
      assertFalse(scheduled); // pool saturated, no thread can be scheduled
    }
    finally {
      latch.countDown();
    }
  }

  public void testProcessorReturningFalseDoesNotCrashTheOtherThreadStress() {
    AtomicInteger processed = new AtomicInteger();
    Processor<String> processor = __ -> {
      int next = processed.incrementAndGet();
      busySleepAndIncrement(next % 4, ProgressManager::checkCanceled);
      return next % 100 != 0;
    };
    int N = Timings.adjustAccordingToMySpeed(10_000, true);
    for (int i=0; i<100 && !timeOut(i); i++) {
      processed.set(0);
      ProgressIndicator indicator = new ProgressIndicatorBase();
      boolean result = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(N, ""), indicator, processor);
      assertFalse(indicator.isCanceled());
      assertFalse(result);
    }
  }

  public void testTasksRunEvenWhenReadActionIsHardToGetStress() throws Exception {
    final Processor<String> processor = __ -> {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      return true;
    };
    int N = Timings.adjustAccordingToMySpeed(300, true);
    for (int i=0; i<10 && !timeOut(i); i++) {
      COUNT.set(0);
      final ProgressIndicator indicator = new EmptyProgressIndicator();
      AtomicBoolean runReads = new AtomicBoolean(true);
      Semaphore startedReads = new Semaphore(1);
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        startedReads.up();
        while (runReads.get() && !timeOut(0)) {
          JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(N, ""),
                                                                    indicator, true, false, processor);
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

  public void testAfterCancelInTheMiddleOfTheExecutionTaskIsDoneReturnsFalseUntilFinished() {
    Random random = new Random();
    for (int i=0; i<100 && !timeOut(i); i++) {
      final AtomicBoolean finished = new AtomicBoolean();
      final AtomicBoolean started = new AtomicBoolean();
      Job<Void> job = JobLauncher.getInstance().submitToJobThread(() -> {
        started.set(true);
        TimeoutUtil.sleep(100);
        finished.set(true);
      }, null);
      assertFalse(job.isDone());
      TimeoutUtil.sleep(random.nextInt(100));
      job.cancel();
      while (!job.isDone() && !timeOut(i)) {
        boolean wasDone = job.isDone();
        boolean wasStarted = started.get();
        boolean wasFinished = finished.get();
        if (wasStarted && !wasFinished) {
          assertFalse(wasDone);
        }
        // else no guarantees

        // but can be finished=true but done=false still
        if (wasDone) {
          assertTrue(wasFinished);
        }
      }
      assertTrue(job.isDone());
    }
  }

  public void testJobWaitForTerminationAfterCancelInTheMiddleOfTheExecutionWaitsUntilFinished() throws Exception {
    for (int i=0; i<100 && !timeOut(i); i++) {
      final AtomicBoolean finished = new AtomicBoolean();
      final AtomicBoolean started = new AtomicBoolean();
      Job<Void> job = JobLauncher.getInstance().submitToJobThread(() -> {
        started.set(true);
        TimeoutUtil.sleep(100);
        finished.set(true);
      }, null);
      assertFalse(job.isDone());
      while (!started.get() && !timeOut(i)) {

      }
      assertTrue(started.get());
      job.cancel();
      job.waitForCompletion(100_000);
      assertTrue(finished.get());
    }
  }

  public void testDaemonDoesNotPauseWhenEventDispatcherHasEventsInTheQueueStress() throws Throwable {
    assertTrue(SwingUtilities.isEventDispatchThread());

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    final AtomicInteger jobsStarted = new AtomicInteger();
    final int N_EVENTS = 50;
    final int N_JOBS = 10_000 * JobSchedulerImpl.getJobPoolParallelism();
    ProgressIndicator indicator = new DaemonProgressIndicator();

    Job<Void> job = JobLauncher.getInstance().submitToJobThread(
      () -> JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(N_JOBS, null), indicator, __ -> {
        jobsStarted.incrementAndGet();
        TimeoutUtil.sleep(10);
        return true;
      }), null);

    for (int i = 0; i < N_EVENTS; i++) {
      setTimeout(TimeUnit.SECONDS.toMillis(10));
      int finalI = i;
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        int jobs0 = jobsStarted.get();
        while (jobsStarted.get() < jobs0 + JobSchedulerImpl.getJobPoolParallelism() && jobsStarted.get() < N_JOBS) {
          if (timeOut(finalI)) {
            System.err.println(ThreadDumper.dumpThreadsToString());
            fail();
            break;
          }
        }
        //int jobs1 = jobsStarted.get();
        //System.out.println("jobs0 = "+jobs0+"; jobs1 = "+jobs1);
      });
      UIUtil.dispatchAllInvocationEvents();
    }
    indicator.cancel();
    job.cancel();
    while (!job.isDone()) {
      UIUtil.dispatchAllInvocationEvents();
      try {
        job.waitForCompletion(1000);
        UIUtil.dispatchAllInvocationEvents();
        break;
      }
      catch (TimeoutException ignored) {
      }
    }
  }

  public void testExecuteAllMustBeResponsiveToTheIndicatorCancelWhenWaitsForTheOtherTasksToComplete()
    throws InterruptedException, ExecutionException, TimeoutException {
    ProgressIndicator indicator = new DaemonProgressIndicator();
    int N = 100_000;
    AtomicInteger counter = new AtomicInteger();
    // run lengthy process in FJP,
    // in which call invokeConcurrentlyUnderProgress() which normally takes 100s
    // and cancel the indicator in the meantime
    // check that invokeConcurrentlyUnderProgress() gets canceled immediately
    Job<Void> job = JobLauncher.getInstance().submitToJobThread(() -> ProgressManager.getInstance().runProcess(()->
        assertFalse(JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(N, null), indicator, __->{
          TimeoutUtil.sleep(1);
          counter.incrementAndGet();
          return true;
    })), indicator), null);
    ScheduledFuture<?> future = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> indicator.cancel(), 10, TimeUnit.MILLISECONDS);
    job.waitForCompletion(10_000);
    assertTrue(job.isDone());
    assertTrue(counter.toString(), counter.get() < N);
    future.get();
  }

  public void testExecuteAllMustBeResponsiveToTheIndicatorCancelWhenWaitsEvenForExtraCoarseGranularTasks() throws Throwable {
    int COARSENESS = 100_000;
    // try to repeat until got into the right thread; but not for too long
    for (int i=0; i<1000; i++) {
      ProgressIndicator indicator = new DaemonProgressIndicator();
      AtomicLong elapsed = new AtomicLong(Long.MAX_VALUE);
      Semaphore run = new Semaphore(1);
      AtomicReference<Thread> mainThread = new AtomicReference<>();
      AtomicBoolean stealHappened = new AtomicBoolean();
      // run lengthy process in FJP,
      // in which call invokeConcurrentlyUnderProgress() which normally takes 100s
      // and cancel the indicator in the meantime
      // check that invokeConcurrentlyUnderProgress() gets canceled immediately
      JobLauncher.getInstance().submitToJobThread(() -> {
        // to ensure lengthy task executes in thread other that the one which called invokeConcurrentlyUnderProgress()
        // otherwise (when the thread doing sleep(COARSENESS) is the same which did invokeConcurrentlyUnderProgress) it means that FJP stole the task, started executing it in the waiting thread and we can't do anything
        mainThread.set(Thread.currentThread());
        try {
          elapsed.set(PlatformTestUtil.measure(() -> ProgressManager.getInstance().runProcess(()-> {
            boolean ok = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(/* more than 1 to pass through processIfTooFew */Arrays.asList(1, 1, 1, COARSENESS), indicator, delay -> {
              if (delay == COARSENESS) {
                indicator.cancel(); // emulate job external cancel
              }
              // we seek to test the situation of "job submitted to FJP is waiting for lengthy task crated via invokeConcurrentlyUnderProgress()"
              // so when the main job steals that lengthy task from within .get() we balk out
              if (Thread.currentThread() != mainThread.get()) {
                TimeoutUtil.sleep(delay);
              }
              else {
                stealHappened.set(true);
              }
              return true;
            });

            assertTrue(!ok || stealHappened.get());
          }, indicator)));
        }
        catch (Throwable e) {
          exception = e;
        }
        finally {
          run.up();
        }
      }, null);

      boolean ok = run.waitFor(30_000);
      if (exception != null) throw exception;
      assertTrue(ok);
      assertTrue(elapsed.toString(), elapsed.get() < COARSENESS);

      if (!stealHappened.get()) break; // tested that we wanted
    }
  }
}
