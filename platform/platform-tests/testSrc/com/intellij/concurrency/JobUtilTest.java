/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class JobUtilTest extends PlatformTestCase {
  private static final AtomicInteger COUNT = new AtomicInteger();

  public void testUnbalancedTaskJobUtilPerformance() {
    List<Integer> things = new ArrayList<Integer>(Collections.<Integer>nCopies(10000, null));
    int sum = 0;
    for (int i = 0; i < things.size(); i++) {
      int v = i < 9950 ? 1 : 1000;
      things.set(i, v);
      sum += things.get(i);
    }
    assertEquals(59950, sum);

    long start = System.currentTimeMillis();
    boolean b = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(things, new ProgressIndicatorBase(), false, false, new Processor<Integer>() {
      @Override
      public boolean process(Integer o) {
        busySleep(o);
        return true;
      }
    });
    assertTrue(b);
    long elapsed = System.currentTimeMillis() - start;
    int expected = 2 * (9950 + 50 * 1000) / JobSchedulerImpl.CORES_COUNT;
    String message = "Elapsed: " + elapsed + "; expected: " + expected;
    System.out.println(message);
    assertTrue(message, elapsed < expected);
  }

  private static int busySleep(int ms) {
    long end = System.currentTimeMillis() + ms;
    while (System.currentTimeMillis() < end);
    return COUNT.incrementAndGet();
  }
  private static int busySleep(int ms, Runnable whileWait) {
    long end = System.currentTimeMillis() + ms;
    while (System.currentTimeMillis() < end)  {
      whileWait.run();
    }
    return COUNT.incrementAndGet();
  }

  public void testJobUtilCorrectlySplitsUpHugeWorkAndFinishes_Performance() throws Exception {
    COUNT.set(0);
    int N = 100000;
    List<String> list = Collections.nCopies(N, null);
    final AtomicReference<Exception> exception = new AtomicReference<Exception>();
    final AtomicBoolean finished = new AtomicBoolean();

    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, new Processor<String>() {
      @Override
      public boolean process(String name) {
        try {
          if (finished.get()) {
            throw new RuntimeException();
          }
          for (int i = 0; i < 1000; i++) {
            new BigDecimal(i).multiply(new BigDecimal(1));
          }
          busySleep(1);
          if (finished.get()) {
            throw new RuntimeException();
          }
        }
        catch (Exception e) {
          exception.set(e);
        }
        return true;
      }
    });
    finished.set(true);
    Thread.sleep(1000);
    if (exception.get() != null) throw exception.get();
    assertEquals(N, COUNT.get());
  }

  public void testJobUtilProcessesAllItems_Performance() throws Exception {
    List<String> list = Collections.nCopies(10000, null);
    final AtomicReference<Exception> exception = new AtomicReference<Exception>();
    for (int i=0; i<10; i++) {
      long start = System.currentTimeMillis();
      COUNT.set(0);
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, new Processor<String>() {
        @Override
        public boolean process(String name) {
          busySleep(1);
          return true;
        }
      });
      if (exception.get() != null) throw exception.get();
      long finish = System.currentTimeMillis();
      System.out.println("Elapsed: "+(finish-start)+"ms");
      assertEquals(list.size(), COUNT.get());
    }
  }

  public void testJobUtilRecursive_Performance() throws Exception {
    final List<String> list = Collections.nCopies(100, null);
    for (int i=0; i<10; i++) {
      COUNT.set(0);
      long start = System.currentTimeMillis();
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, new Processor<String>() {
        @Override
        public boolean process(String name) {
          JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, new Processor<String>() {
            @Override
            public boolean process(String name) {
              busySleep(1);
              return true;
            }
          });
          return true;
        }
      });
      long finish = System.currentTimeMillis();
      System.out.println("Elapsed: "+(finish-start)+"ms");
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
    final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, progress, runInReadAction, new Processor<Object>() {
      @Override
      public boolean process(Object o) {
        try {
          if (objects.size() <= 1 || JobSchedulerImpl.CORES_COUNT <= JobLauncherImpl.CORES_FORK_THRESHOLD) {
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
      }
    });
    if (exception.get() != null) throw exception.get();
  }

  private static class MyException extends RuntimeException {}
  public void testExceptionalCompletion() throws Throwable {
    final List<Object> objects = Collections.nCopies(100000000, null);
    COUNT.set(0);
    try {
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, true, new Processor<Object>() {
        @Override
        public boolean process(Object o) {
          if (COUNT.incrementAndGet() == 100000) {
            System.out.println("PCE");
            throw new MyException();
          }
          return true;
        }
      });
      fail("exception must have been thrown");
    }
    catch (MyException e) {
      // caught OK
    }
  }
  public void testNotNormalCompletion() throws Throwable {
    final List<Object> objects = Collections.nCopies(100000000, null);
    COUNT.set(0);
    boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, true, new Processor<Object>() {
      @Override
      public boolean process(Object o) {
        if (COUNT.incrementAndGet() == 100000) {
          System.out.println("PCE");
          return false;
        }
        return true;
      }
    });
    assertFalse(success);
  }

  public void testJobUtilCompletesEvenIfCannotGrabReadAction() throws Throwable {
    final List<Object> objects = Collections.nCopies(1000000, null);
    COUNT.set(0);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(objects, null, true, false, new Processor<Object>() {
          @Override
          public boolean process(Object o) {
            COUNT.incrementAndGet();
            return true;
          }
        });
        assertTrue(success);
        assertEquals(objects.size(), COUNT.get());
      }
    });
  }

  public void testJobUtilRecursiveCancel() throws Exception {
    final List<String> list = Collections.nCopies(100, "");
    final List<Integer> ilist = Collections.nCopies(100, 0);
    for (int i=0; i<10; i++) {
      COUNT.set(0);
      long start = System.currentTimeMillis();
      boolean success = false;
      try {
        success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, null, false, new Processor<String>() {
          @Override
          public boolean process(String name) {
            boolean nestedSuccess = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(ilist, null, false, new Processor<Integer>() {
              @Override
              public boolean process(Integer integer) {
                if (busySleep(1) == 1000) {
                  System.out.println("PCE");
                  throw new MyException();
                }
                return true;
              }
            });
            //System.out.println("nestedSuccess = " + nestedSuccess);
            return true;
          }
        });
        fail("exception must have been thrown");
      }
      catch (MyException ignored) {
      }
      long finish = System.currentTimeMillis();
      System.out.println("Elapsed: "+(finish-start)+"ms");
      //assertEquals(list.size()*list.size(), COUNT.get());
      assertFalse(success);
    }
  }

  public void testSaturation() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    for (int i=0; i<100; i++) {
      JobLauncher.getInstance().submitToJobThread(new Runnable() {
        @Override
        public void run() {
          try {
            latch.await();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }, null);
    }
    JobLauncher.getInstance().submitToJobThread(new Runnable() {
      @Override
      public void run() {
        latch.countDown();
      }
    }, null);

    try {
      boolean scheduled = latch.await(3, TimeUnit.SECONDS);
      assertFalse(scheduled); // pool saturated, no thread can be scheduled
    }
    finally {
      latch.countDown();
    }
  }

  public void testProcessorReturningFalseDoesNotCrashTheOtherThread() {
    final AtomicInteger delay = new AtomicInteger(0);
    final Runnable checkCanceled = new Runnable() {
      @Override
      public void run() {
        ProgressManager.checkCanceled();
      }
    };
    Processor<String> processor = new Processor<String>() {
      @Override
      public boolean process(String s) {
        busySleep(delay.incrementAndGet() % 10 + 10, checkCanceled);
        return delay.get() % 100 != 0;
      }
    };
    for (int i=0; i<100; i++) {
      ProgressIndicator indicator = new EmptyProgressIndicator();
      boolean result = JobLauncher.getInstance()
        .invokeConcurrentlyUnderProgress(Collections.nCopies(10000, ""), indicator, false, false, processor);
      assertFalse(indicator.isCanceled());
      assertFalse(result);
    }
  }

  public void testTasksRunEvenWhenReadActionIsHardToGet_Performance() throws ExecutionException, InterruptedException {
    final Processor<String> processor = new Processor<String>() {
      @Override
      public boolean process(String s) {
        busySleep(1);
        return true;
      }
    };
    for (int i=0; i<10/*0*/; i++) {
      System.out.println("i = " + i);
      final ProgressIndicator indicator = new EmptyProgressIndicator();
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(10000, ""), indicator, true, false, processor);
          assertFalse(indicator.isCanceled());
        }
      });
      for (int k=0; k<10000; k++) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            busySleep(1);
          }
        });
      }
      future.get();
    }
  }

  public void testAfterCancelInTheMiddleOfTheExecutionTaskIsDoneReturnsFalseUntilFinished() throws ExecutionException, InterruptedException {
    Random random = new Random();
    for (int i=0; i<100; i++) {
      final AtomicBoolean finished = new AtomicBoolean();
      final AtomicBoolean started = new AtomicBoolean();
      Job<Void> job = JobLauncher.getInstance().submitToJobThread(new Runnable() {
        @Override
        public void run() {
          started.set(true);
          TimeoutUtil.sleep(100);
          finished.set(true);
        }
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
    for (int i=0; i<100; i++) {
      final AtomicBoolean finished = new AtomicBoolean();
      final AtomicBoolean started = new AtomicBoolean();
      Job<Void> job = JobLauncher.getInstance().submitToJobThread(new Runnable() {
        @Override
        public void run() {
          started.set(true);
          TimeoutUtil.sleep(100);
          finished.set(true);
        }
      }, null);
      assertFalse(job.isDone());
      while (!started.get());
      job.cancel();
      job.waitForCompletion(100000);
      assertTrue(finished.get());
    }
  }

  public void testDaemonDoesNotPauseWhenEventDispatcherHasEventsInTheQueue() throws Throwable {
    assertTrue(SwingUtilities.isEventDispatchThread());

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    final AtomicInteger jobsStarted = new AtomicInteger();
    final int N_EVENTS = 50;
    final int N_JOBS = 10000 * JobSchedulerImpl.CORES_COUNT;
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
        while (jobsStarted.get() < jobs0 + JobSchedulerImpl.CORES_COUNT && jobsStarted.get() < N_JOBS) {
          if (System.currentTimeMillis() > start + 10000) {
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
