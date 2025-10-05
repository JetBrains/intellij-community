// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.idea.IJIgnore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.BlockingProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressResult;
import com.intellij.openapi.progress.impl.ProgressRunner;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.ProgressWindowTest.TestProgressWindow;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@RunWith(Parameterized.class)
public class ProgressRunnerTest extends LightPlatformTestCase {
  @Parameterized.Parameter
  public boolean myOnEdt;

  @Parameterized.Parameter(1)
  public boolean myReleaseIWLockOnRun;

  @Parameterized.Parameters(name = "onEdt = {0}, releaseIW = {1}")
  public static List<Object[]> dataOnEdt() {
    List<Object[]> result = new ArrayList<>();
    result.add(new Boolean[]{true, false});
    result.add(new Boolean[]{true, true});
    result.add(new Boolean[]{false, false});
    return result;
  }

  @Override
  protected boolean runInDispatchThread() {
    return myOnEdt;
  }

  @Override
  public void tearDown() throws Exception {
    try {
      UIUtil.dispatchAllInvocationEvents();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Test
  public void testToWriteThreadWithEmptyIndicatorSync() {
    TestTask task = new TestTask().withAssertion(() -> ApplicationManager.getApplication().isWriteIntentLockAcquired());
    ProgressResult<?> result = computeAssertingExceptionConditionally(
      myOnEdt && myReleaseIWLockOnRun,
      () -> new ProgressRunner<>(task)
        .onThread(ProgressRunner.ThreadToUse.WRITE)
        .withProgress(new EmptyProgressIndicator())
        .sync()
        .submitAndGet());
    if (result == null) return;
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToWriteThreadWithProgressWindowSync() {
    TestTask task = new TestTask().withAssertion(() -> ApplicationManager.getApplication().isWriteIntentLockAcquired());
    ProgressResult<?> result = computeAssertingExceptionConditionally(
      myOnEdt && myReleaseIWLockOnRun,
      () -> new ProgressRunner<>(task)
        .onThread(ProgressRunner.ThreadToUse.WRITE)
        .withProgress(createProgressWindow())
        .modal()
        .sync()
        .submitAndGet());
    if (result == null) return;
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToWriteThreadWithEmptyIndicatorAsync() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> ApplicationManager.getApplication().isWriteIntentLockAcquired())
      .lock();
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.WRITE)
      .withProgress(progressIndicator)
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    task.release();

    if (EDT.isCurrentThreadEdt()) {
      ApplicationManagerEx.getApplicationEx().runUnlockingIntendedWrite(() -> {
        // Waiting rationale: a task to write thread might have not been submitted yet
        TimeoutUtil.sleep(100);
        // Dispatching rationale: a task might be submitted to write thread. Hence, we need to ensure flush queue
        // has finished processing pending events.
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        return null;
      });
    }

    ProgressResult<?> result = future.get(1000, TimeUnit.MILLISECONDS);
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToPooledThreadWithEmptyIndicatorSync() {
    // Running sync task on pooled thread from EDT w/o event polling can lead to deadlock if pooled thread will try to invokeAndWait.
    BooleanSupplier assertion = ApplicationManager.getApplication().isDispatchThread()
                                ? () -> ApplicationManager.getApplication().isWriteIntentLockAcquired()
                                : () -> !ApplicationManager.getApplication().isWriteIntentLockAcquired();
    TestTask task = new TestTask().withAssertion(assertion);
    ProgressResult<?> result = computeAssertingExceptionConditionally(
      myOnEdt && myReleaseIWLockOnRun,
      () -> new ProgressRunner<>(task)
        .onThread(ProgressRunner.ThreadToUse.POOLED)
        .withProgress(new EmptyProgressIndicator())
        .sync()
        .submitAndGet());
    if (result == null) return;
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToPooledThreadWithProgressWindowSync() {
    TestTask task = new TestTask().withAssertion(() -> !ApplicationManager.getApplication().isWriteIntentLockAcquired());
    ProgressResult<?> result = computeAssertingExceptionConditionally(
      myOnEdt && myReleaseIWLockOnRun,
      () -> new ProgressRunner<>(task)
        .onThread(ProgressRunner.ThreadToUse.POOLED)
        .withProgress(createProgressWindow())
        .modal()
        .sync()
        .submitAndGet());
    if (result == null) return;
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToPooledThreadWithEmptyIndicatorAsync() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteIntentLockAcquired())
      .lock();
    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(new EmptyProgressIndicator())
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    task.release();

    ProgressResult<?> result = future.get(1000, TimeUnit.MILLISECONDS);
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToPooledThreadWithEmptyIndicatorAsyncWithCancel() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteIntentLockAcquired())
      .lock();
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();

    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(progressIndicator)
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    ensureProgressIsRunning(progressIndicator);
    progressIndicator.cancel();
    task.release();

    ProgressResult<?> result = future.get(1000, TimeUnit.MILLISECONDS);
    assertTrue(result.isCanceled());
    assertInstanceOf(result.getThrowable(), ProcessCanceledException.class);
    task.assertNotFinished();
  }

  @Test
  public void testToPooledThreadWithProgressWindowAsync() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteIntentLockAcquired())
      .lock();
    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(createProgressWindow())
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    task.release();

