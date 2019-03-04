// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.TimeoutUtil;
import junit.framework.TestCase;
import one.util.streamex.IntStreamEx;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author peter
 */
public class CachedValuesTest extends LightPlatformTestCase {
  private final UserDataHolderBase holder = new UserDataHolderBase();

  public void testCachedValueCapturingInvalidStuff() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    SimpleModificationTracker dependency = new SimpleModificationTracker();
    Function<String, String> getCached = arg ->
      CachedValuesManager.getManager(getProject()).getCachedValue(holder, () ->
        CachedValueProvider.Result.create("result " + arg, dependency));

    assertEquals("result foo", getCached.apply("foo"));

    dependency.incModificationCount();
    assertEquals("result foo", getCached.apply("foo"));

    dependency.incModificationCount();
    try {
      getCached.apply("bar");
      TestCase.fail();
    }
    catch (AssertionError e) {
      String message = e.getMessage();
      assertTrue(message, message.contains("Incorrect CachedValue use"));
      assertTrue(message, message.contains("foo"));
      assertTrue(message, message.contains("bar"));
    }
  }

  public void testCalculateValueAtMostOncePerThread() throws ExecutionException, InterruptedException {
    AtomicInteger calcCount = new AtomicInteger();
    SimpleModificationTracker dependency = new SimpleModificationTracker();
    Supplier<String> getCached = () -> CachedValuesManager.getManager(getProject()).getCachedValue(holder, () -> {
      calcCount.incrementAndGet();
      TimeoutUtil.sleep(10);
      return CachedValueProvider.Result.create("result", dependency);
    });
    assertEquals("result", getCached.get());
    assertEquals(1, calcCount.getAndSet(0));

    for (int r = 0; r < 1000; r++) {
      //      System.out.println("r = " + r)

      calcCount.set(0);
      dependency.incModificationCount();

      List<? extends Future<?>> jobs = IntStreamEx.range(0, 4).mapToObj(__ -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (int i = 0; i < 10; i++) {
          assertEquals("result", getCached.get());
        }
      })).toList();
      for (Future j : jobs) {
        j.get();
      }

      assertTrue(calcCount.get() <= jobs.size());
    }
  }
}
