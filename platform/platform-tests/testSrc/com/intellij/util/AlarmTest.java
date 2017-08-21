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

 import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AlarmTest extends PlatformTestCase {
  public void testTwoAddsWithZeroDelayMustExecuteSequentially() throws Exception {
    Alarm alarm = new Alarm(getTestRootDisposable());
    assertRequestsExecuteSequentially(alarm);
  }

  public void testAlarmRequestsShouldExecuteSequentiallyEvenInPooledThread() throws Exception {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
    assertRequestsExecuteSequentially(alarm);
  }

  public void testAlarmRequestsShouldExecuteSequentiallyEveryWhere() throws Exception {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, getTestRootDisposable());
    assertRequestsExecuteSequentially(alarm);
  }

  public void testAlarmRequestsShouldExecuteSequentiallyAbsolutelyEveryWhere() throws Exception {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, getTestRootDisposable());
    assertRequestsExecuteSequentially(alarm);
  }

  private static void assertRequestsExecuteSequentially(@NotNull Alarm alarm) throws InterruptedException, ExecutionException {
    int N = 10000;
    StringBuffer log = new StringBuffer(N*4);
    StringBuilder expected = new StringBuilder(N * 4);

    for (int i = 0; i < N; i++) {
      final int finalI = i;
      alarm.addRequest(() -> log.append(finalI).append(" "), 0);
    }
    for (int i = 0; i < N; i++) {
      expected.append(i).append(" ");
    }
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        alarm.waitForAllExecuted(100, TimeUnit.SECONDS);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    while (!future.isDone()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    future.get();
    assertEquals(0, alarm.getActiveRequestCount());
    assertEquals(expected.toString(), log.toString());
  }

  public void testOneAlarmDoesNotStartTooManyThreads() {
    Alarm alarm = new Alarm(getTestRootDisposable());
    Map<Thread, StackTraceElement[]> before = Thread.getAllStackTraces();
    AtomicInteger executed = new AtomicInteger();
    int N = 100000;
    for (int i = 0; i < N; i++) {
      alarm.addRequest(executed::incrementAndGet, 10);
    }
    while (executed.get() != N) {
      UIUtil.dispatchAllInvocationEvents();
    }
    Map<Thread, StackTraceElement[]> after = Thread.getAllStackTraces();
    assertTrue("before: "+before.size()+"; after: "+after.size(), after.size() - before.size() < 10);
  }

  public void testManyAlarmsDoNotStartTooManyThreads() {
    Map<Thread, StackTraceElement[]> before = Thread.getAllStackTraces();
    AtomicInteger executed = new AtomicInteger();
    int N = 100000;
    List<Alarm> alarms = Collections.nCopies(N, "").stream().map(__ -> new Alarm(getTestRootDisposable())).collect(Collectors.toList());
    alarms.forEach(alarm -> alarm.addRequest(executed::incrementAndGet, 10));

    while (executed.get() != N) {
      UIUtil.dispatchAllInvocationEvents();
    }
    Map<Thread, StackTraceElement[]> after = Thread.getAllStackTraces();
    LOG.debug("before: "+before.size()+"; after: "+after.size());
    assertTrue(after.size() - before.size() < 10);
  }

  public void testOrderIsPreservedAfterModalitySwitching() {
    Alarm alarm = new Alarm();
    StringBuilder sb = new StringBuilder();
    Object modal = new Object();
    LaterInvocator.enterModal(modal);

    try {
      ApplicationManager.getApplication().invokeLater(() -> TimeoutUtil.sleep(10), ModalityState.NON_MODAL);
      alarm.addRequest(() -> sb.append("1"), 0, ModalityState.NON_MODAL);
      alarm.addRequest(() -> sb.append("2"), 5, ModalityState.NON_MODAL);
      UIUtil.dispatchAllInvocationEvents();
      assertEquals("", sb.toString());
    }
    finally {
      LaterInvocator.leaveModal(modal);
    }

    while (!alarm.isEmpty()) {
      UIUtil.dispatchAllInvocationEvents();
    }

    assertEquals("12", sb.toString());
  }

  public void testFlushImmediately() {
    Alarm alarm = new Alarm();
    StringBuilder sb = new StringBuilder();

    alarm.addRequest(() -> sb.append("1"), 0, ModalityState.NON_MODAL);
    alarm.addRequest(() -> sb.append("2"), 5, ModalityState.NON_MODAL);
    assertEquals("", sb.toString());
    alarm.flush();
    assertEquals("12", sb.toString());
  }

  public void testWaitForAllExecutedMustWaitUntilExecutionFinish() throws Exception {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
    StringBuffer sb = new StringBuffer();
    long start = System.currentTimeMillis();
    int delay = 100;
    alarm.addRequest(() -> {
      TimeoutUtil.sleep(1000);
      sb.append("1");
    }, delay);
    alarm.addRequest(() -> {
      TimeoutUtil.sleep(1000);
      sb.append("2");
    }, delay*2);

    String s = sb.toString();
    long elapsed = System.currentTimeMillis() - start;
    if (elapsed > delay/2) {
      System.err.println("No no no no this agent is so overloaded I quit");
      return;
    }
    assertEquals(2, alarm.getActiveRequestCount());
    assertEquals("", s);
    try {
      // started to execute but not finished yet
      alarm.waitForAllExecuted(1000, TimeUnit.MILLISECONDS);
      fail();
    }
    catch (TimeoutException ignored) {
    }

    alarm.waitForAllExecuted(3000, TimeUnit.MILLISECONDS);

    assertEquals(2, sb.length());
  }
}