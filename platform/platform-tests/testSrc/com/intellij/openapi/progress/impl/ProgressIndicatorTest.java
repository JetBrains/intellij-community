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
package com.intellij.openapi.progress.impl;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.concurrency.JobScheduler;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yole
 */
public class ProgressIndicatorTest extends LightPlatformTestCase {
  public void testCheckCanceledHasStackFrame() {
    ProgressIndicator pib = new ProgressIndicatorBase();
    pib.cancel();
    try {
      pib.checkCanceled();
      fail("Please restore ProgressIndicatorBase.checkCanceled() check!");
    }
    catch(ProcessCanceledException ex) {
      boolean hasStackFrame = ex.getStackTrace().length != 0;
      assertTrue("Should have stackframe", hasStackFrame);
    }
  }

  public void testProgressManagerCheckCanceledWorksRightAfterIndicatorBeenCanceled() {
    for (int i=0; i<1000;i++) {
      final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
      ProgressManager.getInstance().runProcess(() -> {
        ProgressManager.checkCanceled();
        try {
          indicator.cancel();
          ProgressManager.checkCanceled();
          fail("checkCanceled() must have caught just canceled indicator");
        }
        catch (ProcessCanceledException ignored) {
        }
      }, indicator);
    }
  }

  private volatile long prevTime;
  private volatile long now;
  public void testCheckCanceledGranularity() {
    prevTime = now = 0;
    final long warmupEnd = System.currentTimeMillis() + 1000;
    final TLongArrayList times = new TLongArrayList();
    final long end = warmupEnd + 1000;

    ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronously(() -> {
      final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
      ProgressIndicatorEx indicator = (ProgressIndicatorEx)ProgressIndicatorProvider.getGlobalProgressIndicator();
      prevTime = System.currentTimeMillis();
      assert indicator != null;
      indicator.addStateDelegate(new ProgressIndicatorStub() {
        @Override
        public void checkCanceled() throws ProcessCanceledException {
          now = System.currentTimeMillis();
          if (now > warmupEnd) {
            int delta = (int)(now - prevTime);
            times.add(delta);
          }
          prevTime = now;
        }
      });
      while (System.currentTimeMillis() < end) {
        ProgressManager.checkCanceled();
      }
      alarm.cancelAllRequests();
    }, "", false, getProject(), null, "");
    long averageDelay = ArrayUtil.averageAmongMedians(times.toNativeArray(), 5);
    System.out.println("averageDelay = " + averageDelay);
    assertTrue(averageDelay < CoreProgressManager.CHECK_CANCELED_DELAY_MILLIS *3);
  }

