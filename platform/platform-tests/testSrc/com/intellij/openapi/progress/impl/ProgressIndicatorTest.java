// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.concurrency.JobScheduler;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.assertj.core.util.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        // checkCanceled() must have caught just canceled indicator
        assertThrows(ProcessCanceledException.class, () -> {
          indicator.cancel();
          ProgressManager.checkCanceled();
        });
      }, indicator);
    }
  }

  private volatile long prevTime;
  private volatile long now;

  public void testCheckCanceledGranularity() {
    prevTime = now = 0;
    final long warmupEnd = System.currentTimeMillis() + 1000;
    final LongArrayList times = new LongArrayList();
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
    }, "", false, true, getProject(), null, "");
    long averageDelay = ArrayUtil.averageAmongMedians(times.toLongArray(), 5);
    LOG.debug("averageDelay = " + averageDelay);
    assertTrue(averageDelay < CoreProgressManager.CHECK_CANCELED_DELAY_MILLIS *3);
  }

  public void testProgressIndicatorUtilsScheduleWithWriteActionPriority() throws Exception {
    final AtomicBoolean insideReadAction = new AtomicBoolean();
    final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    CompletableFuture<?> future = ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, new ReadTask() {
      @Override
      public void computeInReadAction(@NotNull ProgressIndicator indicator) {
        insideReadAction.set(true);
        waitForPCE();
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
      }
    });
    UIUtil.dispatchAllInvocationEvents();
    //noinspection StatementWithEmptyBody
    while (!insideReadAction.get()) {

    }
    ApplicationManager.getApplication().runWriteAction(() -> assertTrue(indicator.isCanceled()));
    assertTrue(indicator.isCanceled());
    waitForCompleteInEDT(future);
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
      waitForCompleteInEDT(future);
    }
  }

  private static void waitForCompleteInEDT(@NotNull CompletableFuture<?> future) throws InterruptedException, ExecutionException {
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
      int N = 10;
      Semaphore started = new Semaphore(N);
      Semaphore others = new Semaphore(1);
      List<Future<?>> threads = ContainerUtil.map(Collections.nCopies(N, ""),
          __ -> ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
            try {
              //checkCanceled() must know about canceled indicator even from different thread
              assertThrows(ProcessCanceledException.class, () -> {
                started.up();
                others.waitFor();
                indicator.cancel();
                ProgressManager.checkCanceled();
              });
            }
            catch (Throwable e) {
              exception = e;
            }
          }, indicator)));
      started.waitFor();
      others.up();
      ConcurrencyUtil.getAll(threads);
    }
    if (exception != null) throw exception;
  }

  private volatile boolean checkCanceledCalled;
  private volatile boolean taskCanceled;
  private volatile boolean taskSucceeded;
  private volatile Throwable exception;
  public void testProgressManagerCheckCanceledDoesNotDelegateToProgressIndicatorIfThereAreNoCanceledIndicators() {
    final long warmupEnd = System.currentTimeMillis() + 1000;
    checkCanceledCalled = false;
    final ProgressIndicatorBase myIndicator = new ProgressIndicatorBase();
    taskCanceled = taskSucceeded = false;
    exception = null;
    final long end = warmupEnd + 10000;
    Future<?> future = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
      new Task.Backgroundable(getProject(), "Xxx") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            ApplicationManager.getApplication().assertIsNonDispatchThread();
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

    ThreadingAssertions.assertEventDispatchThread();

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
    TestTimeOut t = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
    assertThrows(ProcessCanceledException.class, () ->
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        while (!t.timedOut()) {
          ProgressManager.checkCanceled();
        }
      }, indicator));
    assertTrue(checkCanceledCalled);
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
      assertFalse(CoreProgressManager.hasThreadUnderCanceledIndicator(Thread.currentThread()));
      ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      assertTrue(indicator != null && !indicator.isCanceled());
      indicator.cancel();
      assertTrue(CoreProgressManager.hasThreadUnderCanceledIndicator(Thread.currentThread()));
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
        assertFalse(CoreProgressManager.hasThreadUnderCanceledIndicator(Thread.currentThread()));
        ProgressIndicator indicator2 = ProgressIndicatorProvider.getGlobalProgressIndicator();
        assertTrue(indicator2 != null && !indicator2.isCanceled());
        assertSame(indicator2, nested);
        ProgressManager.checkCanceled();
      }, nested);

      ProgressIndicator indicator3 = ProgressIndicatorProvider.getGlobalProgressIndicator();
      assertSame(indicator, indicator3);

      assertTrue(CoreProgressManager.hasThreadUnderCanceledIndicator(Thread.currentThread()));
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
    Benchmark.newBenchmark("executeProcessUnderProgress", () -> {
      EmptyProgressIndicator indicator = new EmptyProgressIndicator();
      for (int i=0;i<100000;i++) {
        ProgressManager.getInstance().executeProcessUnderProgress(EmptyRunnable.getInstance(), indicator);
      }
    }).start();
  }

  public void testWrapperIndicatorGotCanceledTooWhenInnerIndicatorHas() {
    final ProgressIndicator progress = new ProgressIndicatorBase(){
      @Override
      protected boolean isCancelable() {
        return true;
      }
    };
    assertThrows(ProcessCanceledException.class, () ->
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        assertFalse(CoreProgressManager.hasThreadUnderCanceledIndicator(Thread.currentThread()));
        assertFalse(progress.isCanceled());
        progress.cancel();
        assertTrue(CoreProgressManager.hasThreadUnderCanceledIndicator(Thread.currentThread()));
        assertTrue(progress.isCanceled());
        waitForPCE();
      }, ProgressWrapper.wrap(progress))
    );
  }

  public void testCheckCanceledAfterWrappedIndicatorIsCanceledAndBaseIndicatorIsNotCanceled() {
    ProgressIndicator base = new EmptyProgressIndicator();
    ProgressIndicator wrapper = new SensitiveProgressWrapper(base);

    wrapper.cancel();
    assertTrue(wrapper.isCanceled());
    assertFalse(base.isCanceled());

    assertThrows(ProcessCanceledException.class, () ->
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        assertTrue(wrapper.isCanceled());

        ProgressManager.checkCanceled(); // this is the main check
      }, wrapper));
  }

  private static void waitForPCE() {
    //noinspection InfiniteLoopStatement
    while (true) {
      ProgressManager.checkCanceled();
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
            assertThrows(ProcessCanceledException.class, () -> ProgressManager.checkCanceled());
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
    assertEquals(ModalityState.nonModal(), ModalityState.defaultModalityState());
    ProgressManager.getInstance().run(new Task.Modal(getProject(), "", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          assertFalse(ModalityState.nonModal().equals(ModalityState.defaultModalityState()));
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

  public void testProgressRestoresModalityOnPumpingException() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      String msg = "expected message";
      try {
        assertThrows(AssertionError.class, () ->
          ProgressManager.getInstance().run(new Task.Modal(getProject(), "Title", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              ApplicationManager.getApplication().invokeLater(() -> {
                throw new AssertionError(msg);
              });

              // ensure previous runnable is executed during progress, not after it
              ApplicationManager.getApplication().invokeAndWait(EmptyRunnable.getInstance());
            }
          }));
        assertSame(ModalityState.nonModal(), ModalityState.current());
      }
      finally {
        LaterInvocator.leaveAllModals();
      }
    });
  }

  public void test_runUnderDisposeAwareIndicator_DoesNotHang_ByCancelThreadProgress() {
    final EmptyProgressIndicator threadIndicator = new EmptyProgressIndicator(ModalityState.defaultModalityState());
    ProgressIndicatorUtils.awaitWithCheckCanceled(
      ApplicationManager.getApplication().executeOnPooledThread(
        () -> ProgressManager.getInstance().executeProcessUnderProgress(
          () -> BackgroundTaskUtil.runUnderDisposeAwareIndicator(
            getTestRootDisposable(),
            () -> {
              threadIndicator.cancel();
              ProgressManager.checkCanceled();
              fail();
            }),
          threadIndicator
        )
      ));
  }

  public void test_runInReadActionWithWriteActionPriority_DoesNotHang() {
    AtomicBoolean finished = new AtomicBoolean();
    Runnable action = () -> {
      TestTimeOut t = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
      while (!finished.get()) {
        ProgressManager.checkCanceled();
        if (t.timedOut()) {
          finished.set(true);
          throw new AssertionError("Too long without cancellation");
        }
      }
    };

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      futures.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
        while (!finished.get()) {
          ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(action);
        }
      }));
      futures.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
        ProgressIndicatorBase reusableProgress = new ProgressIndicatorBase(true);
        while (!finished.get()) {
          ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(action, reusableProgress);
        }
      }));
      futures.add(ReadAction.nonBlocking(action).submit(AppExecutorUtil.getAppExecutorService()));
    }

    for (int i = 0; i < 10_000 && !finished.get(); i++) {
      UIUtil.dispatchAllInvocationEvents();
      WriteAction.run(() -> {});
    }
    finished.set(true);

    waitForFutures(futures);
  }

  private static void waitForFutures(List<? extends Future<?>> futures) {
    for (Future<?> future : futures) {
      PlatformTestUtil.waitForFuture(future, 10_000);
    }
  }

  public void testDuringProgressManagerExecuteNonCancelableSectionTheIndicatorIsCancelableShouldReturnFalse() {
    MyAbstractProgressIndicator progress = new MyAbstractProgressIndicator();
    ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      assertFalse(progress.isCanceled());
      assertTrue(progress.isCancelable());
      try {
        ProgressManager.getInstance().executeNonCancelableSection(()->{
          assertFalse(progress.isCancelable());
          progress.cancel();
          assertTrue(progress.isCanceled());
          progress.checkCanceled();
        });
      }
      catch (ProcessCanceledException e) {
        e.printStackTrace();
        fail("must not throw");
      }

      assertThrows(ProcessCanceledException.class, () -> progress.checkCanceled());
    }, progress);
  }

  public void testWithTimeout() {
    assertEquals("a", ProgressIndicatorUtils.withTimeout(1_000_000_000, () -> "a"));

    assertNull(ProgressIndicatorUtils.withTimeout(1, () -> {
      TimeoutUtil.sleep(50);
      ProgressManager.checkCanceled();
      return "a";
    }));

    assertThrows(ProcessCanceledException.class, () -> ProgressIndicatorUtils.withTimeout(1, () -> {
      throw new ProcessCanceledException();
    }));

    ProgressIndicatorBase outer = new ProgressIndicatorBase();
    ProgressManager.getInstance().runProcess(() -> assertThrows(ProcessCanceledException.class, () ->
      ProgressIndicatorUtils.withTimeout(1, () -> {
        outer.cancel();
        ProgressManager.checkCanceled();
        return null;
      })
    ), outer);
  }

  private static class MyAbstractProgressIndicator extends AbstractProgressIndicatorBase {
    @VisibleForTesting
    @Override
    public boolean isCancelable() {
      return super.isCancelable();
    }
  }

  public void testEmptyIndicatorMustConformToAtLeastSomeSimpleLifecycleConstrains() {
    ProgressIndicator indicator = new EmptyProgressIndicator();
    for (int i = 0; i < 2; i++) {
      assertThrows(IllegalStateException.class, indicator::stop);
      indicator.start();
      assertThrows(IllegalStateException.class, indicator::start);
      indicator.stop();
      assertThrows(IllegalStateException.class, indicator::stop);
    }
  }

  public void testProgressIndicatorsAttachToStartedProgress() {
    ProgressIndicatorEx progressIndicator = new ProgressIndicatorBase();
    progressIndicator.start();
    progressIndicator.setText("Progress 0/2");

    // attach
    ProgressIndicatorEx progressIndicatorWatcher1 = new ProgressIndicatorBase();
    ProgressIndicatorEx progressIndicatorGroup = new ProgressIndicatorBase();
    progressIndicatorGroup.addStateDelegate(progressIndicatorWatcher1);

    progressIndicator.addStateDelegate(progressIndicatorGroup);

    assertEquals(progressIndicator.getText(), progressIndicatorGroup.getText());
    assertEquals(progressIndicator.getText(), progressIndicatorWatcher1.getText());

    progressIndicator.setText("Progress 1/2");

    // attach
    ProgressIndicatorEx progressIndicatorWatcher2 = new ProgressIndicatorBase();
    progressIndicatorGroup.addStateDelegate(progressIndicatorWatcher2);

    assertEquals(progressIndicator.getText(), progressIndicatorGroup.getText());
    assertEquals(progressIndicator.getText(), progressIndicatorWatcher1.getText());
    assertEquals(progressIndicator.getText(), progressIndicatorWatcher2.getText());

    progressIndicator.setText("Progress 2/2");
    progressIndicator.stop();

    assertEquals(progressIndicator.getText(), progressIndicatorGroup.getText());
    assertEquals(progressIndicator.getText(), progressIndicatorWatcher1.getText());
    assertEquals(progressIndicator.getText(), progressIndicatorWatcher2.getText());
  }

  public void testProgressWrapperDoesWrapWrappers() {
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    ProgressWrapper wrapper = ProgressWrapper.wrap(indicator);
    assertNotNull(wrapper);
    ProgressWrapper wrapper2 = ProgressWrapper.wrap(wrapper);
    assertNotSame(wrapper2, wrapper);
    assertSame(wrapper, wrapper2.getOriginalProgressIndicator());
  }

  public void testRelayUiToDelegateIndicatorMustNotPassChangeStateToDelegate() {
    ProgressIndicatorEx ui = new ProgressIndicatorBase();
    ProgressIndicatorEx indicator = new ProgressIndicatorBase();
    indicator.pushState();
    indicator.addStateDelegate(new RelayUiToDelegateIndicator(ui));
    indicator.popState(); // should not cause NPE
  }

  public void testPushStateMustStoreIndeterminateFlag() {
    ProgressIndicatorEx indicator = new ProgressIndicatorBase();
    indicator.setIndeterminate(true);
    indicator.pushState();
    indicator.setIndeterminate(false);
    assertFalse(indicator.isIndeterminate());
    indicator.popState();
    assertTrue(indicator.isIndeterminate());
  }

  public void testRelayUiToDelegateIndicatorMustBeReusable() {
    ProgressIndicatorEx ui = new ProgressIndicatorBase();
    RelayUiToDelegateIndicator relay = new RelayUiToDelegateIndicator(ui);
    ProgressIndicatorBase indicator = new ProgressIndicatorBase(true);
    indicator.addStateDelegate(relay);
    indicator.start();
    indicator.cancel();
    indicator.stop();
    indicator.start();
    indicator.cancel();
    indicator.removeStateDelegate(relay);
  }

  public void testRelayUiToDelegate() {
    ProgressIndicatorEx ui = new ProgressIndicatorBase();
    ProgressIndicatorEx indicator = new ProgressIndicatorBase();

    indicator.addStateDelegate(new RelayUiToDelegateIndicator(ui));

    assertNull(ui.getText());
    indicator.setText("A");
    assertEquals("A", ui.getText());
    assertNull(ui.getText2());
    indicator.setText2("B");
    assertEquals("B", ui.getText2());
    assertEquals(0.0, ui.getFraction());
    indicator.setIndeterminate(false);
    indicator.setFraction(1);
    assertEquals(1.0, ui.getFraction());

    assertThrows(IllegalStateException.class, () -> new RelayUiToDelegateIndicator(ui).addStateDelegate(new ProgressIndicatorBase()));
  }

  public void testRunProcessWithIndicatorAlreadyUsedInTheThisThreadMustBeWarned() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      ProgressIndicatorEx p = new ProgressIndicatorBase();
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        boolean allowed = true;
        try {
          ProgressManager.getInstance().runProcess(() -> {
          }, p);
        }
        catch (Throwable ignored) {
          allowed = false;
        }
        assertFalse("pm.runProcess() with the progress already used in the other thread must be prohibited", allowed);
      }, p);
    });
  }

  public void testRunProcessWithIndicatorAlreadyUsedInTheOtherThreadMustBeWarned() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      ProgressIndicatorEx p = new ProgressIndicatorBase();
      CountDownLatch run = new CountDownLatch(1);
      CountDownLatch exit = new CountDownLatch(1);
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(() -> {
        try {
          run.countDown();
          exit.await();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }, p));
      run.await();
      boolean allowed = true;
      try {
        ProgressManager.getInstance().runProcess(() -> { }, p);
      }
      catch (Throwable ignored) {
        allowed = false;
      }
      finally {
        exit.countDown();
        future.get();
      }
      assertFalse("pm.runProcess() with the progress already used in the other thread must be prohibited", allowed);
    });
  }

  public void testRelayUiToDelegateIndicatorCopiesEverything() {
    ProgressIndicatorBase ui = new ProgressIndicatorBase();
    ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    indicator.setIndeterminate(false);
    indicator.setFraction(0.3141519);
    indicator.setText("0.3141519");
    indicator.setText2("2.3141519");
    indicator.addStateDelegate(new RelayUiToDelegateIndicator(ui));
    //make sure state is replicated correctly
    assertFalse(ui.isIndeterminate());
    assertEquals(0.3141519, ui.getFraction());
    assertEquals("0.3141519", ui.getText());
    assertEquals("2.3141519", ui.getText2());
  }

  public void testMessagePumpingInProgressWindow_startBlockingMustNotStopWhenSomeOneStoppedIndicator() {
    ProgressManager.getInstance().run(new Task.Modal(null, "", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.stop();
        // this makes ProgressWindows#isRunning() false, thus it stops messages processing
        // to make it fail more predictably
        TimeoutUtil.sleep(50);

        // deadlocks, because ProgressWindow is no longer processing the message pump
        ApplicationManager.getApplication().invokeAndWait(() -> {
        });
      }
    });
  }

  public void testMessagePumpingInProgressWindow_startBlockingMustNotStopWhenSomeOneDoesWeirdDelegateTricksWithIndicator() {
    ProgressManager.getInstance().run(new Task.Modal(null, "", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        new ProgressIndicatorBase().addStateDelegate((ProgressIndicatorEx)indicator);
        // this makes ProgressWindows#isRunning() false, thus it stops messages processing
        // to make it fail more predictably
        TimeoutUtil.sleep(50);

        // deadlocks, because ProgressWindow is no longer processing the message pump
        ApplicationManager.getApplication().invokeAndWait(() -> {
        });
      }
    });
  }

  public void testTaskWithResultMustBeAbleToComputeException() throws Exception {
    Exception result = ProgressManager.getInstance().run(new Task.WithResult<Exception, Exception>(null, "", true) {
      @Override
      protected Exception compute(@NotNull ProgressIndicator indicator) {
        return new Exception("result");
      }
    });
    assertEquals("result", result.getMessage());
  }

  public void testInvalidStateActionsMustLeadToExceptions() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      ProgressIndicator indicator = new ProgressIndicatorBase(false);
      indicator.start();
      assertThrows(Exception.class, () -> indicator.start());
      indicator.cancel();
      indicator.stop();
      assertThrows(Exception.class, () -> indicator.start());

      assertThrows(Exception.class, () -> new ProgressIndicatorBase().stop());
    });
  }

  public void testComplexCheckCanceledHookDoesntInterfereWithReadLockAcquire() throws Exception {
    AtomicBoolean futureEntered = new AtomicBoolean();
    AtomicBoolean futureExited = new AtomicBoolean();
    AtomicBoolean readActionCompleted = new AtomicBoolean();
    CoreProgressManager.CheckCanceledHook hook = __ -> {
      doReadAction(); // in case this hook gets called during read action, it will eventually SOE
      return false;
    };
    ((ProgressManagerImpl)ProgressManager.getInstance()).addCheckCanceledHook(hook);
    Disposer.register(getTestRootDisposable(), ()->((ProgressManagerImpl)ProgressManager.getInstance()).removeCheckCanceledHook(hook));

    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    ProgressIndicator indicator = new ProgressIndicatorBase(false);
    indicator.start();

    ThreadingAssertions.assertEventDispatchThread();

    AtomicReference<Future<?>> future = new AtomicReference<>();
    doReadAction();

    WriteAction.run(() -> {
      future.set(ApplicationManager.getApplication().executeOnPooledThread(() ->
         ProgressManager.getInstance().runProcess(() -> {
           futureEntered.set(true);
           doReadAction();
           readActionCompleted.set(true);
           futureExited.set(true);
         }, indicator)
      ));
      while (!futureEntered.get()) {
        // wait until hook is called and finish write action
      }
      TimeoutUtil.sleep(10_000); // ensure to be inside read action by now
    });
    while (!futureExited.get()) {

    }
    future.get().get();
    assertTrue(readActionCompleted.get());
  }

  // extracted to separate method to avoid re-compilation on hot path taking unpredictable time, blowing timeouts
  private static void doReadAction() {
    ReadAction.run(() -> {
      //
    });
  }

  public void testStopAlreadyStoppedIndicatorMustThrow() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      assertThrows(IllegalStateException.class, () -> new StandardProgressIndicatorBase().stop());

      ProgressIndicator indicator = new StandardProgressIndicatorBase();
      indicator.start();
      indicator.stop();
      assertThrows(IllegalStateException.class, () -> indicator.stop());
    });
  }

  public void testStartAlreadyRunningIndicatorMustThrow() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      assertThrows(IllegalStateException.class, () -> {
        ProgressIndicator indicator = new StandardProgressIndicatorBase();
        assertFalse(indicator.isRunning());
        indicator.start();
        assertTrue(indicator.isRunning());
        indicator.start();
      });

      assertThrows(IllegalStateException.class, () -> {
        ProgressIndicator indicator = new StandardProgressIndicatorBase();
        assertFalse(indicator.isRunning());
        indicator.start();
        assertTrue(indicator.isRunning());
        indicator.cancel();
        indicator.start();
      });

      ProgressIndicator indicator = new AbstractProgressIndicatorBase() {
        @Override
        protected boolean isReuseable() {
          return true;
        }
      };
      indicator.start();
      assertTrue(indicator.isRunning());
      indicator.stop();
      assertFalse(indicator.isRunning());
      indicator.start();
      assertTrue(indicator.isRunning());
    });
  }

  public void testCheckCancelledEvenWithPCEDisabledDoesntThrowInNonCancellableSection() {
    ProgressIndicator indicator = new EmptyProgressIndicator();
    ProgressManager.getInstance().runProcess(() -> {
      ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(indicator);
      indicator.cancel();
      assertThrows(ProcessCanceledException.class, () -> ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(indicator));
      Cancellation.computeInNonCancelableSection(() -> {
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(indicator);
        return null;
      });
      assertThrows(ProcessCanceledException.class, () -> ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(indicator));
      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(indicator);
      });
      assertThrows(ProcessCanceledException.class, () -> ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(indicator));
    }, indicator);
  }

  public void testEmptyProgressIndicatorPointsToTheCauseOfCancellation() {
    Registry.get("ide.rich.cancellation.traces").setValue(true, getTestRootDisposable());
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    notableStacktrace(indicator);
    try {
      indicator.checkCanceled();
      fail("Must throw");
    }
    catch (ProcessCanceledException e) {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      assertTrue(sw.toString().contains("notableStacktrace"));
    }
  }

  public void notableStacktrace(ProgressIndicator indicator) {
    indicator.cancel();
  }
}
