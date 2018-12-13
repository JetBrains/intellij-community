// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.testFramework.vcs.FileBasedTest;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import org.junit.Test;

import java.util.*;

import static com.intellij.testFramework.UsefulTestCase.assertSameElements;
import static com.intellij.util.ConcurrencyUtil.joinAll;
import static java.util.Collections.synchronizedList;

public class BufferedListConsumerTest extends FileBasedTest {

  @Test
  public void testHugeWriteRead() throws Exception {
    final Random random = new Random(17);
    final Set<Long> src = new HashSet<>(200);
    for (int i = 0; i < 100; i++) {
      src.add(System.currentTimeMillis());
      src.add(random.nextLong());
    }
    final int sourceSize = src.size();
    final List<Long> dst = synchronizedList(new ArrayList<>());
    final Semaphore semaphore = new Semaphore();
    Consumer<List<Long>> innerConsumer = items -> {
      dst.addAll(items);
      if (sourceSize == dst.size()) {
        semaphore.up();
      }
    };
    final BufferedListConsumer<Long> consumer = new BufferedListConsumer<>(9, innerConsumer, 4);
    Thread thread = new Thread("buffered list test") {
      @Override
      public void run() {
        for (Long aLong : src) {
          consumer.consumeOne(aLong);
        }
        consumer.flush();
      }
    };
    semaphore.down();
    thread.start();

    int timeout = 10 * 1000;
    try {
      if (!semaphore.waitFor(timeout)) throw new Exception("Couldn't await background thread");

      // BufferedListConsumer calls inner consumer in some background thread. This thread could be not the same between calls, which could
      // lead to different elements order in dst and src.
      assertSameElements(dst, src);
    }
    finally {
      joinAll(thread);
    }
  }
}
