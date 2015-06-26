/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.messages.impl;

import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 26/06/15 15:49
 */
public class MessageBusImplTest {

  @Test
  public void stress() throws Throwable {
    final int threadsNumber = 10;
    final int iterationsNumber = 100;
    final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
    final CountDownLatch latch = new CountDownLatch(threadsNumber);
    final MessageBus parentBus = MessageBusFactory.newMessageBus("parent");
    for (int i = 0; i < threadsNumber; i++) {
      new Thread(String.valueOf(i)) {
        @Override
        public void run() {
          int remains = iterationsNumber;
          try {
            while (remains-- > 0) {
              //noinspection ThrowableResultOfMethodCallIgnored
              if (exception.get() != null) {
                break;
              }
              new MessageBusImpl(String.format("child-%s-%s", Thread.currentThread().getName(), remains), parentBus);
            }
          }
          catch (Throwable e) {
            exception.set(e);
          }
          finally {
            latch.countDown();
          }
        }
      }.start();
    }
    latch.await();
    final Throwable e = exception.get();
    if (e != null) {
      throw e;
    }
  }
}
