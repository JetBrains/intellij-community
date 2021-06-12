// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunFirst;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunFirst
public class ApplicationImplTest extends LightPlatformTestCase {
  private TestTimeOut t;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    exception = null;
    t = TestTimeOut.setTimeout(2, TimeUnit.MINUTES);
  }

  @Override
  protected void tearDown() throws Exception {
    readThreads = null;
    exception = null;
    super.tearDown();
  }

  private volatile Throwable exception;

  public void testRead50Write50LockPerformance() {
    runReadWrites(500_000, 500_000, 2000);
  }

  public void testRead100Write0LockPerformance() {
    runReadWrites(50_000_000, 0, 10_000);
  }

  private void runReadWrites(final int readIterations, final int writeIterations, int expectedMs) {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion(); // someone might've submitted a task depending on app events which we disable now
    final ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    Disposable disposable = Disposer.newDisposable();
    application.disableEventsUntil(disposable);
    application.assertIsDispatchThread();

    try {
      PlatformTestUtil.startPerformanceTest("lock/unlock "+getTestName(false), expectedMs, () -> {
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

        for (int i = 0; i < writeIterations; i++) {
          ApplicationManager.getApplication().runWriteAction(() -> {
          });
        }
        waitWithTimeout(threads);
      }).assertTiming();
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  private static void joinWithTimeout(Future<?>... threads) throws TimeoutException {
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

    // take read lock1 in separate thread.
    // try to take write lock - must wait (because of taken read lock)
    // try to take read lock2 in separate thread - must wait (because of write preference - write lock is pending)
    // release read lock1 - write lock must be taken first
    // release write - read lock2 must be taken
    LOG.debug("-----");
    AtomicBoolean holdRead1 = new AtomicBoolean(true);
    AtomicBoolean holdWrite = new AtomicBoolean(true);
    AtomicBoolean read1Acquired = new AtomicBoolean(false);
    AtomicBoolean read1AboutToRelease = new AtomicBoolean(false);
    AtomicBoolean read2Acquired = new AtomicBoolean(false);
    AtomicBoolean read2AboutToRelease = new AtomicBoolean(false);
    AtomicBoolean writeAcquired = new AtomicBoolean(false);
    AtomicBoolean writeAboutToRelease = new AtomicBoolean(false);
    Future<?> readAction1 = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        assertFalse(application.isDispatchThread());
        application.runReadAction(() -> {
          read1Acquired.set(true);
          LOG.debug("read lock1 acquired");
          while (holdRead1.get()) {
            try {
              checkTimeout();
            }
            catch (Throwable throwable) {
              throw new RuntimeException(throwable);
            }
          }
          read1AboutToRelease.set(true);
        });
        LOG.debug("read lock1 released");
      }
      catch (Throwable e) {
        exception = e;
        throw new RuntimeException(e);
      }
    });

    while (!read1Acquired.get()) checkTimeout();
    AtomicBoolean aboutToAcquireWrite = new AtomicBoolean();
    // readAction2 should try to acquire read action when write action is pending
    Future<?> readAction2 = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        assertFalse(application.isDispatchThread());
        while (!aboutToAcquireWrite.get()) checkTimeout();
        // make sure EDT called writelock
        while (!application.myLock.writeRequested) checkTimeout();
        assertTrue(application.isWriteActionPending());
        //assertFalse(application.tryRunReadAction(EmptyRunnable.getInstance()));
        application.runReadAction(() -> {
          read2Acquired.set(true);
          assertFalse(application.isWriteActionPending());
          LOG.debug("read lock2 acquired");
          read2AboutToRelease.set(true);
        });
        LOG.debug("read lock2 released");
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

        doFor(100, TimeUnit.MILLISECONDS, ()->{
          checkTimeout();
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertFalse(read1AboutToRelease.get());
          assertFalse(read2Acquired.get());
          assertFalse(read2AboutToRelease.get());
          assertFalse(writeAcquired.get());
          assertFalse(writeAboutToRelease.get());

          assertFalse(application.tryRunReadAction(EmptyRunnable.getInstance())); // write pending
          assertFalse(application.isWriteActionInProgress());
          assertFalse(application.isWriteAccessAllowed());
          assertTrue(application.isWriteActionPending());
        });

        holdRead1.set(false);
        while (!writeAcquired.get()) checkTimeout();

        doFor(100, TimeUnit.MILLISECONDS, ()->{
          checkTimeout();
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertTrue(read1AboutToRelease.get());
          assertFalse(read2Acquired.get());
          assertFalse(read2AboutToRelease.get());
          assertTrue(writeAcquired.get());
          assertFalse(writeAboutToRelease.get());

          assertFalse(application.tryRunReadAction(EmptyRunnable.getInstance()));
          assertTrue(application.isWriteActionInProgress());
          assertFalse(application.isWriteAccessAllowed());
          assertFalse(application.isWriteActionPending());
        });

        holdWrite.set(false);

        while (!read2AboutToRelease.get()) checkTimeout();

        doFor(100, TimeUnit.MILLISECONDS, ()->{
          checkTimeout();
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertTrue(read1AboutToRelease.get());
          assertTrue(read2Acquired.get());
          assertTrue(read2AboutToRelease.get());
          assertTrue(writeAcquired.get());
          assertTrue(writeAboutToRelease.get());

          assertFalse(application.isWriteActionInProgress());
          assertFalse(application.isWriteAccessAllowed());
          assertFalse(application.isWriteActionPending());
        });
      }
      catch (Throwable e) {
        exception = e;
        throw new RuntimeException(e);
      }
    });

    aboutToAcquireWrite.set(true);
    application.runWriteAction(() -> {
      writeAcquired.set(true);
      LOG.debug("write lock acquired");

      while (holdWrite.get()) {
        try {
          checkTimeout();
        }
        catch (Throwable throwable) {
          throw new RuntimeException(throwable);
        }
        assertTrue(application.isWriteActionInProgress());
        assertTrue(application.isWriteAccessAllowed());
        assertFalse(application.isWriteActionPending());
      }
      writeAboutToRelease.set(true);
    });
    LOG.debug("write lock released");

    joinWithTimeout(readAction1, readAction2, checkThread);
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

          readThreads = new ArrayList<>();
          readThreads.addAll(ContainerUtil.map(anotherReadActionStarted, readActionStarted -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
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
          })));

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
    readThreads.add(main);
    joinWithTimeout(readThreads.toArray(new Future[0]));

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
    joinWithTimeout(thread);
    assertNotNull(exception);
  }

  public void testRunProcessWithProgressFromPooledThread() throws Throwable {
    Future<?> thread = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        boolean result = ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronously(() -> {
          // check that defaultModalityState() carries write-safe context now
          ApplicationManager.getApplication().invokeAndWait(() -> {
            ApplicationManager.getApplication().assertIsWriteThread();
            ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
          });
        }, "Title", true, getProject());
        assertTrue(result);
      }
      catch (Throwable e) {
        exception = e;
      }
    });
    pumpEventsFor(500, TimeUnit.MILLISECONDS);
    joinWithTimeout(thread);
    if (exception != null) throw exception;
  }

  public void testRWLockPerformance() throws Throwable {
    pumpEventsFor(2, TimeUnit.SECONDS);
    int readIterations = 200_000_000;
    ApplicationManager.getApplication().assertIsDispatchThread();
    ReadMostlyRWLock lock = new ReadMostlyRWLock(Thread.currentThread());
    final int numOfThreads = JobSchedulerImpl.getJobPoolParallelism();
    final Field myThreadLocalsField = Objects.requireNonNull(ReflectionUtil.getDeclaredField(Thread.class, "threadLocals"));
    //noinspection Convert2Lambda
    List<Callable<Void>> callables = Collections.nCopies(numOfThreads, new Callable<>() {
      @Override
      public Void call() {
        // It's critical there are no collisions in the thread-local map
        ReflectionUtil.resetField(Thread.currentThread(), myThreadLocalsField);
        for (int r = 0; r < readIterations; r++) {
          ReadMostlyRWLock.Reader reader = lock.startRead();
          assertNotNull(reader);
          lock.endRead(reader);
        }
        return null;
      }
    });

    PlatformTestUtil.startPerformanceTest("RWLock/unlock", 27_000, ()-> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed());
      List<Future<Void>> futures = AppExecutorUtil.getAppExecutorService().invokeAll(callables);
      ConcurrencyUtil.getAll(futures);
    }).assertTiming();
  }

  private static void pumpEventsFor(int timeOut, @NotNull TimeUnit unit) throws Throwable {
    doFor(timeOut, unit, () -> UIUtil.dispatchAllInvocationEvents());
  }

  private static void doFor(int timeOut, @NotNull TimeUnit unit, @NotNull ThrowableRunnable<?> runnable) throws Throwable {
    long due = unit.toMillis(timeOut) + System.currentTimeMillis();
    while (System.currentTimeMillis() <= due) {
      runnable.run();
    }
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
    ThrowableComputable<Void, RuntimeException> runnable = new ThrowableComputable<>() {
      @Override
      public Void compute() throws RuntimeException {
        Class<?> actionClass = getClass();
        assertTrue(app.hasWriteAction(actionClass));
        app.executeSuspendingWriteAction(getProject(), "", () -> ReadAction.run(() -> {
          assertTrue(app.hasWriteAction(actionClass));
          waitForFuture(app.executeOnPooledThread(() -> ReadAction.run(() -> assertTrue(app.hasWriteAction(actionClass)))));
        }));
        return null;
      }
    };

    assertFalse(app.hasWriteAction(runnable.getClass()));
    safeWrite(runnable);
  }
  private static <T> void safeWrite(ThrowableComputable<T, RuntimeException> r) throws Throwable {
    AtomicReference<Throwable> e = new AtomicReference<>();
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        WriteAction.compute(r);
      }
      catch (Throwable e1) {
        e.set(e1);
      }
    });
    UIUtil.dispatchAllInvocationEvents();
    if (e.get() != null) throw e.get();
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

  public void testWriteCommandActionMustThrowRelevantException() {
    assertThrows(IOException.class, () -> WriteCommandAction.runWriteCommandAction(getProject(),
                                          (ThrowableComputable<ThrowableRunnable<?>, IOException>)() -> { throw new IOException("aaaah"); }));
  }
}
