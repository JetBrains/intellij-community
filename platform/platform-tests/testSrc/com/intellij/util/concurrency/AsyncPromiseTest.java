// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.TimeoutUtil.sleep;
import static java.awt.EventQueue.invokeLater;
import static java.awt.EventQueue.isDispatchThread;

public class AsyncPromiseTest {
  private static final boolean PRINT = false;

  enum State {RESOLVE, REJECT, ERROR}

  enum When {NOW, AFTER, BEFORE}

  private static final class CheckedException extends Exception {
  }

  private static boolean isCheckedException(@NotNull Exception exception) {
    return exception instanceof CheckedException;
  }

  private static boolean isMessageError(@NotNull Exception exception) {
    return exception.getClass().getName().equals("org.jetbrains.concurrency.MessageError");
  }

  private static void log(@NotNull String message) {
    if (PRINT) System.out.println(message);
  }

  private static AsyncPromise<String> promise(@NotNull State state, @NotNull When when) {
    assert !isDispatchThread();
    CountDownLatch latch = new CountDownLatch(1);
    AsyncPromise<String> promise = new AsyncPromise<>();
    Runnable task = () -> {
      try {
        sleep(10);
        switch (state) {
          case RESOLVE:
            log("resolve promise");
            promise.setResult("resolved");
            break;
          case REJECT:
            log("reject promise");
            promise.setError("rejected");
            break;
          case ERROR:
            log("notify promise about error to preserve a cause");
            promise.setError(new CheckedException());
            break;
        }
        latch.countDown();
      }
      catch (Throwable throwable) {
        log("unexpected error that breaks current task");
        throwable.printStackTrace();
      }
    };
    switch (when) {
      case NOW:
        log("resolve promise immediately");
        task.run();
        break;
      case AFTER:
        log("resolve promise on another thread");
        invokeLater(task);
        break;
      case BEFORE:
        log("resolve promise on another thread before handler is set");
        invokeLater(task);
        sleep(50);
        break;
    }
    log("add processing handlers");
    promise.processed(value -> log("promise is processed"));
    try {
      log("wait for task completion");
      latch.await(100, TimeUnit.MILLISECONDS);
      if (0 == latch.getCount()) return promise;
      throw new AssertionError("task is not completed");
    }
    catch (InterruptedException exception) {
      throw new AssertionError("task is interrupted", exception);
    }
  }

  @Test
  public void testResolveNow() {
    Promise<String> promise = promise(State.RESOLVE, When.NOW);
    assert "resolved".equals(promise.blockingGet(100));
  }

  @Test
  public void testResolveAfterHandlerSet() {
    Promise<String> promise = promise(State.RESOLVE, When.AFTER);
    assert "resolved".equals(promise.blockingGet(100));
  }

  @Test
  public void testResolveBeforeHandlerSet() {
    Promise<String> promise = promise(State.RESOLVE, When.BEFORE);
    assert "resolved".equals(promise.blockingGet(100));
  }

  @Test
  public void testRejectNow() {
    Promise<String> promise = promise(State.REJECT, When.NOW);
    try {
      assert null == promise.blockingGet(100);
    }
    catch (Exception exception) {
      if (!isMessageError(exception)) throw exception;
    }
  }

  @Test
  public void testRejectAfterHandlerSet() {
    Promise<String> promise = promise(State.REJECT, When.AFTER);
    try {
      assert null == promise.blockingGet(100);
    }
    catch (Exception exception) {
      if (!isMessageError(exception)) throw exception;
    }
  }

  @Test
  public void testRejectBeforeHandlerSet() {
    Promise<String> promise = promise(State.REJECT, When.BEFORE);
    try {
      assert null == promise.blockingGet(100);
    }
    catch (Exception exception) {
      if (!isMessageError(exception)) throw exception;
    }
  }

  @Test
  public void testErrorNow() {
    Promise<String> promise = promise(State.ERROR, When.NOW);
    try {
      assert null == promise.blockingGet(100);
    }
    catch (Exception exception) {
      if (!isCheckedException(exception)) throw exception;
    }
  }

  @Test
  public void testErrorAfterHandlerSet() {
    Promise<String> promise = promise(State.ERROR, When.AFTER);
    try {
      assert null == promise.blockingGet(100);
    }
    catch (Exception exception) {
      if (!isCheckedException(exception)) throw exception;
    }
  }

  @Test
  public void testErrorBeforeHandlerSet() {
    Promise<String> promise = promise(State.ERROR, When.BEFORE);
    try {
      assert null == promise.blockingGet(100);
    }
    catch (Exception exception) {
      if (!isCheckedException(exception)) throw exception;
    }
  }
}
