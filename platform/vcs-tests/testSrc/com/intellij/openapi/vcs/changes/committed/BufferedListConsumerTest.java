/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.vcs.FileBasedTest;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BufferedListConsumerTest extends FileBasedTest {

  @Test
  public void testHugeWriteRead() throws Exception {
    Ref<Future<?>> futureRef = new Ref<>();

    final Random random = new Random(17);
    final Set<Long> src = new HashSet<>(200);
    for (int i = 0; i < 100; i++) {
      src.add(System.currentTimeMillis());
      src.add(random.nextLong());
    }
    final List<Long> dst = new ArrayList<>();
    final BufferedListConsumer<Long> consumer = new BufferedListConsumer<Long>(9, items -> dst.addAll(items), 4) {
      @Override
      protected void invokeConsumer(@NotNull Runnable consumerRunnable) {
        futureRef.set(ApplicationManager.getApplication().executeOnPooledThread(consumerRunnable));
      }
    };
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    Thread thread = new Thread("buffered list test") {
      @Override
      public void run() {
        for (Long aLong : src) {
          consumer.consumeOne(aLong);
        }
        consumer.flush();
        semaphore.up();
      }
    };
    thread.start();

    int timeout = 10 * 1000;
    try {
      if (!semaphore.waitFor(timeout)) throw new Exception("Couldn't await background thread");

      Future<?> future = futureRef.get();
      if (future != null) future.get(timeout, TimeUnit.MILLISECONDS);

      UsefulTestCase.assertOrderedEquals(dst, src);
    }
    finally {
      ConcurrencyUtil.joinAll(thread);
    }
  }
}
