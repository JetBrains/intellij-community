// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class ProgressRunnerTest extends LightPlatformTestCase {
  @Override
  protected void tearDown() throws Exception {
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

  public void testEdtToWriteThreadWithEmptyIndicatorSync() {
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

  public void testEdtToWriteThreadWithProgressWindowSync() {
    TestTask task = new TestTask().withAssertion(() -> ApplicationManager.getApplication().isWriteThread());
    ProgressResult<?> result = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.WRITE)
      .withProgress(new ProgressWindow(false, getProject()))
      .withBlockingEdtStart(ProgressWindow::startBlocking)
      .sync()
      .submitAndGet();
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  public void testEdtToWriteThreadWithEmptyIndicatorAsync() throws Exception {
    TestTask task = new TestTask().withAssertion(() -> ApplicationManager.getApplication().isWriteThread());
    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.WRITE)
      .withProgress(new EmptyProgressIndicator())
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    UIUtil.dispatchAllInvocationEvents();
    ProgressResult<?> result = future.get(1, TimeUnit.MILLISECONDS);
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  // Running sync task on pooled thread from EDT w/o event polling can lead to deadlock if pooled thread will try to invokeAndWait.
  public void testEdtToPooledThreadWithEmptyIndicatorSync() {
    TestTask task = new TestTask().withAssertion(() -> ApplicationManager.getApplication().isWriteThread());
    ProgressResult<?> result = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(new EmptyProgressIndicator())
      .sync()
      .submitAndGet();
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  public void testEdtToPooledThreadWithProgressWindowSync() {
    TestTask task = new TestTask().withAssertion(() -> !ApplicationManager.getApplication().isWriteThread());
    ProgressResult<?> result = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(new ProgressWindow(false, getProject()))
      .withBlockingEdtStart(ProgressWindow::startBlocking)
      .sync()
      .submitAndGet();
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  public void testEdtToPooledThreadWithEmptyIndicatorAsync() throws Exception {
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

    ProgressResult<?> result = future.get(100, TimeUnit.MILLISECONDS);
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  public void testEdtToPooledThreadWithEmptyIndicatorAsyncWithCancel() throws Exception {
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

    ProgressResult<?> result = future.get(100, TimeUnit.MILLISECONDS);
    assertTrue(result.isCanceled());
    assertInstanceOf(result.getThrowable(), ProcessCanceledException.class);
    task.assertNotFinished();
  }

  public void testEdtToPooledThreadWithProgressWindowAsync() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteThread())
      .lock();
    CompletableFuture<ProgressResult<Object>> future = new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(new ProgressWindow(false, getProject()))
      .submit();
    assertFalse(future.isDone());
    task.assertNotFinished();

    task.release();

    ProgressResult<?> result = future.get(100, TimeUnit.MILLISECONDS);
    assertFalse(result.isCanceled());
    assertNull(result.getThrowable());
    task.assertFinished();
  }

  public void testEdtToPooledThreadWithProgressWindowAsyncWithCancel() throws Exception {
    TestTask task = new TestTask()
      .withAssertion(() -> !ApplicationManager.getApplication().isWriteThread())
      .lock();
    ProgressWindow progressIndicator = new ProgressWindow(true, getProject());

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

    ProgressResult<?> result = future.get(100, TimeUnit.MILLISECONDS);
    assertTrue(result.isCanceled());
    assertInstanceOf(result.getThrowable(), ProcessCanceledException.class);
    task.assertNotFinished();
  }

  public void testNoAsyncExecWithBlockingEdtPumping() {
    boolean hasException = false;
    try {
      new ProgressRunner<>(EmptyRunnable.getInstance())
        .onThread(ProgressRunner.ThreadToUse.POOLED)
        .withProgress(new ProgressWindow(false, getProject()))
        .withBlockingEdtStart(ProgressWindow::startBlocking)
        .submit();
    }
    catch (Exception e) {
      hasException = true;
    }

    assertTrue("Exception must be thrown", hasException);
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
      try {
        Thread.sleep(1);
      }
      catch (InterruptedException ignore) {
      }
      if (!myAssertion.getAsBoolean()) {
        throw new AssertionError("Supplied assertion failed", myStacktrace);
      }
      myFinished = true;
    }
  }
}
