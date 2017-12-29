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
package com.intellij.openapi.application.impl;

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("StatementWithEmptyBody")
@JITSensitive
public class ApplicationImplTest extends LightPlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    exception = null;
    timeOut = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
  }

  @Override
  protected void tearDown() throws Exception {
    readThreads = null;
    exception = null;
    super.tearDown();
  }

  private volatile Throwable exception;
  public void testAcquireReadActionLockVsRunReadActionPerformance() throws Throwable {
    final int N = 100_000_000;
    final Application application = ApplicationManager.getApplication();
    String err = null;

    for (int i=0; i<4; i++) {
      Callable<String> runnable = () -> {
        try {
          assertFalse(application.isReadAccessAllowed());
          CpuUsageData dataAcq = CpuUsageData.measureCpuUsage(() -> {
            for (int i1 = 0; i1 < N; i1++) {
              AccessToken token = application.acquireReadActionLock();
              //noinspection EmptyTryBlock
              try {
                // do it
              }
              finally {
                token.finish();
              }
            }
          });
          CpuUsageData dataRun = CpuUsageData.measureCpuUsage(() -> {
            for (int i1 = 0; i1 < N; i1++) {
              application.runReadAction(() -> {
              });
            }
          });
          long l1 = dataRun.durationMs;
          long l2 = dataAcq.durationMs;

          assertFalse(application.isReadAccessAllowed());
          int ratioPercent = (int)((l1 - l2) * 100.0 / l1);
          String msg = "acquireReadActionLock(" + l2 + "ms) vs runReadAction(" + l1 + "ms). Ratio: " + ratioPercent + "% (in "+(ratioPercent<0 ? "my" : "Maxim's") +" favor)";
          LOG.debug(msg + "\nAcquire:\n" + dataAcq.getSummary(" ") + "\nRun:\n" + dataRun.getSummary(" "));
          if (Math.abs(ratioPercent) > 40) {
            return "Suspiciously different times for " + msg;
          }
        }
        catch (Throwable e) {
          exception = e;
        }
        return null;
      };

      err = application.executeOnPooledThread(runnable).get();
      if (err == null) break;
      System.err.println("Still trying, attempt "+i+": "+err);
      System.gc();
    }

    assertNull(err);
    if (exception != null) throw exception;
  }


  public void testRead50Write50LockPerformance() {
    final int readIterations = 600_000;
    final int writeIterations = 600_000;

    runReadWrites(readIterations, writeIterations, 2000);
  }

  public void testRead100Write0LockPerformance() {
    final int readIterations = 60_000_000;
    final int writeIterations = 0;

    runReadWrites(readIterations, writeIterations, 10_000);
  }

  private static void runReadWrites(final int readIterations, final int writeIterations, int expectedMs) {
    final ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    Disposable disposable = Disposer.newDisposable();
    application.disableEventsUntil(disposable);

    try {
      PlatformTestUtil.startPerformanceTest("lock/unlock", expectedMs, () -> {
        final int numOfThreads = JobSchedulerImpl.getJobPoolParallelism();
        List<Thread> threads = new ArrayList<>(numOfThreads);
        for (int i = 0; i < numOfThreads; i++) {
          Thread thread = new Thread(() -> {
            assertFalse(application.isReadAccessAllowed());
            //System.out.println("start "+Thread.currentThread());
            for (int i1 = 0; i1 < readIterations; i1++) {
              application.runReadAction(() -> {
              });
            }
            //System.out.println("end   "+Thread.currentThread());
          }, "read thread " + i);
          thread.start();
          threads.add(thread);
        }

        if (writeIterations > 0) {
          //System.out.println("write start");
          for (int i = 0; i < writeIterations; i++) {
            ApplicationManager.getApplication().runWriteAction(() -> {
            });
          }
          //System.out.println("write end");
        }
        ConcurrencyUtil.joinAll(threads);
        threads.clear();
      }).assertTiming();
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  private long timeOut;
  private boolean ok() throws Throwable {
    if (exception != null) throw exception;
    if (System.currentTimeMillis() > timeOut) throw new RuntimeException("timeout");
    return true;
  }

  public void testAppLockReadWritePreference() throws Throwable {
    // take read lock1.
    // try to take write lock - must wait (because of taken read lock)
    // try to take read lock2 - must wait (because of write preference - write lock is pending)
    // release read lock1 - write lock must be taken first
    // release write - read lock2 must be taken
    LOG.debug("-----");
    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    AtomicBoolean holdRead1 = new AtomicBoolean(true);
    AtomicBoolean holdWrite = new AtomicBoolean(true);
    AtomicBoolean read1Acquired = new AtomicBoolean(false);
    AtomicBoolean read1Released = new AtomicBoolean(false);
    AtomicBoolean read2Acquired = new AtomicBoolean(false);
    AtomicBoolean read2Released = new AtomicBoolean(false);
    AtomicBoolean writeAcquired = new AtomicBoolean(false);
    AtomicBoolean writeReleased = new AtomicBoolean(false);
    Thread readAction1 = new Thread(() -> {
      try {
        assertFalse(ApplicationManager.getApplication().isDispatchThread());
        AccessToken stamp = application.acquireReadActionLock();
        try {
          LOG.debug("read lock1 acquired");
          read1Acquired.set(true);
          while (holdRead1.get() && ok());
        }
        finally {
          read1Released.set(true);
          stamp.finish();
          LOG.debug("read lock1 released");
        }
      }
      catch (Throwable e) {
        exception = e;
        throw new RuntimeException(e);
      }
    }, "read lock1");
    readAction1.start();

    while (!read1Acquired.get() && ok());
    AtomicBoolean aboutToAcquireWrite = new AtomicBoolean();
    // readActions2 should try to acquire read action when write action is pending
    Thread readActions2 = new Thread(() -> {
      try {
        assertFalse(ApplicationManager.getApplication().isDispatchThread());
        while (!aboutToAcquireWrite.get() && ok());
        TimeoutUtil.sleep(1000); // make sure it called writelock
        AccessToken stamp = application.acquireReadActionLock();
        try {
          LOG.debug("read lock2 acquired");
          read2Acquired.set(true);
        }
        finally {
          read2Released.set(true);
          stamp.finish();
          LOG.debug("read lock2 released");
        }
      }
      catch (Throwable e) {
        exception = e;
        throw new RuntimeException(e);
      }
    }, "read lock2");
    readActions2.start();

    Thread checkThread = new Thread(()->{
      try {
        assertFalse(ApplicationManager.getApplication().isDispatchThread());
        while (!aboutToAcquireWrite.get() && ok());
        while (!read1Acquired.get() && ok());
        TimeoutUtil.sleep(1000); // make sure it called writelock

        long timeout = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < timeout && ok()) {
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertFalse(read1Released.get());
          assertFalse(read2Acquired.get());
          assertFalse(read2Released.get());
          assertFalse(writeAcquired.get());
          assertFalse(writeReleased.get());

          assertFalse(application.tryRunReadAction(EmptyRunnable.getInstance())); // write pending
          assertFalse(application.isWriteActionInProgress());
          assertFalse(application.isWriteAccessAllowed());
          assertTrue(application.isWriteActionPending());
        }

        holdRead1.set(false);
        while (!writeAcquired.get() && ok());

        timeout = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < timeout && ok()) {
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertTrue(read1Released.get());
          assertFalse(read2Acquired.get());
          assertFalse(read2Released.get());
          assertTrue(writeAcquired.get());
          assertFalse(writeReleased.get());

          assertFalse(application.tryRunReadAction(EmptyRunnable.getInstance()));
          assertTrue(application.isWriteActionInProgress());
          assertFalse(application.isWriteAccessAllowed());
          assertFalse(application.isWriteActionPending());
        }

        holdWrite.set(false);

        while (!read2Released.get() && ok());

        timeout = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < timeout && ok()) {
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertTrue(read1Released.get());
          assertTrue(read2Acquired.get());
          assertTrue(read2Released.get());
          assertTrue(writeAcquired.get());
          assertTrue(writeReleased.get());

          assertFalse(application.isWriteActionInProgress());
          assertFalse(application.isWriteAccessAllowed());
          assertFalse(application.isWriteActionPending());
        }
      }
      catch (Throwable e) {
        exception = e;
        throw new RuntimeException(e);
      }
    }, "check");
    checkThread.start();

    aboutToAcquireWrite.set(true);
    AccessToken stamp = application.acquireWriteActionLock(getClass());
    try {
      LOG.debug("write lock acquired");
      writeAcquired.set(true);

      while (holdWrite.get() && ok()) {
        assertTrue(application.isWriteActionInProgress());
        assertTrue(application.isWriteAccessAllowed());
        assertFalse(application.isWriteActionPending());
      }
    }
    finally {
      writeReleased.set(true);
      stamp.finish();
      LOG.debug("write lock released");
    }

    readAction1.join();
    readActions2.join();
    checkThread.join();
    if (exception != null) throw exception;
  }

  private volatile boolean tryingToStartWriteAction;
  private volatile boolean readStarted;
  private volatile List<Thread> readThreads;
  @SuppressWarnings("StringConcatenationInsideStringBufferAppend") // to prevent tearing
  public void testReadWontStartWhenWriteIsPending() throws Throwable {
    int N = 5;
    final AtomicBoolean[] anotherThreadStarted = new AtomicBoolean[N];
    final AtomicBoolean[] anotherReadActionStarted = new AtomicBoolean[N];
    for (int i = 0; i < anotherReadActionStarted.length; i++) {
      anotherReadActionStarted[i] = new AtomicBoolean();
      anotherThreadStarted[i] = new AtomicBoolean();
    }
    final StringBuffer LOG = new StringBuffer();
    Thread main = new Thread(() -> {
      try {
        ApplicationManager.getApplication().runReadAction((ThrowableComputable<Object, Throwable>)() -> {
          LOG.append("inside read action\n");
          readStarted = true;
          while (!tryingToStartWriteAction && ok()) ;
          TimeoutUtil.sleep(100);

          readThreads = ContainerUtil.map(anotherReadActionStarted, readActionStarted -> new Thread(() -> {
            int finalI = ArrayUtil.indexOf(anotherReadActionStarted, readActionStarted);
            LOG.append("\nanother thread started " + finalI);
            anotherThreadStarted[finalI].set(true);
            ApplicationManager.getApplication().runReadAction(() -> {
              LOG.append("\ninside another thread read action " + finalI);
              readActionStarted.set(true);
              try {
                Thread.sleep(100);
              }
              catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              readActionStarted.set(false);
              LOG.append("\nfinished another thread read action " + finalI);
            });
            LOG.append("\nanother thread finished " + finalI);
          }, "another read action"));

          readThreads.forEach(Thread::start);

          for (AtomicBoolean threadStarted : anotherThreadStarted) {
            while (!threadStarted.get() && ok()) ;
          }
          // now the other threads try to get read lock. we should not let them
          for (int i = 0; i < 10; i++) {
            for (AtomicBoolean readStarted1 : anotherReadActionStarted) {
              assertThat(!readStarted1.get(), "must not start another read action while write is pending");
            }
            TimeoutUtil.sleep(20);
          }
          LOG.append("\nfinished read action");

          return null;
        });
      }
      catch (Throwable e) {
        exception = e;
      }
    }, "read");
    main.start();


    while (!readStarted && ok());
    tryingToStartWriteAction = true;
    LOG.append("\nwrite about to start");
    ApplicationManager.getApplication().runWriteAction(() -> {
      LOG.append("\ninside write action");
      for (AtomicBoolean readStarted1 : anotherReadActionStarted) {
        assertThat(!readStarted1.get(), "must not start another read action while write is running");
      }
      LOG.append("\nfinished write action");
    });
    main.join();
    ConcurrencyUtil.joinAll(readThreads);

    if (exception != null) {
      System.err.println(LOG);
      throw exception;
    }
  }

  private void assertThat(boolean condition, String msg) {
    if (!condition) {
      exception = new Exception(msg);
      throw new RuntimeException(msg);
    }
  }

  public void testProgressVsReadAction() throws Throwable {
    ProgressManager.getInstance().runProcessWithProgressSynchronously((ThrowableComputable<Void, Exception>)() -> {
      try {
        assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
        assertFalse(ApplicationManager.getApplication().isDispatchThread());
        for (int i=0; i<100;i++) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> TimeoutUtil.sleep(20)));
          ApplicationManager.getApplication().runReadAction(() -> TimeoutUtil.sleep(20));
        }
      }
      catch (Exception e) {
        exception = e;
      }
      return null;
    }, "Cc", false, getProject());
    UIUtil.dispatchAllInvocationEvents();
    if (exception != null) throw exception;
  }

  public void testAsyncProgressVsReadAction() throws Throwable {
    Future<?> future = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
      new Task.Backgroundable(getProject(), "Xx") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
            assertFalse(ApplicationManager.getApplication().isDispatchThread());
          }
          catch (Exception e) {
            exception = e;
          }
        }
      });
    future.get();
    if (exception != null) throw exception;
  }

  public void testWriteActionIsAllowedFromEDTOnly() throws InterruptedException {
    Thread thread = new Thread("test") {
      @Override
      public void run() {
        try {
          ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance());
        }
        catch (Throwable e) {
          exception = e;
        }
      }
    };
    thread.start();
    thread.join();
    assertNotNull(exception);
  }

  public void testRunProcessWithProgressSynchronouslyInReadAction() throws Throwable {
    boolean result = ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronouslyInReadAction(getProject(), "title", true, "cancel", null, () -> {
        try {
          assertFalse(SwingUtilities.isEventDispatchThread());
          assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
        }
        catch (Throwable e) {
          exception = e;
        }
      });
    assertTrue(result);
    if (exception != null) throw exception;
  }

  public void testRunProcessWithProgressSynchronouslyInReadActionWithPendingWriteAction() throws Throwable {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance()));
    boolean result = ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronouslyInReadAction(getProject(), "title", true, "cancel", null, () -> TimeoutUtil.sleep(10_000));
    assertTrue(result);
    UIUtil.dispatchAllInvocationEvents();
    if (exception != null) throw exception;
  }

  public void testRWLockPerformance() {
    long s = System.currentTimeMillis();
    while (System.currentTimeMillis() < s + 2000) {
      UIUtil.dispatchAllInvocationEvents();
    }
    //System.out.println("warming finished");
    final int readIterations = 100_000_000;
    PlatformTestUtil.startPerformanceTest("RWLock/unlock", 1500, ()-> {
      ReadMostlyRWLock lock = new ReadMostlyRWLock(Thread.currentThread());

      final int numOfThreads = JobSchedulerImpl.getJobPoolParallelism();
      List<Thread> threads = new ArrayList<>(numOfThreads);
      for (int i = 0; i < numOfThreads; i++) {
        @SuppressWarnings("Convert2Lambda") // runnable is more debuggable
        Thread thread = new Thread(new Runnable() {
          @Override
          public void run() {
            for (int r = 0; r < readIterations; r++) {
              try {
                lock.readLock();
              }
              finally {
                lock.readUnlock();
              }
            }
          }
        }, "read thread " + i);
        thread.start();
        threads.add(thread);
      }
      ConcurrencyUtil.joinAll(threads);
    }).usesAllCPUCores().assertTiming();
  }

  public void testCheckCanceledReadAction() throws Exception {
    Semaphore mayStartReadAction = new Semaphore();
    mayStartReadAction.down();

    ProgressIndicatorBase progress = new ProgressIndicatorBase();
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(() -> {
      mayStartReadAction.waitFor();
      ReadAction.run(() -> fail("should be canceled before entering read action"));
    }, progress));

    WriteAction.run(() -> {
      mayStartReadAction.up();
      progress.cancel();
      future.get(1, TimeUnit.SECONDS);
    });
  }

  private static void safeWrite(Runnable r) {
    ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(r::run));
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testSuspendWriteActionDelaysForeignReadActions() {
    Semaphore mayStartForeignRead = new Semaphore();
    mayStartForeignRead.down();

    List<Future> futures = new ArrayList<>();

    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    List<String> log = Collections.synchronizedList(new ArrayList<>());
    futures.add(app.executeOnPooledThread(() -> {
      assertTrue(mayStartForeignRead.waitFor(1000));
      ReadAction.run(() -> log.add("foreign read"));
    }));

    safeWrite(() -> {
      log.add("write started");
      app.executeSuspendingWriteAction(ourProject, "", () -> {
        app.invokeAndWait(() ->
          futures.add(app.executeOnPooledThread(() -> ReadAction.run(() -> log.add("foreign read")))));

        mayStartForeignRead.up();
        TimeoutUtil.sleep(50);

        ReadAction.run(() -> log.add("progress read"));
        app.invokeAndWait(() -> WriteAction.run(() -> log.add("nested write")));
        waitForFuture(app.executeOnPooledThread(() -> ReadAction.run(() -> log.add("forked read"))));
      });
      log.add("write finished");
    });

    futures.forEach(ApplicationImplTest::waitForFuture);
    assertOrderedEquals(log, "write started", "progress read", "nested write", "forked read", "write finished", "foreign read", "foreign read");
  }

  private static void waitForFuture(Future<?> future) {
    try {
      future.get(10_000, TimeUnit.MILLISECONDS);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void testHasWriteActionWorksInOtherThreads() {
    Class<?> actionClass = WriteAction.class;

    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    assertFalse(app.hasWriteAction(actionClass));
    safeWrite(() -> {
      assertTrue(app.hasWriteAction(actionClass));
      app.executeSuspendingWriteAction(ourProject, "", () -> ReadAction.run(() -> {
        assertTrue(app.hasWriteAction(actionClass));
        waitForFuture(app.executeOnPooledThread(() -> ReadAction.run(() -> assertTrue(app.hasWriteAction(actionClass)))));
      }));
    });
  }

  public void testPooledThreadsThatHappenInSuspendedWriteActionStayInSuspendedWriteAction() {
    LoggedErrorProcessor.getInstance().disableStderrDumping(getTestRootDisposable());

    Ref<Future> future = Ref.create();
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    safeWrite(() -> {
      try {
        Semaphore started = new Semaphore();
        started.down();
        app.executeSuspendingWriteAction(ourProject, "", () -> {
          future.set(app.executeOnPooledThread(() -> {
            started.up();
            TimeoutUtil.sleep(1000);
          }));
          assertTrue(started.waitFor(1000));
        });
        fail("should not allow pooled thread to stay there");
      }
      catch (AssertionError e) {
        assertTrue(ExceptionUtil.getThrowableText(e), isEscapingThreadAssertion(e));
      }
    });
    waitForFuture(future.get());
  }

  public void testPooledThreadsStartedAfterQuickSuspendedWriteActionDontGetReadPrivileges() {
    for (int i = 0; i < 1000; i++) {
      safeWrite(ApplicationImplTest::checkPooledThreadsDontGetWrongPrivileges);
    }
  }

  private static void checkPooledThreadsDontGetWrongPrivileges() {
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    Ref<Future> future = Ref.create();

    Disposable disableStderrDumping = Disposer.newDisposable();
    LoggedErrorProcessor.getInstance().disableStderrDumping(disableStderrDumping);

    Semaphore mayFinish = new Semaphore();
    mayFinish.down();
    try {
      app.executeSuspendingWriteAction(ourProject, "", () ->
        future.set(app.executeOnPooledThread(
          () -> assertTrue(mayFinish.waitFor(1000)))));
    }
    catch (AssertionError e) {
      if (!isEscapingThreadAssertion(e)) {
        e.printStackTrace();
        throw e;
      }
    }
    finally {
      Disposer.dispose(disableStderrDumping);
    }

    app.executeSuspendingWriteAction(ourProject, "", () -> {});
    mayFinish.up();
    waitForFuture(future.get());
  }

  private static boolean isEscapingThreadAssertion(AssertionError e) {
    return e.getMessage().contains("should have been terminated");
  }

  public void testReadActionInImpatientModeShouldThrowWhenThereIsAPendingWrite() throws ExecutionException, InterruptedException {
    AtomicBoolean stopRead = new AtomicBoolean();
    AtomicBoolean readAcquired = new AtomicBoolean();
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    Future<?> readAction1 = app.executeOnPooledThread(() ->
      app.runReadAction(() -> {
        readAcquired.set(true);
        try {
          while (!stopRead.get()) ;
        }
        finally {
          readAcquired.set(false);
        }
      })
    );
    while (!readAcquired.get());
    Future<?> readAction2 = app.executeOnPooledThread(() -> {
      // wait for write action attempt to start - i.e. app.myLock.writeLock() started to execute
      while (!app.myLock.writeRequested);
      app.executeByImpatientReader(() -> {
        try {
          app.runReadAction(EmptyRunnable.getInstance());
          fail("Must have been failed");
        }
        catch (ApplicationUtil.CannotRunReadActionException ignored) {

        }
        finally {
          stopRead.set(true);
        }
      });
    });

    app.runWriteAction(EmptyRunnable.getInstance());

    readAction2.get();
    readAction1.get();
  }

  public void testReadActionInImpatientModeMustNotThrowWhenThereIsAPendingWriteAndWeAreUnderNonCancelableSection() throws Exception {
    AtomicBoolean stopRead = new AtomicBoolean();
    AtomicBoolean readAcquired = new AtomicBoolean();
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    Future<?> readAction1 = app.executeOnPooledThread(() ->
      app.runReadAction(() -> {
        readAcquired.set(true);
        try {
          while (!stopRead.get()) ;
        }
        finally {
          readAcquired.set(false);
        }
      })
    );
    while (!readAcquired.get());

    AtomicBoolean executingImpatientReader = new AtomicBoolean();

    Future<?> readAction2 = app.executeOnPooledThread(() -> {
      // wait for write action attempt to start
      while (!app.isWriteActionPending());
      ProgressManager.getInstance().executeNonCancelableSection(()-> app.executeByImpatientReader(() -> {
        executingImpatientReader.set(true);
        app.runReadAction(EmptyRunnable.getInstance());
        // must not throw
      }));
    });

    Future<?> readAction1Canceler = app.executeOnPooledThread(() -> {
      while (!executingImpatientReader.get());
      TimeoutUtil.sleep(300); // make sure readAction2 does call runReadAction()
      stopRead.set(true);
    });
    app.runWriteAction(EmptyRunnable.getInstance());

    readAction1Canceler.get();
    readAction2.get();
    readAction1.get();
  }
}
