// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.concurrency.ThreadContext.currentThreadContext;
import static com.intellij.concurrency.ThreadContext.installThreadContext;
import static com.intellij.openapi.progress.ContextKt.prepareThreadContext;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class SafeNullableLazyValueTest extends LightPlatformTestCase {

  private final static long WAIT_TIMEOUT_IN_SECONDS = 30;

  public void testValueIsComputedAsynchronouslyOnEdt() throws Exception {
    final var expectedAsyncValue = "Hey!";
    final var lazyValue = SafeNullableLazyValue.create(() -> expectedAsyncValue);

    final var expectedSyncValue = "Not yet!";
    final var syncValue = runWithDummyProgress(() -> EdtTestUtil.runInEdtAndGet(() -> lazyValue.getValueOrElse(expectedSyncValue)));
    assertEquals(expectedSyncValue, syncValue);

    final var asyncValue = awaitAsyncValue(lazyValue);
    assertEquals(expectedAsyncValue, asyncValue);
  }

  public void testValueIsComputedAsynchronouslyUnderRA() throws Exception {
    final var expectedAsyncValue = "Hey!";
    final var lazyValue = SafeNullableLazyValue.create(() -> expectedAsyncValue);

    final var expectedSyncValue = "Not yet!";
    final var syncValue = runWithDummyProgress(() -> ReadAction.compute(() -> lazyValue.getValueOrElse(expectedSyncValue)));
    assertEquals(expectedSyncValue, syncValue);

    final var asyncValue = awaitAsyncValue(lazyValue);
    assertEquals(expectedAsyncValue, asyncValue);
  }

  public void testValueIsComputedSynchronouslyInPooledThreadNotUnderRA() throws Exception {
    final var expectedValue = "Hey!";
    final var lazyValue = SafeNullableLazyValue.create(() -> expectedValue);

    final var syncValue = runWithDummyProgress(
      () -> {
        var coroutineContext = currentThreadContext();
        return CompletableFuture
          .supplyAsync(
            () -> {
              try (var ignored = installThreadContext(coroutineContext, true)) {
                return lazyValue.getValue();
              }
            },
            AppExecutorUtil.getAppExecutorService())
          .get(WAIT_TIMEOUT_IN_SECONDS, SECONDS);
      });
    assertEquals(expectedValue, syncValue);
  }

  public void testValueIsComputedOnlyOnceWhenNoException() throws Exception {
    final var counter = new AtomicInteger(0);
    final var lazyValue = SafeNullableLazyValue.create(() -> {
      TimeoutUtil.sleep(1000);
      return counter.incrementAndGet();
    });

    final int threadCount = getThreadCount();
    launchBackgroundThreadsAndWait(threadCount, () -> runWithDummyProgress(lazyValue::getValue));

    assertEquals(counter.get(), 1);
  }

  public void testValueIsReComputedOnException() throws Exception {
    final var counter = new AtomicInteger(0);
    final var lazyValue = SafeNullableLazyValue.create(() -> {
      counter.incrementAndGet();
      throw new RuntimeException("Oh no!");
    });

    final int threadCount = getThreadCount();
    launchBackgroundThreadsAndWait(threadCount, () -> runWithDummyProgress(lazyValue::getValue));

    assertEquals(counter.get(), threadCount);
  }

  private static int getThreadCount() {
    return Math.max(1, Math.min(10, Runtime.getRuntime().availableProcessors() - 1));
  }

  private static void launchBackgroundThreadsAndWait(final int threadCount, final @NotNull ThrowableComputable<?, Throwable> action)
    throws InterruptedException {
    final var latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; i++) {
      CompletableFuture.runAsync(() -> {
        try {
          safeRethrow(action);
        }
        finally {
          latch.countDown();
        }
      });
    }
    assertTrue("Wait for threads timed out!", latch.await(WAIT_TIMEOUT_IN_SECONDS, SECONDS));
  }

  private static @Nullable String awaitAsyncValue(final @NotNull SafeNullableLazyValue<String> lazyValue) {
    return runWithDummyProgress(() -> {
      long start = System.nanoTime();
      do {
        if (lazyValue.isComputed()) {
          return lazyValue.getValue();
        }
        //noinspection BusyWait
        Thread.sleep(10);
      }
      while (System.nanoTime() < start + WAIT_TIMEOUT_IN_SECONDS * 1_000_000_000);
      return null;
    });
  }

  private static <T> T runWithDummyProgress(@NotNull ThrowableComputable<T, Exception> body) {
    class Holder extends RuntimeException {
      Holder(Throwable t) { super(t); }
    }
    return safeRethrow(() -> {
      try {
        return ProgressManager.getInstance().runProcess(
          () -> prepareThreadContext(coroutineContext -> {
            try (var ignored = installThreadContext(coroutineContext, true)) {
              return body.compute();
            }
            catch (Exception err) {
              throw new Holder(err);
            }
          }),
          new ProgressIndicatorBase());
      }
      catch (Holder e) {
        throw e.getCause();
      }
    });
  }

  private static <T> T safeRethrow(ThrowableComputable<T, ?> body) {
    try {
      return body.compute();
    }
    catch (Throwable e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof ExecutionException) {
        //noinspection AssignmentToCatchBlockParameter
        e = e.getCause();
      }
      ExceptionUtil.rethrowUnchecked(e);
      throw new RuntimeException(e);
    }
  }
}
