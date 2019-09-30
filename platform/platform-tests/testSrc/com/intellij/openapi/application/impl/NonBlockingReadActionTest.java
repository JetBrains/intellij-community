// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.testFramework.PlatformTestUtil.waitForPromise;

/**
 * @author peter
 */
public class NonBlockingReadActionTest extends LightPlatformTestCase {

  public void testCoalesceEqual() {
    CancellablePromise<String> promise = WriteAction.compute(() -> {
      CancellablePromise<String> promise1 =
        ReadAction.nonBlocking(() -> "y").coalesceBy("foo").submit(AppExecutorUtil.getAppExecutorService());
      assertFalse(promise1.isCancelled());

      CancellablePromise<String> promise2 =
        ReadAction.nonBlocking(() -> "x").coalesceBy("foo").submit(AppExecutorUtil.getAppExecutorService());
      assertTrue(promise1.isCancelled());
      assertFalse(promise2.isCancelled());
      return promise2;
    });
    String result = waitForPromise(promise);
    assertEquals("x", result);
  }

  public void testDoNotCoalesceDifferent() {
    Pair<CancellablePromise<String>, CancellablePromise<String>> promises = WriteAction.compute(
      () -> Pair.create(ReadAction.nonBlocking(() -> "x").coalesceBy("foo").submit(AppExecutorUtil.getAppExecutorService()),
                        ReadAction.nonBlocking(() -> "y").coalesceBy("bar").submit(AppExecutorUtil.getAppExecutorService())));
    assertEquals("x", waitForPromise(promises.first));
    assertEquals("y", waitForPromise(promises.second));
  }

  public void testDoNotBlockExecutorThreadWhileWaitingForEdtFinish() throws Exception {
    Semaphore semaphore = new Semaphore(1);
    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(getName());
    CancellablePromise<Void> promise = ReadAction
      .nonBlocking(() -> {})
      .finishOnUiThread(ModalityState.defaultModalityState(), __ -> semaphore.up())
      .submit(executor);
    assertFalse(semaphore.isUp());
    executor.submit(() -> {}).get(10, TimeUnit.SECONDS); // shouldn't fail by timeout
    waitForPromise(promise);
  }

  public void testStopExecutionWhenOuterProgressIndicatorStopped() {
    ProgressIndicator outerIndicator = new EmptyProgressIndicator();
    CancellablePromise<Object> promise = ReadAction
      .nonBlocking(() -> {
        //noinspection InfiniteLoopStatement
        while (true) {
          ProgressManager.getInstance().getProgressIndicator().checkCanceled();
        }
      })
      .cancelWith(outerIndicator)
      .submit(AppExecutorUtil.getAppExecutorService());
    outerIndicator.cancel();
    waitForPromise(promise);
  }

  public void testDoNotSpawnZillionThreadsForManyCoalescedSubmissions() {
    int count = 1000;

    AtomicInteger executionCount = new AtomicInteger();
    Executor countingExecutor = r -> AppExecutorUtil.getAppExecutorService().execute(() -> {
      executionCount.incrementAndGet();
      r.run();
    });

    List<CancellablePromise<?>> submissions = new ArrayList<>();
    WriteAction.run(() -> {
      for (int i = 0; i < count; i++) {
        submissions.add(ReadAction.nonBlocking(() -> {}).coalesceBy(this).submit(countingExecutor));
      }
    });
    for (CancellablePromise<?> submission : submissions) {
      waitForPromise(submission);
    }

    assertTrue(executionCount.toString(), executionCount.get() <= 2);
  }
}
