// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.concurrency.JobLauncher;
import com.intellij.testFramework.LightPlatformTestCase;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueryTest extends LightPlatformTestCase {
  public void testForEachIsThreadSafeByDefault() {
    List<String> src = IntStreamEx.range(0, 100).mapToObj(String::valueOf).toList();
    AtomicBoolean processing = new AtomicBoolean();
    parallelQuery(src).forEach(__ -> {
      assertTrue(processing.compareAndSet(false, true));
      TimeoutUtil.sleep(1);
      processing.set(false);
      return true;
    });
  }

  public void testForEachIsParallelWhenAllowed() {
    int cores = Runtime.getRuntime().availableProcessors();
    assertTrue(cores > 1);
    int eachSleep = 200;
    long start = System.currentTimeMillis();
    parallelQuery(Arrays.asList("a", "b")).allowParallelProcessing().forEach(__ -> {
      TimeoutUtil.sleep(eachSleep);
      return true;
    });
    long elapsed = System.currentTimeMillis() - start;
    assertTrue("elapsed " + elapsed + " on " + cores + " cores", elapsed < eachSleep * 2);
  }

  private static AbstractQuery<String> parallelQuery(List<String> strings) {
    return new AbstractQuery<>() {
      @Override
      protected boolean processResults(@NotNull Processor<? super String> consumer) {
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(strings, null, consumer);
        return false;
      }
    };
  }
}
