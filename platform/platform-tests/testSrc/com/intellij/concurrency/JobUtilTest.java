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
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class JobUtilTest extends PlatformTestCase {
  private static final AtomicInteger COUNT = new AtomicInteger();
  private long timeOutMs;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    timeOutMs = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
  }
  private boolean timeOut(Object workProgress) {
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
    boolean b = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(things, new ProgressIndicatorBase(), false, false, o -> {
      busySleepAndIncrement(o);
      return true;
    });
    assertTrue(b);
    long elapsed = System.currentTimeMillis() - start;
    int expected = 2 * (9950 + 50 * 1000) / JobSchedulerImpl.getJobPoolParallelism();
    String message = "Elapsed: " + elapsed + "; expected: " + expected + "; parallelism=" + JobSchedulerImpl.getJobPoolParallelism() + "; current cores=" + Runtime.getRuntime().availableProcessors();
    assertTrue(message, elapsed <= expected);
  }

  private static int busySleepAndIncrement(int ms) {
    long end = System.currentTimeMillis() + ms;
    while (System.currentTimeMillis() < end);
    return COUNT.incrementAndGet();
  }
  private static int busySleepAndIncrement(int ms, @NotNull Runnable whileWait) {
    long end = System.currentTimeMillis() + ms;
    while (System.currentTimeMillis() < end)  {
      whileWait.run();
    }
    return COUNT.incrementAndGet();
  }

  public void testJobUtilCorrectlySplitsUpHugeWorkAndFinishesStress() throws Exception {
    COUNT.set(0);
    int N = Timings.adjustAccordingToMySpeed(100_000, true);
    List<String> list = Collections.nCopies(N, null);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    final AtomicBoolean finished = new AtomicBoolean();

    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, name -> {
      try {
        if (finished.get()) {
          throw new RuntimeException();
        }
        for (int i = 0; i < 1000; i++) {
          new BigDecimal(i).multiply(new BigDecimal(1));
        }
        busySleepAndIncrement(1);
        if (finished.get()) {
          throw new RuntimeException();
        }
      }
      catch (Exception e) {
        exception.set(e);
      }
      return true;
    });
    finished.set(true);
    Thread.sleep(1000);
    if (exception.get() != null) throw exception.get();
    assertEquals(N, COUNT.get());
  }

  public void testJobUtilProcessesAllItemsStress() throws Exception {
    List<String> list = Collections.nCopies(Timings.adjustAccordingToMySpeed(1000, true), null);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    for (int i=0; i<10 && !timeOut(i); i++) {
      long start = System.currentTimeMillis();
      COUNT.set(0);
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, name -> {
        busySleepAndIncrement(1);
        return true;
      });
      if (exception.get() != null) throw exception.get();
      long finish = System.currentTimeMillis();
      LOG.debug("Elapsed: "+(finish-start)+"ms");
      assertEquals(list.size(), COUNT.get());
    }
  }

  public void testJobUtilRecursiveStress() {
    int N = Timings.adjustAccordingToMySpeed(100, true);
    final List<String> list = Collections.nCopies(N, null);
    for (int i=0; i<10 && !timeOut(i); i++) {
      COUNT.set(0);
      long start = System.currentTimeMillis();
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, name -> {
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, name1 -> {
          busySleepAndIncrement(1);
          return true;
        });
        return true;
      });
      long finish = System.currentTimeMillis();
      LOG.debug("Elapsed: "+(finish-start)+"ms");
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

  private static void checkProgressAndReadAction(final List<Object> objects,
                                                 final DaemonProgressIndicator progress,
                                                 final boolean runInReadAction) throws Throwable {
    final AtomicReference<Throwable> exception = new AtomicReference<>();
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, progress, runInReadAction, o -> {
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
        exception.set(e);
      }
      return true;
    });
    if (exception.get() != null) throw exception.get();
  }

  private static class MyException extends RuntimeException {}
  public void testExceptionalCompletion() {
    COUNT.set(0);
    try {
      final List<Object> objects = Collections.nCopies(100_000_000, null);
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, true, o -> {
        if (COUNT.incrementAndGet() == 100_000) {
          LOG.debug("PCE");
          throw new MyException();
        }
        return true;
      });
      fail("exception must have been thrown");
    }
    catch (MyException e) {
      // caught OK
    }
  }
  public void testNotNormalCompletion() {
    COUNT.set(0);
    final List<Object> objects = Collections.nCopies(100_000_000, null);
    boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, true, o -> {
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
      boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, true, false, o -> {
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
      long start = System.currentTimeMillis();
      boolean success = false;
      try {
        success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, name -> {
          boolean nestedSuccess = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(ilist, null, false, integer -> {
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
      long finish = System.currentTimeMillis();
      LOG.debug("Elapsed: "+(finish-start)+"ms");
      //assertEquals(list.size()*list.size(), COUNT.get());
      assertFalse(success);
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
    final AtomicInteger delay = new AtomicInteger(0);
    final Runnable checkCanceled = ProgressManager::checkCanceled;
    Processor<String> processor = s -> {
      busySleepAndIncrement(delay.incrementAndGet() % 10 + 10, checkCanceled);
      return delay.get() % 100 != 0;
    };
    int N = Timings.adjustAccordingToMySpeed(10_000, true);
    for (int i=0; i<100 && !timeOut(i); i++) {
      ProgressIndicator indicator = new EmptyProgressIndicator();
      boolean result = JobLauncher.getInstance()
        .invokeConcurrentlyUnderProgress(Collections.nCopies(N, ""), indicator, false, false, processor);
      assertFalse(indicator.isCanceled());
      assertFalse(result);
    }
  }

  public void testTasksRunEvenWhenReadActionIsHardToGetStress() throws ExecutionException, InterruptedException {
    AtomicInteger processorCalled = new AtomicInteger();
    final Processor<String> processor = s -> {
      busySleepAndIncrement(1);
      processorCalled.incrementAndGet();
      return true;
    };
    for (int i=0; i<10 && !timeOut(i); i++) {
      LOG.debug("i = " + i);
      processorCalled.set(0);
      final ProgressIndicator indicator = new EmptyProgressIndicator();
      int N = Timings.adjustAccordingToMySpeed(1000, true);
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(N, ""), indicator, true, false, processor);
        assertFalse(indicator.isCanceled());
      });
      for (int k=0; k<N; k++) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          busySleepAndIncrement(1);
        });
      }
      future.get();
      assertEquals(N, processorCalled.get());
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
      long start = System.currentTimeMillis();
      while (!job.isDone() && (started.get() || System.currentTimeMillis() < start + 2000)) {
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
      while (!started.get());
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
      () -> JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(N_JOBS, null), indicator, false, o -> {
        jobsStarted.incrementAndGet();
        TimeoutUtil.sleep(10);
        return true;
      }), null);

    for (int i = 0; i < N_EVENTS; i++) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        int jobs0 = jobsStarted.get();
        long start = System.currentTimeMillis();
        while (jobsStarted.get() < jobs0 + JobSchedulerImpl.getJobPoolParallelism() && jobsStarted.get() < N_JOBS) {
          if (System.currentTimeMillis() > start + 10_000) {
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
      try {
        job.waitForCompletion(1000);
        UIUtil.dispatchAllInvocationEvents();
        break;
      }
      catch (TimeoutException ignored) {
      }
    }
  }
}