    ProgressResult<?> result = future.get(1000, TimeUnit.MILLISECONDS);
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToPooledThreadWithProgressWindowAsyncWithCancel() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteIntentLockAcquired())
      .lock();
    CompletableFuture<ProgressWindow> progressIndicator = createProgressWindow();

    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(progressIndicator)
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    ensureProgressIsRunning(progressIndicator.get());
    progressIndicator.get().cancel();
    task.release();

    ProgressResult<?> result = future.get(1000, TimeUnit.MILLISECONDS);
    assertTrue(result.isCanceled());
    assertInstanceOf(result.getThrowable(), ProcessCanceledException.class);
    task.assertNotFinished();
  }

  @Test
  public void testToPooledThreadWithProgressWindowWithCanceledInvokeAndWait() throws Throwable {
    var result = computeAssertingExceptionConditionally(
      !myOnEdt || myReleaseIWLockOnRun,
      () -> {
        return new ProgressRunner<>(() -> {
          var progressWindow = (TestProgressWindow)ProgressIndicatorProvider.getGlobalProgressIndicator();
          var cancelled = new Semaphore(1);
          EventQueue.invokeLater(() -> {
            var modalComponent = progressWindow.getExposedDialog().getPanel();
            var escapeEvent = new KeyEvent(modalComponent, KeyEvent.KEY_PRESSED, System.nanoTime(), 0, KeyEvent.VK_ESCAPE, '');
            IdeEventQueue.getInstance().postEvent(escapeEvent);
            EventQueue.invokeLater(() -> cancelled.up());
          });
          Assert.assertTrue(cancelled.waitFor(1000));
        })
          .onThread(ProgressRunner.ThreadToUse.POOLED)
          .withProgress(new TestProgressWindow(getProject()))
          .modal()
          .sync()
          .submitAndGet();
      });
    if (result == null) {
      return;
    }
    var throwable = result.getThrowable();
    if (throwable != null) {
      throw throwable;
    }
    assertTrue(result.isCanceled());
  }

  @Test
  public void testAsyncModalPooledExecution() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteIntentLockAcquired())
      .lock();
    CompletableFuture<ProgressWindow> progressIndicator = createProgressWindow();

    CompletableFuture<ProgressResult<Object>> future = computeAssertingExceptionConditionally(
      myOnEdt,
      () -> new ProgressRunner<>(task)
        .onThread(ProgressRunner.ThreadToUse.POOLED)
        .withProgress(progressIndicator)
        .modal()
        .submit());
    if (future == null) return;
    assertFalse(future.isDone());
    task.assertNotFinished();

    ensureProgressIsRunning(progressIndicator.get());

    AtomicBoolean test = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeLaterOnWriteThread(() -> test.set(true));

    dispatchEverything();
    assertFalse(test.get());

    task.release();

    ProgressResult<?> result = future.get(1000, TimeUnit.MILLISECONDS);
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    assertTrue(progressIndicator.isDone());
    task.assertFinished();

    dispatchEverything();
    assertTrue(test.get());
  }

  /**
   * Tests only testMode-ish execution: unhandled exceptions on event pumping are `LOG.error`-ed, hence throw exceptions in tests.
   * It is better to be aware of such exceptions in tests so we propagate them in ProgressRunner
   */
  @Test
  @IJIgnore(issue = "IDEA-350585")
  @Ignore
  public void testPumpingExceptionPropagation() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      final String failureMessage = "Expected Failure";
      ProgressResult<?> result = computeAssertingExceptionConditionally(
        myOnEdt && myReleaseIWLockOnRun,
        () -> {
          return new ProgressRunner<>(() ->
                                        UIUtil.invokeAndWaitIfNeeded(() -> {
                                          throw new RuntimeException(failureMessage);
                                        }))
            .onThread(ProgressRunner.ThreadToUse.POOLED)
            .withProgress(createProgressWindow())
            .modal()
            .sync()
            .submitAndGet();
        });
      if (result == null) {
        return;
      }
      assertFalse(result.isCanceled());
      Throwable throwable = result.getThrowable();

      assertNotNull(throwable);
      assertEquals(failureMessage, ExceptionUtil.getRootCause(throwable).getMessage());
    });
  }

  @Test
  public void testStartBlockingExceptionPropagation() {
    var t = new RuntimeException();
    class ThrowingIndicator extends EmptyProgressIndicator implements BlockingProgressIndicator {
      @Override
      public void startBlocking(@NotNull Runnable init, boolean isSynchronousHeadlessExecution, @NotNull CompletableFuture<?> stopCondition) {
        // "enter" modality
        init.run();
        // wait for task future to complete
        try {
          stopCondition.get(1000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
          throw new RuntimeException(e);
        }
        throw t;
      }
    }

    ProgressResult<?> result = computeAssertingExceptionConditionally(
      !myOnEdt || myReleaseIWLockOnRun,
      () -> {
        return new ProgressRunner<>(EmptyRunnable.getInstance())
          .onThread(ProgressRunner.ThreadToUse.POOLED)
          .withProgress(new ThrowingIndicator())
          .modal()
          .submitAndGet();
      });
    if (result == null) {
      return;
    }
    assertFalse(result.isCanceled());
    assertSame(t, result.getThrowable());
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    super.runTestRunnable(() -> {
      if (runInDispatchThread() && myReleaseIWLockOnRun) {
        ApplicationManagerEx.getApplicationEx().runUnlockingIntendedWrite(() -> {
          testRunnable.run();
          return null;
        });
      }
      else {
        testRunnable.run();
      }
    });
  }

  private static <T> T computeAssertingExceptionConditionally(boolean shouldFail, @NotNull Supplier<T> computation) {
    try {
      T result = computation.get();
      assertFalse(shouldFail);
      return result;
    }
    catch (Throwable t) {
      if (!shouldFail) {
        ExceptionUtil.rethrow(t);
      }
      return null;
    }
  }

  @NotNull
  private CompletableFuture<@NotNull ProgressWindow> createProgressWindow() {
    if (EDT.isCurrentThreadEdt()) {
      return CompletableFuture.completedFuture(new ProgressWindow(false, getProject()));
    }
    else {
      return CompletableFuture.supplyAsync(() -> new ProgressWindow(false, getProject()), EdtExecutorService.getInstance());
    }
  }

  private static void ensureProgressIsRunning(ProgressIndicator progressIndicator) {
    for (int iter = 0; iter < 100 && !progressIndicator.isRunning(); ++iter) {
      TimeoutUtil.sleep(1);
    }
  }

  private static void dispatchEverything() {
    if (EDT.isCurrentThreadEdt()) {
      ApplicationManagerEx.getApplicationEx().runUnlockingIntendedWrite(() -> {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        return null;
      });
    }
    else if (ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
      LaterInvocator.pollWriteThreadEventsOnce();
      ApplicationManagerEx.getApplicationEx().runUnlockingIntendedWrite(() -> {
        ApplicationManager.getApplication().invokeAndWait(EmptyRunnable.getInstance(), ModalityState.any());
        return null;
      });
    }
    else {
      Semaphore semaphore = new Semaphore(1);
      ApplicationManager.getApplication().invokeLaterOnWriteThread(semaphore::up, ModalityState.any());
      semaphore.waitFor();
      ApplicationManager.getApplication().invokeAndWait(EmptyRunnable.getInstance(), ModalityState.any());
    }
  }

  private static final class TestTask implements Runnable {

    private final Semaphore mySemaphore;

    private BooleanSupplier myAssertion = () -> true;

    private final Throwable myStacktrace = new Throwable();

    private volatile boolean myFinished = false;

    private TestTask() {
      mySemaphore = new Semaphore();
    }

    TestTask withAssertion(@NotNull BooleanSupplier r) {
      myAssertion = r;
      return this;
    }

    TestTask lock() {
      mySemaphore.down();
      return this;
    }

    void release() {
      mySemaphore.up();
    }

    void assertFinished() {
      if (!myFinished) {
        throw new AssertionError("Task must have finished by now");
      }
    }

    void assertNotFinished() {
      if (myFinished) {
        throw new AssertionError("Task must have not finished by now");
      }
    }

    @Override
    public void run() {
      if (!myAssertion.getAsBoolean()) {
        throw new AssertionError("Supplied assertion failed", myStacktrace);
      }
      mySemaphore.waitFor();
      ProgressManager.checkCanceled();
      TimeoutUtil.sleep(1);
      if (!myAssertion.getAsBoolean()) {
        throw new AssertionError("Supplied assertion failed", myStacktrace);
      }
      myFinished = true;
    }
  }
}
