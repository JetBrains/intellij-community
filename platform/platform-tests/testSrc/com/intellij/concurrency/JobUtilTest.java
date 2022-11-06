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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  private static final Logger LOG = Logger.getInstance(JobUtilTest.class);
  private static final AtomicInteger COUNT = new AtomicInteger();
  private TestTimeOut t;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    COUNT.set(0);
    t = setTimeout(2, TimeUnit.MINUTES);
    int parallelism = JobSchedulerImpl.getJobPoolParallelism();
    Assume.assumeTrue("Too low parallelism: " + parallelism + ", I give up", parallelism >= 4);
  }

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

    long elapsed = TimeoutUtil.measureExecutionTime(() -> assertTrue(JobLauncher.getInstance().invokeConcurrentlyUnderProgress(things, new ProgressIndicatorBase(), o -> {
      if (o <= 1) {
        busySleepAndIncrement(o);
      }
      else {
        longSleep(o);
      }
      return true;
    })));
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

  private static void logElapsed(@NotNull Runnable r) {
    LOG.debug("Elapsed: " + TimeoutUtil.measureExecutionTime(r::run) + "ms");
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

  private void checkProgressAndReadAction(@NotNull List<Object> objects,
                                          @Nullable DaemonProgressIndicator progress,
                                          boolean runInReadAction) throws Throwable {
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
            // there can be read access even if we didn't ask for it (e.g., when task under read action steals others work)
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
    UsefulTestCase.assertThrows(ex.getClass(), "myMsg", () ->
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
      }));
  }

  public void testIndicatorCancelMustEnsuePCE() {
    ProgressIndicator progress = new DaemonProgressIndicator();
    UsefulTestCase.assertThrows(ProcessCanceledException.class, () ->
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(100_000, null), progress, __ -> {
        if (COUNT.incrementAndGet() == 10_000) {
          progress.cancel();
        }
        return true;
      }));
    assertTrue(progress.isCanceled());
  }

  public void testReturnFalseFromProcessorMustLeadToReturningFalseFromInvokeConcurrently() {
    List<Object> objects = Collections.nCopies(100_000, null);
    boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, __ -> COUNT.incrementAndGet() != 10_000);
    assertFalse(success);
  }

  public void testCompletesEvenIfCannotGrabReadAction() {
    List<Object> objects = Collections.nCopies(1_000_000, null);
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
    List<Integer> list = IntStream.range(0, 100).boxed().collect(Collectors.toList());
    for (int i = 0; i<10 && !t.timedOut(i); i++) {
      int fingerPrint = i;
      COUNT.set(0);
      LOG.debug("--- " + i+"; fingerPrint="+fingerPrint+"; COUNT="+COUNT);
      boolean[] success = new boolean[1];
      logElapsed(()->
        UsefulTestCase.assertThrows(MyException.class, "myMsg", () ->
          success[0] = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, ind -> {
            boolean nestedSuccess = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, nestedInd -> {
              if (busySleepAndIncrement(1) % 1024 == 0) {
                LOG.debug("throw myMsg ind=" + ind + "; nestedInd=" + nestedInd+"; fingerPrint="+fingerPrint+"; COUNT="+COUNT);
                throw new MyException("myMsg"+fingerPrint+"; COUNT="+COUNT);
              }
              return true;
            });
            LOG.debug("nestedSuccess: " + nestedSuccess + "; ind:" + ind+"; fingerPrint="+fingerPrint+"; COUNT="+COUNT);
            return true;
          })
        ));
      assertFalse(success[0]);
    }
  }

  public void testSaturation() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<Job<?>> jobs = new ArrayList<>();
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

  private static void cancelAndWait(List<? extends Job<?>> jobs) throws Exception {
    for (Job<?> job : jobs) {
      job.cancel();
    }
    for (Job<?> job : jobs) {
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
    Processor<String> processor = __ -> {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      return true;
    };
    int N = Timings.adjustAccordingToMySpeed(300, true);
    for (int i = 0; i<10 && !t.timedOut(i); i++) {
      COUNT.set(0);
      ProgressIndicator indicator = new EmptyProgressIndicator();
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

  // create bounded queue and spawn producer which fills this queue with elements
  // spawn consumer which calls JobLauncherImpl.processQueue() for that queue,
  // stress EDT with periodic write actions and check that processQueue() doesn't lose elements due to
  // constant PCEs and multiple restarts
  public void testProcessInOrderWorksEvenWhenReadActionIsHardToGetStress() throws Exception {
    String TOMB_STONE = "TOMB_STONE";
    // schedule huge number of write actions interrupting processQueue
    Future<?> interrupts = EdtScheduledExecutorService.getInstance().scheduleWithFixedDelay(() ->
        WriteAction.run(() -> { }), 1, 1, TimeUnit.MICROSECONDS);
    for (int i = 0; i<10 && !t.timedOut(i); i++) {
      BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
      int N_ELEMENTS = 10000;
      COUNT.set(0);
      Future<?> supplyFuture = supplyElementsInBatchesInBackground(queue, N_ELEMENTS, TOMB_STONE);
      Queue<String> failedQueue = new LinkedBlockingQueue<>();
      Future<?> background = AppExecutorUtil.getAppExecutorService().submit(() -> processQueueInBackground(TOMB_STONE, queue, failedQueue));
      while (!background.isDone() && !t.timedOut()) {
        UIUtil.dispatchAllInvocationEvents();
      }
      supplyFuture.get();
      assertEquals(COUNT.get(), N_ELEMENTS);
    }
    interrupts.cancel(true);
    while (!interrupts.isDone() && !t.timedOut()) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  private static void processQueueInBackground(@NotNull String TOMB_STONE, @NotNull BlockingQueue<String> queue, @NotNull Queue<String> failedQueue) {
    while (true) {
      Disposable disposable = Disposer.newDisposable();
      ProgressIndicator wrapper = new DaemonProgressIndicator();
      try {
        // avoid "attach listener"/"write action" race
        ReadAction.run(() -> {
          wrapper.start();
          ProgressIndicatorUtils.forceWriteActionPriority(wrapper, disposable);
          // there is a chance we are racing with write action, in which case just registered listener might not be called, retry.
          if (ApplicationManagerEx.getApplicationEx().isWriteActionPending()) {
            throw new ProcessCanceledException();
          }
        });
        // use wrapper here to cancel early when write action start but do not affect the original indicator
        ((JobLauncherImpl)JobLauncher.getInstance()).processQueue(queue, failedQueue, wrapper, TOMB_STONE, __ -> {
          ReadAction.run(() -> {
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
    for (int i = 0; i<100 && !t.timedOut(i); i++) {
      AtomicBoolean finished = new AtomicBoolean();
      AtomicBoolean started = new AtomicBoolean();
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
        assertFalse(isDone && !isFinished);
        if (isDone) {
          break;
        }
      }
      cancelAndWait(Collections.singletonList(job));
    }
  }

  public void testJobWaitForTerminationAfterCancelInTheMiddleOfTheExecutionWaitsUntilFinished() throws Exception {
    for (int i=0; i<100 && !t.timedOut(i); i++) {
      AtomicBoolean finished = new AtomicBoolean();
      AtomicBoolean started = new AtomicBoolean();
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
      job.waitForCompletion(1000);
    }
  }

  public void testExecuteAllMustBeResponsiveToTheIndicatorCancelWhenWaitsForTheOtherTasksToComplete() throws Exception {
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
    Future<?> future = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> indicator.cancel(), 10, TimeUnit.MILLISECONDS);
    UsefulTestCase.assertThrows(ProcessCanceledException.class, ()-> job.waitForCompletion(10_000));
    assertTrue(job.isDone());
    assertTrue(counter.toString(), counter.get() < N);
    future.get();
  }

  public void testExecuteAllMustBeResponsiveToTheIndicatorCancelWhenWaitsEvenForExtraCoarseGranularTasksStress() throws Throwable {
    int COARSENESS = 100_000;
    List<Job<?>> jobs = new ArrayList<>();
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
            // otherwise (when the thread doing sleep(COARSENESS) is the same which did invokeConcurrentlyUnderProgress) it means that FJP stole the task, started executing it in the waiting thread, and we can't do anything
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
          String threadDump = ThreadDumper.dumpThreadsToString();
          assertTrue(threadDump, ok);
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

  public void testInvokeConcurrentlyMustExecuteMultipleTasksConcurrentlyEvenIfOneOfThemIsWildlySlowStress() {
    int parallelism = JobSchedulerImpl.getJobPoolParallelism();
    // values higher than this can lead to too coarse tasks in FJP queue which means some of them may contain more than one element which means long-delay task and some shot-delay tasks can be scheduled to one chunk which means some of the tasks will not be stolen for a (relatively)long time.
    // so it's a TODO for ApplierCompleter
    int N = 1 << parallelism;
    Integer[] times = new Integer[N];
    Arrays.fill(times, 0);
    int longDelay = (int)TimeUnit.MINUTES.toMillis(2);
    for (int i=0; i<100; i++) {
      times[(i + N - 1) % N] = 0;
      times[i % N] = longDelay;

      AtomicInteger executed = new AtomicInteger();
      DaemonProgressIndicator progress = new DaemonProgressIndicator();
      UsefulTestCase.assertThrows(MyException.class, "myMsg", () -> {
        TestTimeOut deadline = setTimeout(2, TimeUnit.SECONDS);
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Arrays.asList(times.clone()), progress, time -> {
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
      });
    }
  }

  public void testJobWaitForTerminationAfterCancelInTheMiddleOfTheExecutionWaitsUntilFinished2Stress() {
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
