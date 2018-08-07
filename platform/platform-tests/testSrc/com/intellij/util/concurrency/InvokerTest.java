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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * @author Sergey.Malenkov
 */
public class InvokerTest {
  private static final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());
  private final Disposable parent = Disposer.newDisposable();
  @After
  public void tearDown() throws Exception {
    while (!futures.isEmpty()) {
      List<Future<?>> a = new ArrayList<>(futures);
      futures.clear();
      for (Future<?> future : a) {
        future.get(100,TimeUnit.SECONDS);
      }
    }
    Disposer.dispose(parent);
  }

  @Test
  public void testValidOnEDT() {
    testValidThread(new Invoker.EDT(parent));
  }

  @Test
  public void testValidBgPool() {
    testValidThread(new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testValidBgThread() {
    testValidThread(new Invoker.BackgroundThread(parent));
  }

  private static void testValidThread(Invoker invoker) {
    CountDownLatch latch = new CountDownLatch(1);
    test(invoker, latch, error -> countDown(latch, 0, error, "task on invalid thread", invoker::isValidThread));
  }

  @Test
  public void testInvokeLaterOnEDT() {
    testInvokeLater(new Invoker.EDT(parent));
  }

  @Test
  public void testInvokeLaterOnBgPool() {
    testInvokeLater(new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testInvokeLaterOnBgThread() {
    testInvokeLater(new Invoker.BackgroundThread(parent));
  }

  private static void testInvokeLater(Invoker invoker) {
    CountDownLatch latch = new CountDownLatch(1);
    test(invoker, latch, error -> {
      AtomicBoolean current = new AtomicBoolean(false);
      futures.add(invoker.invokeLater(() -> countDown(latch, 100, error, "task is not done before subtask", current::get)));
      current.set(true);
    });
  }

  @Test
  public void testScheduleOnEDT() {
    testSchedule(new Invoker.EDT(parent));
  }

  @Test
  public void testScheduleOnBgPool() {
    testSchedule(new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testScheduleOnBgThread() {
    testSchedule(new Invoker.BackgroundThread(parent));
  }

  private static void testSchedule(Invoker invoker) {
    CountDownLatch latch = new CountDownLatch(1);
    test(invoker, latch, error -> {
      AtomicBoolean current = new AtomicBoolean(false);
      futures.add(invoker.invokeLater(() -> countDown(latch, 0, error, "task is not done before subtask", current::get), 200));
      futures.add(invoker.invokeLater(() -> current.set(true), 100));
    });
  }

  @Test
  public void testInvokeLaterIfNeededOnEDT() {
    testInvokeLaterIfNeeded(new Invoker.EDT(parent));
  }

  @Test
  public void testInvokeLaterIfNeededOnBgPool() {
    testInvokeLaterIfNeeded(new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testInvokeLaterIfNeededOnBgThread() {
    testInvokeLaterIfNeeded(new Invoker.BackgroundThread(parent));
  }

  private static void testInvokeLaterIfNeeded(Invoker invoker) {
    CountDownLatch latch = new CountDownLatch(1);
    test(invoker, latch, error -> {
      AtomicBoolean current = new AtomicBoolean(true);
      futures.add(invoker.runOrInvokeLater(() -> countDown(latch, 100, error, "task is done before subtask", current::get)));
      current.set(false);
    });
  }

  @Test
  public void testRestartOnEDT() {
    testRestartOnPCE(new Invoker.EDT(parent));
  }

  @Test
  public void testRestartOnBgPool() {
    testRestartOnPCE(new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testRestartOnBgThread() {
    testRestartOnPCE(new Invoker.BackgroundThread(parent));
  }

  private static void testRestartOnPCE(Invoker invoker) {
    AtomicInteger value = new AtomicInteger(10);
    CountDownLatch latch = new CountDownLatch(1);
    test(invoker, latch, error -> {
      if (0 < value.decrementAndGet()) throw new ProcessCanceledException();
      latch.countDown();
    });
  }

  @Test
  public void testQueueOnEDT() {
    testQueue(new Invoker.EDT(parent), true);
  }

  @Test
  public void testQueueOnBgPool() {
    testQueue(new Invoker.BackgroundPool(parent), false);
  }

  @Test
  public void testQueueOnBgThread() {
    testQueue(new Invoker.BackgroundThread(parent), true);
  }

  private static void testQueue(Invoker invoker, boolean ordered) {
    CountDownLatch latch = new CountDownLatch(2);
    test(invoker, latch, error -> {
      long first = ordered ? 2 : 1;
      futures.add(invoker.invokeLater(() -> countDown(latch, 100, error, "unexpected task order", () -> first == latch.getCount())));
      long second = ordered ? 1 : 2;
      futures.add(invoker.invokeLater(() -> countDown(latch, 0, error, "unexpected task order", () -> second == latch.getCount())));
    });
  }

  @Test
  public void testThreadChangingOnEDT() {
    testThreadChanging(new Invoker.EDT(parent));
  }

  @Test
  public void testThreadChangingOnBgPool() {
    testThreadChanging(new Invoker.BackgroundPool(parent));
  }

  @Test
  public void testThreadChangingOnBgThread() {
    testThreadChanging(new Invoker.BackgroundThread(parent));
  }

  private static void testThreadChanging(Invoker invoker) {
    testThreadChanging(invoker, invoker, true);
  }

  @Test
  public void testThreadChangingOnEDTfromEDT() {
    testThreadChanging(new Invoker.EDT(parent), new Invoker.EDT(parent), true);
  }

  @Test
  public void testThreadChangingOnEDTfromBgPool() {
    testThreadChanging(new Invoker.EDT(parent), new Invoker.BackgroundPool(parent), false);
  }

  @Test
  public void testThreadChangingOnEDTfromBgThread() {
    testThreadChanging(new Invoker.EDT(parent), new Invoker.BackgroundThread(parent), false);
  }

  @Test
  public void testThreadChangingOnBgPoolFromEDT() {
    testThreadChanging(new Invoker.BackgroundPool(parent), new Invoker.EDT(parent), false);
  }

  @Test
  public void testThreadChangingOnBgPoolFromBgPool() {
    testThreadChanging(new Invoker.BackgroundPool(parent), new Invoker.BackgroundPool(parent), true);
  }

  @Test
  public void testThreadChangingOnBgPoolFromBgThread() {
    testThreadChanging(new Invoker.BackgroundPool(parent), new Invoker.BackgroundThread(parent), true);
  }

  @Test
  public void testThreadChangingOnBgThreadFromEDT() {
    testThreadChanging(new Invoker.BackgroundThread(parent), new Invoker.EDT(parent), false);
  }

  @Test
  public void testThreadChangingOnBgThreadFromBgPool() {
    testThreadChanging(new Invoker.BackgroundThread(parent), new Invoker.BackgroundPool(parent), null);
  }

  @Test
  public void testThreadChangingOnBgThreadFromBgThread() {
    testThreadChanging(new Invoker.BackgroundThread(parent), new Invoker.BackgroundThread(parent), null);
  }

  private static void testThreadChanging(Invoker foreground, Invoker background, Boolean equal) {
    CountDownLatch latch = new CountDownLatch(1);
    test(foreground, latch, error
      -> new Command.Processor(foreground, background).process(Thread::currentThread, thread
      -> countDown(latch, 0, error, "unexpected thread", ()
      -> isExpected(thread, equal))));
  }

  private static boolean isExpected(Thread thread, Boolean equal) {
    if (equal != null) return equal.equals(thread == Thread.currentThread());
    return true; // debug only: thread may be reused
  }


  private static void test(Invoker invoker, CountDownLatch latch, Consumer<? super AtomicReference<String>> consumer) {
    Assert.assertFalse("EDT should not be used to start this test", invoker instanceof Invoker.EDT && SwingUtilities.isEventDispatchThread());
    AtomicReference<String> error = new AtomicReference<>();
    futures.add(invoker.invokeLater(() -> consumer.accept(error)));
    String message;
    try {
      latch.await(10, TimeUnit.SECONDS);
      message = error.get();
    }
    catch (InterruptedException ignore) {
      message = "interrupted exception";
    }
    if (message != null) Assert.fail(message + " @ " + invoker);
  }

  private static void countDown(CountDownLatch latch, long ms, AtomicReference<? super String> error, String message, BooleanSupplier success) {
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
}
