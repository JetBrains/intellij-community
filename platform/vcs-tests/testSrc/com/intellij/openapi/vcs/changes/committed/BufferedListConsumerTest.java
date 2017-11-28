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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.vcs.FileBasedTest;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.Assert;
import org.junit.Test;

import java.util.*;

public class BufferedListConsumerTest extends FileBasedTest {

  @Test
  public void testHugeWriteRead() {
    List<Thread> threads = new ArrayList<>();
    final Random random = new Random(17);
    final Set<Long> src = new HashSet<>(200);
    for (int i = 0; i < 100; i++) {
      src.add(System.currentTimeMillis());
      src.add(random.nextLong());
    }
    final List<Long> dst = new ArrayList<>();
    final BufferedListConsumer<Long> consumer = new BufferedListConsumer<>(9, items -> dst.addAll(items), 4);
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    Thread thread1 = new Thread("buffered list test") {
      @Override
      public void run() {
        for (Long aLong : src) {
          consumer.consumeOne(aLong);
        }
        consumer.flush();
        semaphore.up();
      }
    };
    thread1.start();
    threads.add(thread1);

    final long timeout = 10 * 1000;
    final long start = System.currentTimeMillis();
    while ((System.currentTimeMillis() - start) < timeout) {
      semaphore.waitFor(50);
      if (dst.size() == src.size()) break;
    }

    boolean equal = src.size() == dst.size();
    if (equal) {
      for (int i = 0; i < dst.size(); i++) {
        Long dstL = dst.get(i);
        if (! src.contains(dstL)) {
          System.out.println("i = " + i);
          equal = false;
          break;
        }
      }
    }
    if (! equal) {
      System.out.println("src: " + src.size() + ", dst: " + dst.size());
      final Function<Long, String> f = aLong -> String.valueOf(aLong);
      System.out.println("Contents: src: [" + StringUtil.join(src, f, ", ") + "}\n\n\ndst: [" +
                         StringUtil.join(dst, f, ", ") + "]\n");
    }
    Assert.assertTrue(equal);
    ConcurrencyUtil.joinAll(threads);
  }
}
