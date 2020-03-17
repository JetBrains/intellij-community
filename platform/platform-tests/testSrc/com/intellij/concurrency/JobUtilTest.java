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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.util.TestTimeOut.setTimeout;

public class JobUtilTest extends LightPlatformTestCase {
  private static final AtomicInteger COUNT = new AtomicInteger();
  private TestTimeOut t;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    t = setTimeout(2, TimeUnit.MINUTES);
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
    long expected = sum / JobSchedulerImpl.getJobPoolParallelism();
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
  public void testCorrectlySplitsUpHugeWorkAndFinishesStress() throws Throwable {
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
    for (int i = 0; i<10 && !t.timedOut(i); i++) {
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

  private static void logElapsed(ThrowableRunnable<RuntimeException> r) {
    LOG.debug("Elapsed: " + TimeoutUtil.measureExecutionTime(r) + "ms");
  }

  public void testJobUtilRecursiveStress() {
    int N = Timings.adjustAccordingToMySpeed(40, true);
    List<String> list = Collections.nCopies(N, null);
    for (int i = 0; i<10 && !t.timedOut(i); i++) {
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
  public void testThrowExceptionInProcessorMustBubbleUpToInvokeConcurrently() {
    checkExceptionBubblesUp(new RuntimeException());
    checkExceptionBubblesUp(new MyException());
    checkExceptionBubblesUp(new Error());
    checkExceptionBubblesUp(new IncorrectOperationException());
    //checkExceptionBubblesUp(new ProcessCanceledException());
  }

  private static void checkExceptionBubblesUp(Throwable ex) {
    COUNT.set(0);
    try {
      final List<Object> objects = Collections.nCopies(100_000, null);
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, __ -> {
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
      fail(ex+" exception must have been thrown");
    }
    catch (Throwable e) {
      assertSame(ex, e);
    }
  }

  public void testIndicatorCancelMustEnsuePCE() {
    try {
      ProgressIndicator progress = new DaemonProgressIndicator();
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(100_000, null), progress, __ -> {
        if (COUNT.incrementAndGet() == 10_000) {
          progress.cancel();
        }
        return true;
      });
      fail("PCE must have been thrown");
    }
    catch (ProcessCanceledException ignored) {
    }
  }

  public void testReturnFalseFromProcessorMustLeadToReturningFalseFromInvokeConcurrently() {
    COUNT.set(0);
    final List<Object> objects = Collections.nCopies(100_000, null);
    boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, __ -> COUNT.incrementAndGet() != 10_000);
    assertFalse(success);
  }

  public void testCompletesEvenIfCannotGrabReadAction() {
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

  public void testRecursiveCancel() {
    final List<String> list = Collections.nCopies(100, "");
    final List<Integer> ilist = Collections.nCopies(100, 0);
    for (int i = 0; i<10 && !t.timedOut(i); i++) {
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

  public void testSaturation() throws InterruptedException, TimeoutException, ExecutionException {
    final CountDownLatch latch = new CountDownLatch(1);
    List<Job> jobs = new ArrayList<>();
    for (int i = 0; i<100 && !t.timedOut(i); i++) {
      jobs.add(JobLauncher.getInstance().submitToJobThread(() -> {
        try {
          latch.await();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }, null));
    }
    jobs.add(JobLauncher.getInstance().submitToJobThread(latch::countDown, null));

    try {
      boolean scheduled = latch.await(3, TimeUnit.SECONDS);
      assertFalse(scheduled); // pool saturated, no thread can be scheduled
    }
    finally {
      latch.countDown();
      cancelAndWait(jobs);
    }
  }

  private static void cancelAndWait(List<? extends Job> jobs) throws InterruptedException, ExecutionException, TimeoutException {
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
    Processor<String> processor = __ -> {
      int next = processed.incrementAndGet();
      busySleepAndIncrement(next % 4, ProgressManager::checkCanceled);
      return next % 100 != 0;
    };
    int N = Timings.adjustAccordingToMySpeed(10_000, true);
    for (int i = 0; i<100 && !t.timedOut(i); i++) {
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
    for (int i = 0; i<10 && !t.timedOut(i); i++) {
      COUNT.set(0);
      final ProgressIndicator indicator = new EmptyProgressIndicator();
      AtomicBoolean runReads = new AtomicBoolean(true);
      Semaphore startedReads = new Semaphore(1);
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        startedReads.up();
        while (runReads.get() && !t.timedOut()) {
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

  public void testAfterCancelInTheMiddleOfTheExecutionTaskIsDoneReturnsFalseUntilFinished()
    throws InterruptedException, ExecutionException, TimeoutException {
    Random random = new Random();
    for (int i = 0; i<100 && !t.timedOut(i); i++) {
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

      while (!started.get() && !t.timedOut(i)) {
        // need to test cancel() after started
      }
      while (!t.timedOut(i)) {
        boolean isDone = job.isDone();
        boolean isFinished = finished.get();

        // the only forbidden state is isDone == 1, isFinished == 0
        if (isDone && !isFinished) {
          fail("isDone: " + isDone + "; isFinished: +" + isFinished);
        }
        if (isDone) {
          break;
        }
      }
      cancelAndWait(Collections.singletonList(job));
    }
  }

  public void testJobWaitForTerminationAfterCancelInTheMiddleOfTheExecutionWaitsUntilFinished() throws Exception {
    for (int i=0; i<100 && !t.timedOut(i); i++) {
      final AtomicBoolean finished = new AtomicBoolean();
      final AtomicBoolean started = new AtomicBoolean();
      Job<Void> job = JobLauncher.getInstance().submitToJobThread(() -> {
        started.set(true);
        TimeoutUtil.sleep(100);
        finished.set(true);
      }, null);
      assertFalse(job.isDone());
      while (!started.get()) {
        assertFalse(t.timedOut(i));
      }
      assertTrue(started.get());
      job.cancel();
      try {
        job.waitForCompletion(100_000);
      }
      catch (TimeoutException e) {
        System.err.println(ThreadDumper.dumpThreadsToString());
        throw e;
      }
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
      TestTimeOut n = setTimeout(10, TimeUnit.SECONDS);
      int finalI = i;
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        int jobs0 = jobsStarted.get();
        while (jobsStarted.get() < jobs0 + JobSchedulerImpl.getJobPoolParallelism() && jobsStarted.get() < N_JOBS) {
          if (n.timedOut(finalI)) {
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
    try {
      job.waitForCompletion(10_000);
      fail();
    }
    catch (ProcessCanceledException ignored) {
    }
    assertTrue(job.isDone());
    assertTrue(counter.toString(), counter.get() < N);
    future.get();
  }

  public void testExecuteAllMustBeResponsiveToTheIndicatorCancelWhenWaitsEvenForExtraCoarseGranularTasks() throws Throwable {
    int COARSENESS = 100_000;
    List<Job> jobs = new ArrayList<>();
    // try to repeat until got into the right thread; but not for too long
    try {
      for (int i=0; i<1000; i++) {
        ProgressIndicator indicator = new DaemonProgressIndicator();
        Semaphore run = new Semaphore(1);
        AtomicReference<Thread> mainThread = new AtomicReference<>();
        AtomicBoolean stealHappened = new AtomicBoolean();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(TimeoutUtil.measureExecutionTime(() -> {
          // run lengthy process in FJP,
          // in which call invokeConcurrentlyUnderProgress() which normally takes 100s
          // and cancel the indicator in the meantime
          // check that invokeConcurrentlyUnderProgress() gets canceled immediately
          Job<Void> job = JobLauncher.getInstance().submitToJobThread(() -> {
            // to ensure lengthy task executes in thread other that the one which called invokeConcurrentlyUnderProgress()
            // otherwise (when the thread doing sleep(COARSENESS) is the same which did invokeConcurrentlyUnderProgress) it means that FJP stole the task, started executing it in the waiting thread and we can't do anything
            mainThread.set(Thread.currentThread());
            try {
              ProgressManager.getInstance().runProcess(() -> {
                // more than 1 to pass through processIfTooFew
                List<Integer> things = Arrays.asList(1, 1, 1, COARSENESS);
                AtomicInteger count = new AtomicInteger();
                boolean ok = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(things, indicator, delay -> {
                  if (delay == COARSENESS) {
                    indicator.cancel(); // emulate job external cancel
                  }
                  // we seek to test the situation of "job submitted to FJP is waiting for lengthy task created via invokeConcurrentlyUnderProgress()"
                  // so when the main job steals that lengthy task from within .get() we balk out
                  if (Thread.currentThread() == mainThread.get()) {
                    stealHappened.set(true);
                  }
                  else {
                    TimeoutUtil.sleep(delay);
                  }
                  count.incrementAndGet();
                  return true;
                });

                assertTrue(!ok || stealHappened.get());
              }, indicator);
            }
            catch (ProcessCanceledException ignored) {
            }
            catch (Throwable e) {
              exception = e;
            }
            finally {
              run.up();
            }
          }, null);
          jobs.add(job);

          boolean ok = run.waitFor(30_000);
          assertTrue(ok);
          cancelAndWait(Collections.singletonList(job));
        }));
        if (exception != null) throw exception;
        assertTrue(String.valueOf(elapsed), elapsed < COARSENESS);

        if (!stealHappened.get()) break; // tested that we wanted
      }
    }
    finally {
      cancelAndWait(jobs);
    }
  }

  public void testInvokeConcurrentlyMustExecuteMultipleTasksConcurrentlyEvenIfOneOfThemIsWildlySlow() {
    int parallelism = JobSchedulerImpl.getJobPoolParallelism();
    Assume.assumeTrue("Too low parallelism: " + parallelism + ", I give up", parallelism > 1);
    // values higher than this can lead to too coarse tasks in FJP queue which means some of them may contain more than one element which means long-delay task and some shot-delay tasks can be scheduled to one chunk which means some of the tasks will not be stolen for a (relatively)long time.
    // so it's a TODO for ApplierCompleter
    int N = 1 << parallelism;
    Integer[] times = new Integer[N];
    Arrays.fill(times, 0);
    class MyException extends RuntimeException { }
    int longDelay = (int)TimeUnit.MINUTES.toMillis(2);
    for (int i=0; i<100; i++) {
      times[(i + N - 1) % N] = 0;
      times[i % N] = longDelay;

      AtomicInteger executed = new AtomicInteger();
      DaemonProgressIndicator progress = new DaemonProgressIndicator();
      try {
        TestTimeOut deadline = setTimeout(2, TimeUnit.SECONDS);
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Arrays.asList(times.clone()), progress, time -> {
          while ((time -= 100) >= 0) {
            ProgressManager.checkCanceled();
            TimeoutUtil.sleep(100);
            if (deadline.isTimedOut()) {
              String s = ThreadDumper.dumpThreadsToString();
              throw new AssertionError("Timed out at " + time + "; threads:\n" + s);
            }
          }

          if (executed.incrementAndGet() >= times.length - 1) {
            // executed all but the slowest one
            throw new MyException();
          }
          return true;
        });
        fail();
      }
      catch (MyException ignored) {
      }
    }
  }

  public void testJobWaitForTerminationAfterCancelInTheMiddleOfTheExecutionWaitsUntilFinished2() {
    List<Integer> ints = IntStream.range(1, 123_271*JobSchedulerImpl.getJobPoolParallelism()/11).boxed().collect(Collectors.toList());
    for (int i=0; i<10 && !t.timedOut(i); i++) {
      AtomicInteger executed = new AtomicInteger();
      AtomicBoolean returnedFalse = new AtomicBoolean();
      boolean result =
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(ints, new DaemonProgressIndicator(), n -> {
          if (!returnedFalse.get()) {
            TimeoutUtil.sleep(ThreadLocalRandom.current().nextBoolean() ? 1 : 0);
          }
          executed.incrementAndGet();
          if (n % 10_000 == 0) {
            returnedFalse.set(true);
            return false;
            //throw new ProcessCanceledException();
          }
          return true;
        });

      int count = executed.get();
      assertFalse(result);

      TestTimeOut w = setTimeout(1000, TimeUnit.MILLISECONDS);
      while (!w.isTimedOut()) {
        assertEquals(count, executed.get());
      }
    }
  }
}