  public void testProgressIndicatorUtilsScheduleWithWriteActionPriority() throws Exception {
    final AtomicBoolean insideReadAction = new AtomicBoolean();
    final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    CompletableFuture<?> future = ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, new ReadTask() {
      @Override
      public void computeInReadAction(@NotNull ProgressIndicator indicator) {
        insideReadAction.set(true);
        while (true) {
          ProgressManager.checkCanceled();
        }
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
      }
    });
    UIUtil.dispatchAllInvocationEvents();
    while (!insideReadAction.get()) {

    }
    ApplicationManager.getApplication().runWriteAction(() -> assertTrue(indicator.isCanceled()));
    assertTrue(indicator.isCanceled());
    waitForComplete(future);
  }

  public void testReadTaskCanceledShouldNotHappenAfterEdtContinuation() throws Exception {
    for (int i = 0; i < 1000; i++) {
      final AtomicBoolean afterContinuation = new AtomicBoolean();
      final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
      CompletableFuture<?> future = ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, new ReadTask() {
        @Override
        public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
          return new Continuation(() -> afterContinuation.set(true));
        }

        @Override
        public void onCanceled(@NotNull ProgressIndicator indicator) {
          assertFalse(afterContinuation.get());
        }
      });
      UIUtil.dispatchAllInvocationEvents();
      ApplicationManager.getApplication().runWriteAction(() -> {
        if (!afterContinuation.get()) {
          assertTrue(indicator.isCanceled());
        }
      });
      UIUtil.dispatchAllInvocationEvents();
      waitForComplete(future);
    }
  }

  private static void waitForComplete(CompletableFuture<?> future) throws InterruptedException, ExecutionException {
    while (true) {
      try {
        future.get(1, TimeUnit.MILLISECONDS);
        break;
      }
      catch (TimeoutException e) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
  }

  public void testThereIsNoDelayBetweenIndicatorCancelAndProgressManagerCheckCanceled() throws Throwable {
    for (int i=0; i<100;i++) {
      final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
      List<Thread> threads = ContainerUtil.map(Collections.nCopies(10, ""),
                                               s -> new Thread(() -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
                                                 try {
                                                   Thread.sleep(new Random().nextInt(100));
                                                   indicator.cancel();
                                                   ProgressManager.checkCanceled();
                                                   fail("checkCanceled() must know about canceled indicator even from different thread");
                                                 }
                                                 catch (ProcessCanceledException ignored) {
                                                 }
                                                 catch (Throwable e) {
                                                   exception = e;
                                                 }
                                               }, indicator), "indicator test"));
      threads.forEach(Thread::start);
      ConcurrencyUtil.joinAll(threads);
    }
    if (exception != null) throw exception;
  }

  private volatile boolean checkCanceledCalled;
  private volatile boolean taskCanceled;
  private volatile boolean taskSucceeded;
  private volatile Throwable exception;
  public void testProgressManagerCheckCanceledDoesNotDelegateToProgressIndicatorIfThereAreNoCanceledIndicators() {
    final long warmupEnd = System.currentTimeMillis() + 1000;
    final long end = warmupEnd + 10000;
    checkCanceledCalled = false;
    final ProgressIndicatorBase myIndicator = new ProgressIndicatorBase();
    taskCanceled = taskSucceeded = false;
    exception = null;
    Future<?> future = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
      new Task.Backgroundable(getProject(), "Xxx") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            assertFalse(ApplicationManager.getApplication().isDispatchThread());
            assertSame(indicator, myIndicator);
            while (System.currentTimeMillis() < end) {
              ProgressManager.checkCanceled();
            }
          }
          catch (ProcessCanceledException e) {
            exception = e;
            checkCanceledCalled = true;
            throw e;
          }
          catch (RuntimeException | Error e) {
            exception = e;
            throw e;
          }
        }

        @Override
        public void onCancel() {
          taskCanceled = true;
        }

        @Override
        public void onSuccess() {
          taskSucceeded = true;
        }
      }, myIndicator, null);

    ApplicationManager.getApplication().assertIsDispatchThread();

    while (!future.isDone()) {
      if (System.currentTimeMillis() < warmupEnd) {
        assertFalse(checkCanceledCalled);
      }
      else {
        myIndicator.cancel();
      }
    }
    // invokeLater in runProcessWithProgressAsynchronously
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(checkCanceledCalled);
    assertFalse(taskSucceeded);
    assertTrue(taskCanceled);
    assertTrue(String.valueOf(exception), exception instanceof ProcessCanceledException);
  }

  private volatile boolean myFlag;
  public void testPerverseIndicator() {
    checkCanceledCalled = false;
    ProgressIndicator indicator = new ProgressIndicatorStub() {
      @Override
      public void checkCanceled() throws ProcessCanceledException {
        checkCanceledCalled = true;
        if (myFlag) throw new ProcessCanceledException();
      }
    };
    ensureCheckCanceledCalled(indicator);
  }

  private void ensureCheckCanceledCalled(@NotNull ProgressIndicator indicator) {
    myFlag = false;
    JobScheduler.getScheduler().schedule(() -> myFlag = true, 100, TimeUnit.MILLISECONDS);
    final long start = System.currentTimeMillis();
    try {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        while (System.currentTimeMillis() - start < 10000) {
          ProgressManager.checkCanceled();
        }
      }, indicator);
      fail("must have thrown PCE");
    }
    catch (ProcessCanceledException e) {
      assertTrue(checkCanceledCalled);
    }
  }

  public void testExtremelyPerverseIndicatorWhichCancelMethodIsNoop() {
    checkCanceledCalled = false;
    ProgressIndicator indicator = new ProgressIndicatorStub() {
      @Override
      public void checkCanceled() throws ProcessCanceledException {
        checkCanceledCalled = true;
        if (myFlag) throw new ProcessCanceledException();
      }

      @Override
      public void cancel() {
      }
    };
    ensureCheckCanceledCalled(indicator);
  }

  public void testNestedIndicatorsAreCanceledRight() {
    checkCanceledCalled = false;
    ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      assertFalse(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
      ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      assertTrue(indicator != null && !indicator.isCanceled());
      indicator.cancel();
      assertTrue(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
      assertTrue(indicator.isCanceled());
      final ProgressIndicatorEx nested = new ProgressIndicatorBase();
      nested.addStateDelegate(new ProgressIndicatorStub() {
        @Override
        public void checkCanceled() throws ProcessCanceledException {
          checkCanceledCalled = true;
          throw new RuntimeException("must not call checkCanceled()");
        }
      });
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        assertFalse(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
        ProgressIndicator indicator2 = ProgressIndicatorProvider.getGlobalProgressIndicator();
        assertTrue(indicator2 != null && !indicator2.isCanceled());
        assertSame(indicator2, nested);
        ProgressManager.checkCanceled();
      }, nested);

      ProgressIndicator indicator3 = ProgressIndicatorProvider.getGlobalProgressIndicator();
      assertSame(indicator, indicator3);

      assertTrue(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
    }, new EmptyProgressIndicator());
    assertFalse(checkCanceledCalled);
  }

  public void testWrappedIndicatorsAreSortedRight() {
    EmptyProgressIndicator indicator1 = new EmptyProgressIndicator();
    DelegatingProgressIndicator indicator2 = new DelegatingProgressIndicator(indicator1);
    final DelegatingProgressIndicator indicator3 = new DelegatingProgressIndicator(indicator2);
    ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      ProgressIndicator current = ProgressIndicatorProvider.getGlobalProgressIndicator();
      assertSame(indicator3, current);
    }, indicator3);
    assertFalse(checkCanceledCalled);
  }

  public void testProgressPerformance() {
    PlatformTestUtil.startPerformanceTest("executeProcessUnderProgress", 100, () -> {
      EmptyProgressIndicator indicator = new EmptyProgressIndicator();
      for (int i=0;i<100000;i++) {
        ProgressManager.getInstance().executeProcessUnderProgress(EmptyRunnable.getInstance(), indicator);
      }
    }).assertTiming();
  }

  public void testWrapperIndicatorGotCanceledTooWhenInnerIndicatorHas() {
    final ProgressIndicator progress = new ProgressIndicatorBase(){
      @Override
      protected boolean isCancelable() {
        return true;
      }
    };
    try {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        assertFalse(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
        assertTrue(!progress.isCanceled());
        progress.cancel();
        assertTrue(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
        assertTrue(progress.isCanceled());
        while (true) { // wait for PCE
          ProgressManager.checkCanceled();
        }
      }, ProgressWrapper.wrap(progress));
      fail("PCE must have been thrown");
    }
    catch (ProcessCanceledException ignored) {

    }
  }

  public void testSOEUnderExtremelyNestedWrappedIndicator() {
    ProgressIndicator indicator = new DaemonProgressIndicator();
    for (int i=0;i<10000;i++) {
      indicator = new SensitiveProgressWrapper(indicator);
    }
    ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      assertTrue(progressIndicator instanceof SensitiveProgressWrapper);
      progressIndicator.checkCanceled();
      progressIndicator.isCanceled();
    }, indicator);
  }

  public void testBombedIndicator() {
    final int count = 10;
    new BombedProgressIndicator(count).runBombed(() -> {
      for (int i = 0; i < count * 2; i++) {
        TimeoutUtil.sleep(10);
        try {
          ProgressManager.checkCanceled();
          if (i >= count) {
            ProgressManager.checkCanceled();
            fail("PCE expected on " + i + "th check");
          }
        }
        catch (ProcessCanceledException e) {
          if (i < count) {
            fail("Too early PCE");
          }
        }
      }
    });
  }

  private static class ProgressIndicatorStub implements ProgressIndicatorEx {
    private volatile boolean myCanceled;

    @Override
    public void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
      throw new RuntimeException();
    }

    @Override
    public void finish(@NotNull TaskInfo task) {
    }

    @Override
    public boolean isFinished(@NotNull TaskInfo task) {
      throw new RuntimeException();
    }

    @Override
    public boolean wasStarted() {
      throw new RuntimeException();
    }

    @Override
    public void processFinish() {
      throw new RuntimeException();
    }

    @Override
    public void initStateFrom(@NotNull ProgressIndicator indicator) {
    }

    @NotNull
    @Override
    public Stack<String> getTextStack() {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public DoubleArrayList getFractionStack() {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public Stack<String> getText2Stack() {
      throw new RuntimeException();
    }

    @Override
    public int getNonCancelableCount() {
      throw new RuntimeException();
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void setText(String text) {
      throw new RuntimeException();
    }

    @Override
    public String getText() {
      throw new RuntimeException();
    }

    @Override
    public String getText2() {
      throw new RuntimeException();
    }

    @Override
    public void setText2(String text) {
      throw new RuntimeException();
    }

    @Override
    public double getFraction() {
      throw new RuntimeException();
    }

    @Override
    public void setFraction(double fraction) {
      throw new RuntimeException();
    }

    @Override
    public void pushState() {
      throw new RuntimeException();
    }

    @Override
    public void popState() {
      throw new RuntimeException();
    }

    @Override
    public void startNonCancelableSection() {
      throw new RuntimeException();
    }

    @Override
    public void finishNonCancelableSection() {
      throw new RuntimeException();
    }

    @Override
    public boolean isModal() {
      return false;
    }

    @NotNull
    @Override
    public ModalityState getModalityState() {
      throw new RuntimeException();
    }

    @Override
    public void setModalityProgress(ProgressIndicator modalityProgress) {
      throw new RuntimeException();
    }

    @Override
    public boolean isIndeterminate() {
      throw new RuntimeException();
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
      throw new RuntimeException();
    }

    @Override
    public boolean isPopupWasShown() {
      throw new RuntimeException();
    }

    @Override
    public boolean isShowing() {
      throw new RuntimeException();
    }

    @Override
    public boolean isRunning() {
      return true;
    }

    @Override
    public void cancel() {
      myCanceled = true;
      ProgressManager.canceled(this);
    }

    @Override
    public boolean isCanceled() {
      return myCanceled;
    }

    @Override
    public void checkCanceled() throws ProcessCanceledException {
       if (myCanceled) throw new ProcessCanceledException();
    }
  }

  public void testDefaultModalityWithNestedProgress() {
    assertEquals(ModalityState.NON_MODAL, ModalityState.defaultModalityState());
    ProgressManager.getInstance().run(new Task.Modal(getProject(), "", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          assertFalse(ModalityState.NON_MODAL.equals(ModalityState.defaultModalityState()));
          assertEquals(ProgressManager.getInstance().getProgressIndicator().getModalityState(), ModalityState.defaultModalityState());
          ProgressManager.getInstance().runProcess(() -> {
            assertSame(indicator.getModalityState(), ModalityState.defaultModalityState());
            assertInvokeAndWaitWorks();
          }, new ProgressIndicatorBase());
        }
        catch (Throwable e) {
          throw new RuntimeException(e); // ProgressManager doesn't handle errors
        }
      }
    });
  }

  public void testProgressWrapperModality() {
    ProgressManager.getInstance().run(new Task.Modal(getProject(), "", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(
            () -> ProgressManager.getInstance().runProcess(
              ProgressIndicatorTest::assertInvokeAndWaitWorks,
              ProgressWrapper.wrap(indicator)));
          future.get(2000, TimeUnit.MILLISECONDS);
        }
        catch (Throwable e) {
          throw new RuntimeException(e); // ProgressManager doesn't handle errors
        }
      }
    });
  }

  private static void assertInvokeAndWaitWorks() {
    Semaphore semaphore = new Semaphore();
    semaphore.down();
    ApplicationManager.getApplication().invokeLater(semaphore::up);
    assertTrue("invokeAndWait would deadlock", semaphore.waitFor(1000));
  }

  public void testNonCancelableSectionDetectedCorrectly() {
    ProgressManager progressManager = ProgressManager.getInstance();
    assertFalse(progressManager.isInNonCancelableSection());
    progressManager.run(new Task.Modal(getProject(), "", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        assertFalse(indicator instanceof NonCancelableIndicator);
        assertFalse(progressManager.isInNonCancelableSection());
        progressManager.executeNonCancelableSection(() -> {
          assertTrue(progressManager.getProgressIndicator() instanceof NonCancelableIndicator);
          assertTrue(progressManager.isInNonCancelableSection());

          progressManager.executeProcessUnderProgress(() -> {
            assertFalse(progressManager.getProgressIndicator() instanceof NonCancelableIndicator);
            assertTrue(progressManager.isInNonCancelableSection());
          }, new DaemonProgressIndicator());

          assertTrue(progressManager.isInNonCancelableSection());
        });

        assertFalse(progressManager.isInNonCancelableSection());
      }
    });
    assertFalse(progressManager.isInNonCancelableSection());
  }

  public void testProgressIndicatorUtilsScheduleWithWriteActionPriorityMustRemoveListenerBeforeContinuationStartsExecutingInEDT() {
    final AtomicBoolean canceled = new AtomicBoolean();
    final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    AtomicReference<CompletableFuture<?>> future = new AtomicReference<>();
    future.set(ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, new ReadTask() {
      @Override
      public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
        return new Continuation(() -> {
          assertFalse(future.get().isDone());
          WriteAction.run(() -> {
          });  // when I start write action from the continuation, the read task must not cancel
        });
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
        canceled.set(true);
        fail();
      }
    }));
    while (!future.get().isDone()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    assertFalse(canceled.get());
  }

  public void testProgressRestoresModalityOnPumpingException() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    String msg = "expected message";
    try {
      ProgressManager.getInstance().run(new Task.Modal(getProject(), "Title", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          ApplicationManager.getApplication().invokeLater(() -> {
            throw new AssertionError(msg);
          });
          
          // ensure previous runnable is executed during progress, not after it
          ApplicationManager.getApplication().invokeAndWait(EmptyRunnable.getInstance());
        }
      });
      fail("should fail");
    }
    catch (Throwable e) {
      assertTrue(e.getMessage(), e.getMessage().endsWith(msg));
      assertSame(ModalityState.NON_MODAL, ModalityState.current());
    }
    finally {
      LaterInvocator.leaveAllModals();
    }
  }
}
