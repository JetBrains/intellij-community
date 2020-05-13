/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.concurrency;

import com.intellij.util.ConcurrencyUtil;
import junit.framework.TestCase;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SameThreadExecutorTest extends TestCase {
  public void testReallySync() throws ExecutionException, InterruptedException {
    ExecutorService service = ConcurrencyUtil.newSameThreadExecutorService();
    AtomicBoolean called = new AtomicBoolean();
    for (int i=0; i<10_000; i++) {
      called.set(false);
      Future<String> future = service.submit(() -> {
        called.set(true);
        return "x";
      });
      assertTrue(called.get());
      assertTrue(future.isDone());
      assertEquals("x", future.get());
    }
  }

  public void testShutdownLifecycle() throws InterruptedException, ExecutionException {
    ExecutorService service = ConcurrencyUtil.newSameThreadExecutorService();
    assertFalse(service.isShutdown());
    assertFalse(service.isTerminated());
    try {
      service.awaitTermination(1, TimeUnit.NANOSECONDS);
      fail("must not be able to call awaitTermination before shutdown");
    }
    catch (IllegalStateException ignored) {
    }
    service.shutdown();
    assertTrue(service.isShutdown());
    assertTrue(service.isTerminated());
    assertTrue(service.awaitTermination(1, TimeUnit.NANOSECONDS));
    try {
      service.execute(()->{});
      fail("must not be able to execute after shutdown");
    }
    catch (IllegalStateException ignored) {
    }
    try {
      service.submit(()->{}).get();
      fail("must not be able to submit after shutdown");
    }
    catch (IllegalStateException ignored) {
    }
  }
}
