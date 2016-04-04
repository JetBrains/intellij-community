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
package com.intellij.util;

import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SimpleTimerTask;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SimpleTimerTest extends TestCase {
  public void testRequestsForTheSameTimeAreExecutedSequentially() throws InterruptedException {
    SimpleTimer timer = SimpleTimer.newInstance("test timer");
    int N = 10000;
    StringBuffer LOG = new StringBuffer(N*5);
    StringBuffer expected = new StringBuffer(N*5);
    CountDownLatch executed = new CountDownLatch(N);

    List<SimpleTimerTask> tasks = new ArrayList<>(N);
    for (int i = 0; i < N; i++) {
      final int finalI = i;
      expected.append(finalI + ", ");
      SimpleTimerTask task = timer.setUp(new Runnable() {
        @Override
        public void run() {
          LOG.append(finalI + ", ");
          executed.countDown();
        }

        @Override
        public String toString() {
          return String.valueOf(finalI);
        }
      }, 500);
      tasks.add(task);
    }
    boolean completed = executed.await(5000, TimeUnit.MILLISECONDS);
    assertEquals(tasks.toString(), expected.toString(), LOG.toString());
    assertTrue(completed);
  }
}
