// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;

public final class SafeNullableLazyValueTest extends LightPlatformTestCase {

  private final static long WAIT_TIMEOUT_IN_SECONDS = 30;

  public void testValueIsComputedAsynchronouslyOnEdt() throws Exception {
    final var expectedAsyncValue = "Hey!";
    final var lazyValue = SafeNullableLazyValue.create(() -> expectedAsyncValue);

    final var expectedSyncValue = "Not yet!";
    final var syncValue = EdtTestUtil.runInEdtAndGet(() -> lazyValue.getValueOrElse(expectedSyncValue));
    assertEquals(expectedSyncValue, syncValue);

    final var asyncValue = awaitAsyncValue(lazyValue);
    assertEquals(expectedAsyncValue, asyncValue);
  }

  public void testValueIsComputedAsynchronouslyUnderRA() throws Exception {
    final var expectedAsyncValue = "Hey!";
    final var lazyValue = SafeNullableLazyValue.create(() -> expectedAsyncValue);

    final var expectedSyncValue = "Not yet!";
    final var syncValue = ReadAction.compute(() -> lazyValue.getValueOrElse(expectedSyncValue));
    assertEquals(expectedSyncValue, syncValue);

    final var asyncValue = awaitAsyncValue(lazyValue);
    assertEquals(expectedAsyncValue, asyncValue);
  }

  public void testValueIsComputedSynchronouslyInPooledThreadNotUnderRA() throws Exception {
    final var expectedValue = "Hey!";
    final var lazyValue = SafeNullableLazyValue.create(() -> expectedValue);

    final var syncValue =
      CompletableFuture.supplyAsync(lazyValue::getValue, AppExecutorUtil.getAppExecutorService()).get(WAIT_TIMEOUT_IN_SECONDS, SECONDS);
    assertEquals(expectedValue, syncValue);
  }

  public void testValueIsComputedOnlyOnceWhenNoException() throws Exception {
    final var counter = new AtomicInteger(0);
    final var lazyValue = SafeNullableLazyValue.create(() -> {
      TimeoutUtil.sleep(1000);
      return counter.incrementAndGet();
    });

    final int threadCount = getThreadCount();
    launchBackgroundThreadsAndWait(threadCount, lazyValue::getValue);

    assertEquals(counter.get(), 1);
  }

  public void testValueIsReComputedOnException() throws Exception {
    final var counter = new AtomicInteger(0);
    final var lazyValue = SafeNullableLazyValue.create(() -> {
      counter.incrementAndGet();
      throw new RuntimeException("Oh no!");
    });

    final int threadCount = getThreadCount();
    launchBackgroundThreadsAndWait(threadCount, lazyValue::getValue);

    assertEquals(counter.get(), threadCount);
  }

  private static int getThreadCount() {
    return Math.max(1, Math.min(10, Runtime.getRuntime().availableProcessors() - 1));
  }

  private static void launchBackgroundThreadsAndWait(final int threadCount, final @NotNull Runnable action) throws InterruptedException {
    final var latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; i++) {
      CompletableFuture.runAsync(() -> {
        try {
          action.run();
        }
        finally {
          latch.countDown();
        }
      });
    }
    assertTrue("Wait for threads timed out!", latch.await(WAIT_TIMEOUT_IN_SECONDS, SECONDS));
  }

  private static @Nullable String awaitAsyncValue(final @NotNull SafeNullableLazyValue<String> lazyValue) throws InterruptedException {
    for (int i = 0; i < WAIT_TIMEOUT_IN_SECONDS; i++) {
      if (lazyValue.isComputed()) {
        return lazyValue.getValue();
      }
      Thread.sleep(1000);
    }
    return null;
  }
}
