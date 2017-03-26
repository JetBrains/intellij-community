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
package com.intellij.util.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static javax.swing.SwingUtilities.isEventDispatchThread;

/**
 * @author Sergey.Malenkov
 */
public class InvokerTest {
  @Test
  public void testValidOnEDT() {
    Disposable parent = InvokerTest::dispose;
    testValidThread(parent, new Invoker.EDT(parent));
  }

  @Test
  public void testValidBgPool() {
    Disposable parent = InvokerTest::dispose;
    testValidThread(parent, new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testValidBgThread() {
    Disposable parent = InvokerTest::dispose;
    testValidThread(parent, new Invoker.BackgroundThread(parent));
  }

  private static void testValidThread(Disposable parent, Invoker invoker) {
    CountDownLatch latch = new CountDownLatch(1);
    test(parent, invoker, latch, error -> countDown(latch, 0, error, "task on invalid thread", invoker::isValidThread));
  }

  @Test
  public void testInvokeLaterOnEDT() {
    Disposable parent = InvokerTest::dispose;
    testInvokeLater(parent, new Invoker.EDT(parent));
  }

  @Test
  public void testInvokeLaterOnBgPool() {
    Disposable parent = InvokerTest::dispose;
    testInvokeLater(parent, new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testInvokeLaterOnBgThread() {
    Disposable parent = InvokerTest::dispose;
    testInvokeLater(parent, new Invoker.BackgroundThread(parent));
  }

  private static void testInvokeLater(Disposable parent, Invoker invoker) {
    CountDownLatch latch = new CountDownLatch(1);
    test(parent, invoker, latch, error -> {
      AtomicBoolean current = new AtomicBoolean(false);
      invoker.invokeLater(() -> countDown(latch, 100, error, "task is not done before subtask", current::get));
      current.set(true);
    });
  }

  @Test
  public void testInvokeLaterIfNeededOnEDT() {
    Disposable parent = InvokerTest::dispose;
    testInvokeLaterIfNeeded(parent, new Invoker.EDT(parent));
  }

  @Test
  public void testInvokeLaterIfNeededOnBgPool() {
    Disposable parent = InvokerTest::dispose;
    testInvokeLaterIfNeeded(parent, new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testInvokeLaterIfNeededOnBgThread() {
    Disposable parent = InvokerTest::dispose;
    testInvokeLaterIfNeeded(parent, new Invoker.BackgroundThread(parent));
  }

  private static void testInvokeLaterIfNeeded(Disposable parent, Invoker invoker) {
    CountDownLatch latch = new CountDownLatch(1);
    test(parent, invoker, latch, error -> {
      AtomicBoolean current = new AtomicBoolean(true);
      invoker.invokeLaterIfNeeded(() -> countDown(latch, 100, error, "task is done before subtask", current::get));
      current.set(false);
    });
  }

  @Test
  public void testQueueOnEDT() {
    Disposable parent = InvokerTest::dispose;
    testQueue(parent, new Invoker.EDT(parent), true);
  }

  @Test
  public void testQueueOnBgPool() {
    Disposable parent = InvokerTest::dispose;
    testQueue(parent, new Invoker.BackgroundPool(parent), false);
  }

  @Test
  public void testQueueOnBgThread() {
    Disposable parent = InvokerTest::dispose;
    testQueue(parent, new Invoker.BackgroundThread(parent), true);
  }

  private static void testQueue(Disposable parent, Invoker invoker, boolean ordered) {
    CountDownLatch latch = new CountDownLatch(2);
    test(parent, invoker, latch, error -> {
      long first = ordered ? 2 : 1;
      invoker.invokeLater(() -> countDown(latch, 100, error, "unexpected task order", () -> first == latch.getCount()));
      long second = ordered ? 1 : 2;
      invoker.invokeLater(() -> countDown(latch, 0, error, "unexpected task order", () -> second == latch.getCount()));
    });
  }

  @Test
  public void testThreadChangingOnEDT() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.EDT(parent));
  }

  @Test
  public void testThreadChangingOnBgPool() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testThreadChangingOnBgThread() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.BackgroundThread(parent));
  }

  private static void testThreadChanging(Disposable parent, Invoker invoker) {
    testThreadChanging(parent, invoker, invoker, true);
  }

  @Test
  public void testThreadChangingOnEDTfromEDT() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.EDT(parent), new Invoker.EDT(parent), true);
  }

  @Test
  public void testThreadChangingOnEDTfromBgPool() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.EDT(parent), new Invoker.BackgroundPool(parent), false);
  }

  @Test
  public void testThreadChangingOnEDTfromBgThread() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.EDT(parent), new Invoker.BackgroundThread(parent), false);
  }

  @Test
  public void testThreadChangingOnBgPoolFromEDT() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.BackgroundPool(parent), new Invoker.EDT(parent), false);
  }

  @Test
  public void testThreadChangingOnBgPoolFromBgPool() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.BackgroundPool(parent), new Invoker.BackgroundPool(parent), true);
  }

  @Test
  public void testThreadChangingOnBgPoolFromBgThread() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.BackgroundPool(parent), new Invoker.BackgroundThread(parent), true);
  }

  @Test
  public void testThreadChangingOnBgThreadFromEDT() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.BackgroundThread(parent), new Invoker.EDT(parent), false);
  }

  @Test
  public void testThreadChangingOnBgThreadFromBgPool() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.BackgroundThread(parent), new Invoker.BackgroundPool(parent), null);
  }

  @Test
  public void testThreadChangingOnBgThreadFromBgThread() {
    Disposable parent = InvokerTest::dispose;
    testThreadChanging(parent, new Invoker.BackgroundThread(parent), new Invoker.BackgroundThread(parent), null);
  }

  private static void testThreadChanging(Disposable parent, Invoker foreground, Invoker background, Boolean equal) {
    CountDownLatch latch = new CountDownLatch(1);
    test(parent, foreground, latch, error
      -> new Command.Processor(foreground, background).process(Thread::currentThread, thread
      -> countDown(latch, 0, error, "unexpected thread", ()
      -> isExpected(thread, equal))));
  }

  private static boolean isExpected(Thread thread, Boolean equal) {
    if (equal != null) return equal.equals(thread == Thread.currentThread());
    return true; // debug only: thread may be reused
  }


  private static void test(Disposable parent, Invoker invoker, CountDownLatch latch, Consumer<AtomicReference<String>> consumer) {
    Assert.assertFalse("EDT should not be used to start this test", invoker instanceof Invoker.EDT && isEventDispatchThread());
    AtomicReference<String> error = new AtomicReference<>();
    invoker.invokeLater(() -> consumer.accept(error));
    String message;
    try {
      latch.await();
      message = error.get();
    }
    catch (InterruptedException ignore) {
      message = "interrupted exception";
    }
    finally {
      Disposer.dispose(parent);
    }
    if (message != null) Assert.fail(message + " @ " + invoker);
  }

  private static void countDown(CountDownLatch latch, long ms, AtomicReference<String> error, String message, BooleanSupplier success) {
    try {
      if (ms > 0) Thread.sleep(ms);
    }
    catch (InterruptedException ignore) {
    }
    finally {
      if (!success.getAsBoolean()) error.set(message);
      latch.countDown();
    }
  }

  private static void dispose() {
  }
}
