// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.diagnostic.ThreadDumper;
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
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.TestTimeOut.setTimeout;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;

@RunFirst
public class ApplicationImplTest extends LightPlatformTestCase {
  private TestTimeOut t;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    exception = null;
    t = setTimeout(2, TimeUnit.MINUTES);
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
    runReadWrites(600_000, 600_000, 2000);
  }

  public void testRead100Write0LockPerformance() {
    runReadWrites(60_000_000, 0, 10_000);
  }

  private static void runReadWrites(final int readIterations, final int writeIterations, int expectedMs) {
    NonBlockingReadActionImpl.cancelAllTasks(); // someone might've submitted a task depending on app events which we disable now
    final ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    Disposable disposable = Disposer.newDisposable();
    application.disableEventsUntil(disposable);
    application.assertIsDispatchThread();

    try {
      PlatformTestUtil.startPerformanceTest("lock/unlock", expectedMs, () -> {
        final int numOfThreads = JobSchedulerImpl.getJobPoolParallelism();
        List<Job<Void>> threads = new ArrayList<>(numOfThreads);
        for (int i = 0; i < numOfThreads; i++) {
          Job<Void> thread = JobLauncher.getInstance().submitToJobThread(() -> {
            assertFalse(application.isReadAccessAllowed());
            for (int i1 = 0; i1 < readIterations; i1++) {
              application.runReadAction(() -> {
              });
            }
          }, null);
          threads.add(thread);
        }

        if (writeIterations > 0) {
          for (int i = 0; i < writeIterations; i++) {
            ApplicationManager.getApplication().runWriteAction(() -> {
            });
          }
        }
        waitWithTimeout(threads);
      }).assertTiming();
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  private static void joinWithTimeout(List<? extends Future<?>> threads) throws TimeoutException {
    for (Future<?> thread : threads) {
      try {
        thread.get(20, TimeUnit.SECONDS);
      }
      catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (!thread.isDone()) {
        System.err.println(thread + " is still running. threaddump:\n" + ThreadDumper.dumpThreadsToString());
        throw new TimeoutException();
      }
    }
  }
  private static void waitWithTimeout(List<? extends Job<?>> threads) throws TimeoutException {
    for (Job<?> thread : threads) {
      try {
        thread.waitForCompletion(20_000);
      }
      catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (!thread.isDone()) {
        System.err.println(thread + " is still running. threaddump:\n" + ThreadDumper.dumpThreadsToString());
        throw new TimeoutException();
      }
    }
  }

  private void checkTimeout() throws Throwable {
    if (exception != null) throw exception;
    if (t.timedOut()) throw new TimeoutException("timeout");
  }

  public void testAppLockReadWritePreference() throws Throwable {
    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    assertFalse(application.isWriteAccessAllowed());
    assertFalse(application.isWriteActionPending());

    // take read lock1.
    // try to take write lock - must wait (because of taken read lock)
    // try to take read lock2 - must wait (because of write preference - write lock is pending)
    // release read lock1 - write lock must be taken first
    // release write - read lock2 must be taken
    LOG.debug("-----");
    AtomicBoolean holdRead1 = new AtomicBoolean(true);
    AtomicBoolean holdWrite = new AtomicBoolean(true);
    AtomicBoolean read1Acquired = new AtomicBoolean(false);
    AtomicBoolean read1Released = new AtomicBoolean(false);
    AtomicBoolean read2Acquired = new AtomicBoolean(false);
    AtomicBoolean read2Released = new AtomicBoolean(false);
    AtomicBoolean writeAcquired = new AtomicBoolean(false);
    AtomicBoolean writeReleased = new AtomicBoolean(false);
    Future<?> readAction1 = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        assertFalse(application.isDispatchThread());
        AccessToken stamp = application.acquireReadActionLock();
        try {
          LOG.debug("read lock1 acquired");
          read1Acquired.set(true);
          while (holdRead1.get()) checkTimeout();
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
    });

    while (!read1Acquired.get()) checkTimeout();
    AtomicBoolean aboutToAcquireWrite = new AtomicBoolean();
    // readActions2 should try to acquire read action when write action is pending
    Future<?> readActions2 = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        assertFalse(application.isDispatchThread());
        while (!aboutToAcquireWrite.get()) checkTimeout();
        // make sure EDT called writelock
        while (!application.myLock.writeRequested) checkTimeout();
        assertTrue(application.isWriteActionPending());
        //assertFalse(application.tryRunReadAction(EmptyRunnable.getInstance()));
        AccessToken stamp = application.acquireReadActionLock();
        assertFalse(application.isWriteActionPending());
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
    });

    Future<?> checkThread = ApplicationManager.getApplication().executeOnPooledThread(()->{
      try {
        assertFalse(application.isDispatchThread());
        while (!aboutToAcquireWrite.get()) checkTimeout();
        while (!read1Acquired.get()) checkTimeout();
        // make sure EDT called writelock
        while (!application.myLock.writeRequested) checkTimeout();

        TestTimeOut c = setTimeout(2, TimeUnit.SECONDS);
        while (!c.timedOut()) {
          checkTimeout();
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
        while (!writeAcquired.get()) checkTimeout();

        c = setTimeout(2, TimeUnit.SECONDS);
        while (!c.timedOut()) {
          checkTimeout();
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

        while (!read2Released.get()) checkTimeout();

        c = setTimeout(2, TimeUnit.SECONDS);
        while (!c.timedOut()) {
          checkTimeout();
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
    });

    aboutToAcquireWrite.set(true);
    AccessToken stamp = application.acquireWriteActionLock(getClass());
    try {
      LOG.debug("write lock acquired");
      writeAcquired.set(true);

      while (holdWrite.get()) {
        checkTimeout();
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

    joinWithTimeout(Arrays.asList(readAction1, readActions2, checkThread));
    if (exception != null) throw exception;
  }

  private volatile boolean tryingToStartWriteAction;
  private volatile boolean readStarted;
  private volatile List<Future<?>> readThreads;
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
    Future<?> main = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        ApplicationManager.getApplication().runReadAction((ThrowableComputable<Object, Throwable>)() -> {
          LOG.append("inside read action\n");
          readStarted = true;
          while (!tryingToStartWriteAction) checkTimeout();
          TimeoutUtil.sleep(100);

          readThreads = ContainerUtil.map(anotherReadActionStarted, readActionStarted -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
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
          }));

          for (AtomicBoolean threadStarted : anotherThreadStarted) {
            while (!threadStarted.get()) checkTimeout();
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
    });


    while (!readStarted) checkTimeout();
    tryingToStartWriteAction = true;
    LOG.append("\nwrite about to start");
    ApplicationManager.getApplication().runWriteAction(() -> {
      LOG.append("\ninside write action");
      for (AtomicBoolean readStarted1 : anotherReadActionStarted) {
        assertThat(!readStarted1.get(), "must not start another read action while write is running");
      }
      LOG.append("\nfinished write action");
    });
    joinWithTimeout(ContainerUtil.concat(Collections.singletonList(main), readThreads));

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

  public void testWriteActionIsAllowedFromEDTOnly() throws TimeoutException {
    Future<?> thread = ApplicationManager.getApplication().executeOnPooledThread(()-> {
        try {
          ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance());
        }
        catch (Throwable e) {
          exception = e;
        }
    });
    joinWithTimeout(Collections.singletonList(thread));
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
    SwingUtilities.invokeLater(() -> ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance()));
    AtomicBoolean ran = new AtomicBoolean();
    boolean result = ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronouslyInReadAction(getProject(), "title", true, "cancel", null,
                                                       () -> ran.set(true));
    assertTrue(result);
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(ran.get());
    if (exception != null) throw exception;
  }

  public void testRWLockPerformance() {
    TestTimeOut p = setTimeout(2, TimeUnit.SECONDS);
    while (!p.timedOut()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    int readIterations = 200_000_000;
    ReadMostlyRWLock lock = new ReadMostlyRWLock(Thread.currentThread());
    final int numOfThreads = JobSchedulerImpl.getJobPoolParallelism();
    final Field myThreadLocalsField = ObjectUtils.notNull(ReflectionUtil.getDeclaredField(Thread.class, "threadLocals"));
    //noinspection Convert2Lambda
    List<Callable<Void>> callables = Collections.nCopies(numOfThreads, new Callable<Void>() {
      @Override
      public Void call() {
        // It's critical there are no collisions in the thread-local map
        ReflectionUtil.resetField(Thread.currentThread(), myThreadLocalsField);
        for (int r = 0; r < readIterations; r++) {
          try {
            lock.readLock();
          }
          finally {
            lock.readUnlock();
          }
        }
        return null;
      }
    });

    PlatformTestUtil.startPerformanceTest("RWLock/unlock", 27_000, ()-> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed());
      List<Future<Void>> futures = AppExecutorUtil.getAppExecutorService().invokeAll(callables);
      ConcurrencyUtil.getAll(futures);
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

  private static void safeWrite(ThrowableRunnable<RuntimeException> r) throws Throwable {
    Ref<Throwable> e = new Ref<>();
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        WriteAction.run(r);
      }
      catch (Throwable e1) {
        e.set(e1);
      }
    });
    UIUtil.dispatchAllInvocationEvents();
    if (e.get() != null) throw e.get();
  }

  public void testSuspendWriteActionDelaysForeignReadActions() throws Throwable {
    Semaphore mayStartForeignRead = new Semaphore();
    mayStartForeignRead.down();

    List<Future<?>> futures = new ArrayList<>();

    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    List<String> log = Collections.synchronizedList(new ArrayList<>());
    futures.add(app.executeOnPooledThread(() -> {
      assertTrue(mayStartForeignRead.waitFor(1000));
      ReadAction.run(() -> log.add("foreign read"));
    }));

    safeWrite(() -> {
      log.add("write started");
      app.executeSuspendingWriteAction(getProject(), "", () -> {
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

  public void testHasWriteActionWorksInOtherThreads() throws Throwable {
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    ThrowableRunnable<RuntimeException> runnable = new ThrowableRunnable<RuntimeException>() {
      @Override
      public void run() throws RuntimeException {
        Class<? extends ThrowableRunnable<RuntimeException>> actionClass = getClass();
        assertTrue(app.hasWriteAction(actionClass));
        app.executeSuspendingWriteAction(getProject(), "", () -> ReadAction.run(() -> {
          assertTrue(app.hasWriteAction(actionClass));
          waitForFuture(app.executeOnPooledThread(() -> ReadAction.run(() -> assertTrue(app.hasWriteAction(actionClass)))));
        }));
      }
    };

    assertFalse(app.hasWriteAction(runnable.getClass()));
    safeWrite(runnable);
  }

  public void testPooledThreadsThatHappenInSuspendedWriteActionStayInSuspendedWriteAction() throws Throwable {
    LoggedErrorProcessor.getInstance().disableStderrDumping(getTestRootDisposable());

    Ref<Future<?>> future = Ref.create();
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    safeWrite(() -> {
      try {
        Semaphore started = new Semaphore();
        started.down();
        app.executeSuspendingWriteAction(getProject(), "", () -> {
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

  public void testPooledThreadsStartedAfterQuickSuspendedWriteActionDontGetReadPrivileges() throws Throwable {
    for (int i = 0; i < 1000; i++) {
      safeWrite(this::checkPooledThreadsDontGetWrongPrivileges);
    }
  }

  private void checkPooledThreadsDontGetWrongPrivileges() {
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    Ref<Future<?>> future = Ref.create();

    Disposable disableStderrDumping = Disposer.newDisposable();
    LoggedErrorProcessor.getInstance().disableStderrDumping(disableStderrDumping);

    Semaphore mayFinish = new Semaphore();
    mayFinish.down();
    try {
      app.executeSuspendingWriteAction(getProject(), "", () ->
        future.set(app.executeOnPooledThread(
          () -> assertTrue(mayFinish.waitFor(5_000)))));
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

    app.executeSuspendingWriteAction(getProject(), "", () -> {});
    mayFinish.up();
    waitForFuture(future.get());
  }

  private static boolean isEscapingThreadAssertion(AssertionError e) {
    return e.getMessage().contains("should have been terminated");
  }

  public void testReadActionInImpatientModeShouldThrowWhenThereIsAPendingWrite() throws Throwable {
    AtomicBoolean stopRead = new AtomicBoolean();
    AtomicBoolean readAcquired = new AtomicBoolean();
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    Future<?> readAction1 = app.executeOnPooledThread(() ->
      app.runReadAction(() -> {
        readAcquired.set(true);
        try {
          while (!stopRead.get()) checkTimeout();
        }
        catch (Throwable e) {
          exception = e;
        }
        finally {
          readAcquired.set(false);
        }
      })
    );
    while (!readAcquired.get()) checkTimeout();

    AtomicBoolean writeCompleted = new AtomicBoolean();
    Future<?> readAction2 = app.executeOnPooledThread(() -> {
      try {
        // wait for write action attempt to start - i.e. app.myLock.writeLock() started to execute
        while (!app.myLock.writeRequested) checkTimeout();
        app.executeByImpatientReader(() -> {
          try {
            assertFalse(app.isReadAccessAllowed());
            app.runReadAction(EmptyRunnable.getInstance());
            assertFalse(writeCompleted.get());
            if (exception != null) throw new RuntimeException(exception);
            System.err.println(ThreadDumper.dumpThreadsToString());
            fail("Must have been failed");
          }
          catch (ApplicationUtil.CannotRunReadActionException ignored) {

          }
          catch (Throwable e) {
            exception = e;
          }
          finally {
            stopRead.set(true);
          }
        });
      }
      catch (Throwable e) {
        exception = e;
      }
    });

    app.runWriteAction(EmptyRunnable.getInstance());
    writeCompleted.set(true);

    readAction2.get();
    readAction1.get();
    if (exception != null) throw exception;
  }

  public void testReadActionInImpatientModeMustNotThrowWhenThereIsAPendingWriteAndWeAreUnderNonCancelableSection() throws Throwable {
    AtomicBoolean stopRead = new AtomicBoolean();
    AtomicBoolean readAcquired = new AtomicBoolean();
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    Future<?> readAction1 = app.executeOnPooledThread(() ->
       app.runReadAction(() -> {
         readAcquired.set(true);
         try {
           while (!stopRead.get()) checkTimeout();
         }
         catch (Throwable e) {
           exception = e;
         }
         finally {
           readAcquired.set(false);
         }
       })
    );
    while (!readAcquired.get()) checkTimeout();

    AtomicBoolean executingImpatientReader = new AtomicBoolean();

    Future<?> readAction2 = app.executeOnPooledThread(() -> {
      // wait for write action attempt to start
      while (!app.myLock.writeRequested) {
        try {
          checkTimeout();
        }
        catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
      ProgressManager.getInstance().executeNonCancelableSection(()-> app.executeByImpatientReader(() -> {
        executingImpatientReader.set(true);
        app.runReadAction(EmptyRunnable.getInstance());
        // must not throw
      }));
    });

    Future<?> readAction1Canceler = app.executeOnPooledThread(() -> {
      try {
        while (!executingImpatientReader.get()) checkTimeout();
        // make sure readAction2 does call runReadAction()
        TimeoutUtil.sleep(300);
        stopRead.set(true);
      }
      catch (Throwable e) {
        exception = e;
      }
    });
    app.runWriteAction(EmptyRunnable.getInstance());

    readAction1Canceler.get();
    readAction2.get();
    readAction1.get();
    if (exception != null) throw exception;
  }

  public void testPluginsHostProperty() {
    String host = "IntellijIdeaRulezzz";

    String oldHost = System.setProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY, host);

    try {
      ApplicationInfoImpl applicationInfo = new ApplicationInfoImpl();

      Assert.assertThat(applicationInfo.getPluginManagerUrl(), containsString(host));
      Assert.assertThat(applicationInfo.getPluginsListUrl(), containsString(host));
      Assert.assertThat(applicationInfo.getPluginsDownloadUrl(), containsString(host));
      Assert.assertThat(applicationInfo.getChannelsListUrl(), containsString(host));

      Assert.assertThat(applicationInfo.getPluginManagerUrl(), not(containsString(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST)));
      Assert.assertThat(applicationInfo.getPluginsListUrl(), not(containsString(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST)));
      Assert.assertThat(applicationInfo.getPluginsDownloadUrl(), not(containsString(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST)));
      Assert.assertThat(applicationInfo.getChannelsListUrl(), not(containsString(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST)));
    }
    finally {
      if (oldHost == null) {
        System.clearProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY);
      }
      else {
        System.setProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY, oldHost);
      }
    }
  }
}
