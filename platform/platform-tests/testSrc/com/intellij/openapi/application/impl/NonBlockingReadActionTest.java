// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.concurrency.CancellablePromise;

/**
 * @author peter
 */
public class NonBlockingReadActionTest extends HeavyPlatformTestCase {

  public void testCancelPrevious_SameClass_SameIdentity() throws Exception {
    CancellablePromise<String> promise = WriteAction.compute(() -> {
      CancellablePromise<String> promise1 =
        ReadAction.nonBlocking(() -> "y").cancelPrevious("foo").submit(AppExecutorUtil.getAppExecutorService());
      assertFalse(promise1.isCancelled());

      CancellablePromise<String> promise2 =
        ReadAction.nonBlocking(() -> "x").cancelPrevious("foo").submit(AppExecutorUtil.getAppExecutorService());
      assertTrue(promise1.isCancelled());
      assertFalse(promise2.isCancelled());
      return promise2;
    });
    String result = getResult(promise);
    assertEquals("x", result);
  }

  public void testCancelPrevious_SameClass_DifferentIdentities() throws Exception {
    Pair<CancellablePromise<String>, CancellablePromise<String>> promises = WriteAction.compute(
      () -> Pair.create(ReadAction.nonBlocking(() -> "x").cancelPrevious("foo").submit(AppExecutorUtil.getAppExecutorService()),
                        ReadAction.nonBlocking(() -> "y").cancelPrevious("bar").submit(AppExecutorUtil.getAppExecutorService())));
    assertEquals("x", getResult(promises.first));
    assertEquals("y", getResult(promises.second));
  }

  public void testCancelPrevious_DifferentClasses_SameIdentities() throws Exception {
    Pair<CancellablePromise<String>, CancellablePromise<String>> promises = WriteAction.compute(() -> {
      class Inner {
        CancellablePromise<String> launch() {
          return ReadAction.nonBlocking(() -> "x").cancelPrevious("foo").submit(AppExecutorUtil.getAppExecutorService());
        }
      }

      return Pair.create(new Inner().launch(),
                         ReadAction.nonBlocking(() -> "y").cancelPrevious("foo").submit(AppExecutorUtil.getAppExecutorService()));
    });
    assertEquals("x", getResult(promises.first));
    assertEquals("y", getResult(promises.second));
  }

  private static String getResult(CancellablePromise<String> promise) throws InterruptedException, java.util.concurrent.ExecutionException {
    while (!promise.isDone()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    return promise.get();
  }
}
