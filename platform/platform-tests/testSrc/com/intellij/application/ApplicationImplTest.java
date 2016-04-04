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
package com.intellij.application;

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

public class ApplicationImplTest extends LightPlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    exception = null;
    timeOut = System.currentTimeMillis() + 2*60*1000;
  }

  private volatile Throwable exception;
  public void testAcquireReadActionLockVsRunReadActionPerformance() throws Throwable {
    final int N = 100000000;
    final Application application = ApplicationManager.getApplication();
    String err = null;

    for (int i=0; i<4; i++) {
      Callable<String> runnable = () -> {
        try {
          assertFalse(application.isReadAccessAllowed());
          long l2 = PlatformTestUtil.measure(() -> {
            for (int i1 = 0; i1 < N; i1++) {
              AccessToken token = application.acquireReadActionLock();
              try {
                // do it
              }
              finally {
                token.finish();
              }
            }
          });

          long l1 = PlatformTestUtil.measure(() -> {
            for (int i1 = 0; i1 < N; i1++) {
              application.runReadAction(() -> {
              });
            }
          });

          assertFalse(application.isReadAccessAllowed());
          int ratioPercent = (int)((l1 - l2) * 100.0 / l1);
          String msg = "acquireReadActionLock(" + l2 + "ms) vs runReadAction(" + l1 + "ms). Ratio: " + ratioPercent + "%";
          System.out.println(msg);
          if (Math.abs(ratioPercent) > 20) {
            return "Suspiciously different times for " + msg +" (in "+(ratioPercent<0 ? "my" : "Maxim's") +" favor)";
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
    }

    assertNull(err);
    if (exception != null) throw exception;
  }


  public void testRead50Write50LockPerformance() throws InterruptedException {
    int iterations = 400000;
    final int readIterations = iterations;
    final int writeIterations = iterations;

    runReadWrites(readIterations, writeIterations, 2000);
  }

  public void testRead100Write0LockPerformance() throws InterruptedException {
    final int readIterations = 400000;
    final int writeIterations = 0;

    runReadWrites(readIterations, writeIterations, 1000);
  }

  private static void runReadWrites(final int readIterations, final int writeIterations, int expectedMs) throws InterruptedException {
    final ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    Disposable disposable = Disposer.newDisposable();
    application.disableEventsUntil(disposable);
    List<Thread> threads = new ArrayList<>();

    try {
      PlatformTestUtil.startPerformanceTest("lock performance", expectedMs, () -> {
        final int numOfThreads = JobSchedulerImpl.CORES_COUNT;
        final CountDownLatch reads = new CountDownLatch(numOfThreads);
        for (int i = 0; i < numOfThreads; i++) {
          Thread thread = new Thread(() -> {
            System.out.println(Thread.currentThread());
            for (int i1 = 0; i1 < readIterations; i1++) {
              application.runReadAction(() -> {

              });
            }

            reads.countDown();
          }, "stress thread " + i);
          thread.start();
          threads.add(thread);
        }

        if (writeIterations > 0) {
          System.out.println("write start");
          for (int i = 0; i < writeIterations; i++) {
            ApplicationManager.getApplication().runWriteAction(() -> {
            });
          }
          System.out.println("write end");
        }
        reads.await();
      }).cpuBound().assertTiming();
    }
    finally {
      Disposer.dispose(disposable);
      for (Thread thread : threads) {
        thread.join();
      }
    }
  }


  public void _testStampLockReadWritePreference() throws Throwable {
    // take read lock1.
    // try to take write lock - must wait (because of taken read lock)
    // try to take read lock2 - must wait (because of write preference - write lock is pending)
    // release read lock1 - write lock must be taken first
    // release write - read lock2 must be taken

    StampedLock lock = new StampedLock();
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
        long stamp = lock.readLock();
        try {
          System.out.println("read lock1 acquired");
          read1Acquired.set(true);
          while (holdRead1.get() && ok());
        }
        finally {
          lock.unlockRead(stamp);
          read1Released.set(true);
          System.out.println("read lock1 released");
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
        TimeoutUtil.sleep(2000); // make sure it called writelock
        long stamp = lock.readLock();
        try {
          System.out.println("read lock2 acquired");
          read2Acquired.set(true);
        }
        finally {
          lock.unlockRead(stamp);
          read2Released.set(true);
          System.out.println("read lock2 released");
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
        TimeoutUtil.sleep(2000); // make sure it called writelock

        long timeout = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < timeout && ok()) {
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertFalse(read1Released.get());
          assertFalse(read2Acquired.get());
          assertFalse(read2Released.get());
          assertFalse(writeAcquired.get());
          assertFalse(writeReleased.get());

          assertEquals(0, lock.tryReadLock());
          assertEquals(0, lock.tryWriteLock());
        }

        holdRead1.set(false);
        while (!writeAcquired.get() && ok());

        timeout = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < timeout && ok()) {
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertTrue(read1Released.get());
          assertFalse(read2Acquired.get());
          assertFalse(read2Released.get());
          assertTrue(writeAcquired.get());
          assertFalse(writeReleased.get());

          assertEquals(0, lock.tryReadLock());
        }

        holdWrite.set(false);

        while (!read2Acquired.get() && ok());
        TimeoutUtil.sleep(1000); // wait for immediate release of read lock2

        timeout = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < timeout && ok()) {
          assertTrue(aboutToAcquireWrite.get());
          assertTrue(read1Acquired.get());
          assertTrue(read1Released.get());
          assertTrue(read2Acquired.get());
          assertTrue(read2Released.get());
          assertTrue(writeAcquired.get());
          assertTrue(writeReleased.get());
        }
      }
      catch (Throwable e) {
        exception = e;
        throw new RuntimeException(e);
      }
    }, "check");
    checkThread.start();

    aboutToAcquireWrite.set(true);
    long stamp = lock.writeLock();
    try {
      System.out.println("write lock acquired");
      writeAcquired.set(true);
      while (holdWrite.get() && ok());
    }
    finally {
      lock.unlockWrite(stamp);
      writeReleased.set(true);
      System.out.println("write lock released");
    }

    readAction1.join();
    readActions2.join();
    checkThread.join();
    if (exception != null) throw exception;
  }

  private static long timeOut;
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
    System.out.println("-----");
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
          System.out.println("read lock1 acquired");
          read1Acquired.set(true);
          while (holdRead1.get() && ok());
        }
        finally {
          read1Released.set(true);
          stamp.finish();
          System.out.println("read lock1 released");
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
          System.out.println("read lock2 acquired");
          read2Acquired.set(true);
        }
        finally {
          read2Released.set(true);
          stamp.finish();
          System.out.println("read lock2 released");
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

        long timeout = System.currentTimeMillis() + 10000;
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

        timeout = System.currentTimeMillis() + 10000;
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

        timeout = System.currentTimeMillis() + 10000;
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
      System.out.println("write lock acquired");
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
      System.out.println("write lock released");
    }

    readAction1.join();
    readActions2.join();
    checkThread.join();
    if (exception != null) throw exception;
  }

  private volatile boolean tryingToStartWriteAction;
  private volatile boolean readStarted;
  private volatile List<Thread> readThreads;
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
    for (Thread thread : readThreads) {
      thread.join();
    }

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
          SwingUtilities.invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
            TimeoutUtil.sleep(20);
          }));
          ApplicationManager.getApplication().runReadAction(() -> {
            TimeoutUtil.sleep(20);
          });
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
      .runProcessWithProgressSynchronouslyInReadAction(getProject(), "title", true, "cancel", null, () -> TimeoutUtil.sleep(10000));
    assertTrue(result);
    UIUtil.dispatchAllInvocationEvents();
    if (exception != null) throw exception;
  }
}
