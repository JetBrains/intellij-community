// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.model.Statement;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.apache.commons.lang.StringUtils.substringBefore;

@RunWith(Parameterized.class)
public class ProgressRunnerTest extends LightPlatformTestCase {
  @Parameterized.Parameter
  public boolean myOnEdt;

  @Rule
  public final TestRule myBaseRule = (base, description) -> new Statement() {
    @Override
    public void evaluate() throws Throwable {
      setName(substringBefore(description.getMethodName(), "["));
      runBare();
    }
  };

  @Parameterized.Parameters(name = "onEdt = {0}")
  public static List<Boolean> data() {
    return Arrays.asList(true, false);
  }

  @Override
  protected boolean runInDispatchThread() {
    return myOnEdt;
  }

  @Override
  public void tearDown() throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        UIUtil.dispatchAllInvocationEvents();
      }
      catch (Throwable e) {
        addSuppressedException(e);
      }
      finally {
        super.tearDown();
      }
    });
  }

  @Test
  public void testToWriteThreadWithEmptyIndicatorSync() {
    TestTask task = new TestTask().withAssertion(() -> ApplicationManager.getApplication().isWriteThread());
    ProgressResult<?> result = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.WRITE)
      .withProgress(new EmptyProgressIndicator())
      .sync()
      .submitAndGet();
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToWriteThreadWithProgressWindowSync() {
    TestTask task = new TestTask().withAssertion(() -> ApplicationManager.getApplication().isWriteThread());
    ProgressResult<?> result = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.WRITE)
      .withProgress(createProgressWindow())
      .withBlockingEdtStart(ProgressWindow::startBlocking)
      .sync()
      .submitAndGet();
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToWriteThreadWithEmptyIndicatorAsync() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> ApplicationManager.getApplication().isWriteThread())
      .lock();
    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.WRITE)
      .withProgress(new EmptyProgressIndicator())
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    task.release();

    if (EDT.isCurrentThreadEdt()) {
      ApplicationManager.getApplication().runUnlockingIntendedWrite(() -> {
        // Waiting rationale: a task to write thread might have not been submitted yet
        TimeoutUtil.sleep(100);
        // Dispatching rationale: a task might be submitted to write thread. Hence, we need to ensure flush queue
        // has finished processing pending events.
        LaterInvocator.dispatchPendingFlushes();
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
    BooleanSupplier assertion = EDT.isCurrentThreadEdt() ? () -> ApplicationManager.getApplication().isWriteThread()
                                                         : () -> !ApplicationManager.getApplication().isWriteThread();
    TestTask task = new TestTask().withAssertion(assertion);
    ProgressResult<?> result = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(new EmptyProgressIndicator())
      .sync()
      .submitAndGet();
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @Test
  public void testToPooledThreadWithProgressWindowSync() {
    TestTask task = new TestTask().withAssertion(() -> !ApplicationManager.getApplication().isWriteThread());
    ProgressResult<?> result = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(createProgressWindow())
      .withBlockingEdtStart(ProgressWindow::startBlocking)
      .sync()
      .submitAndGet();
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  @NotNull
  private CompletableFuture<ProgressWindow> createProgressWindow() {
    if (EDT.isCurrentThreadEdt()) {
      return CompletableFuture.completedFuture(new ProgressWindow(false, getProject()));
    }
    else {
      return CompletableFuture.supplyAsync(() -> new ProgressWindow(false, getProject()), EdtExecutorService.getInstance());
    }
  }

  @Test
  public void testToPooledThreadWithEmptyIndicatorAsync() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteThread())
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
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteThread())
      .lock();
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();

    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(progressIndicator)
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    for (int iter = 0; iter < 100 && !progressIndicator.isRunning(); ++iter) {
      TimeoutUtil.sleep(1);
    }
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
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteThread())
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
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteThread())
      .lock();
    CompletableFuture<ProgressWindow> progressIndicator = createProgressWindow();

    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(progressIndicator)
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    for (int iter = 0; iter < 100 && !progressIndicator.get().isRunning(); ++iter) {
      TimeoutUtil.sleep(1);
    }
    progressIndicator.get().cancel();
    task.release();

    ProgressResult<?> result = future.get(1000, TimeUnit.MILLISECONDS);
    assertTrue(result.isCanceled());
    assertInstanceOf(result.getThrowable(), ProcessCanceledException.class);
    task.assertNotFinished();
  }

  @Test
  public void testNoAsyncExecWithBlockingEdtPumping() {
    boolean hasException = false;
    try {
      new ProgressRunner<>(EmptyRunnable.getInstance())
        .onThread(ProgressRunner.ThreadToUse.POOLED)
        .withProgress(createProgressWindow())
        .withBlockingEdtStart(ProgressWindow::startBlocking)
        .submit();
    }
    catch (Exception e) {
      hasException = true;
    }

    assertTrue("Exception must be thrown", hasException);
  }

  @Override
  protected void runBareImpl(ThrowableRunnable<?> start) throws Exception {
    if (!shouldRunTest()) {
      return;
    }

    TestRunnerUtil.replaceIdeEventQueueSafely();
    if (runInDispatchThread()) {
      EdtTestUtil.runInEdtAndWait(() -> {
        start.run();
      });
    }
    else {
      try {
        start.run();
      }
      catch (Throwable throwable) {
        ExceptionUtil.rethrow(throwable);
      }
    }

    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        Application application = ApplicationManager.getApplication();
        if (application instanceof ApplicationEx) {
          HeavyPlatformTestCase.cleanupApplicationCaches(getProject());
        }
        resetAllFields();
      }
      catch (Throwable e) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
    });

    // just to make sure all deferred Runnables to finish
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());

    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }
  }

  private static class TestTask implements Runnable {
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
