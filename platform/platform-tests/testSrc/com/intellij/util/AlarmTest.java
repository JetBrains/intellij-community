 // Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

 import com.intellij.diagnostic.PerformanceWatcher;
 import com.intellij.openapi.application.ApplicationManager;
 import com.intellij.openapi.application.ModalityState;
 import com.intellij.openapi.application.impl.LaterInvocator;
 import com.intellij.testFramework.LightPlatformTestCase;
 import com.intellij.testFramework.LoggedErrorProcessor;
 import com.intellij.util.containers.ContainerUtil;
 import com.intellij.util.ui.UIUtil;
 import org.jetbrains.annotations.NotNull;

 import java.util.List;
 import java.util.Set;
 import java.util.concurrent.ExecutionException;
 import java.util.concurrent.Future;
 import java.util.concurrent.TimeUnit;
 import java.util.concurrent.TimeoutException;
 import java.util.concurrent.atomic.AtomicInteger;
 import java.util.stream.Collectors;
 import java.util.stream.Stream;

 import static org.assertj.core.api.Assertions.assertThat;

 public class AlarmTest extends LightPlatformTestCase {
  public void testTwoAddsWithZeroDelayMustExecuteSequentially() throws Exception {
    Alarm alarm = new Alarm(getTestRootDisposable());
    assertRequestsExecuteSequentially(alarm);
  }

  public void testAlarmRequestsShouldExecuteSequentiallyEvenInPooledThread() throws Exception {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
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
    assertThat(alarm.isEmpty()).isTrue();
    assertEquals(expected.toString(), log.toString());
  }

  public void testOneAlarmDoesNotStartTooManyThreads() {
    Alarm alarm = new Alarm(getTestRootDisposable());
    AtomicInteger executed = new AtomicInteger();
    int N = 100000;
    Set<Thread> used = ContainerUtil.newConcurrentSet();
    for (int i = 0; i < N; i++) {
      alarm.addRequest(() -> {
        executed.incrementAndGet();
        used.add(Thread.currentThread());
      }, 10);
    }
    while (executed.get() != N) {
      UIUtil.dispatchAllInvocationEvents();
    }
    if (used.size() > 10) {
      fail(used.size()+" threads created: "+used.stream().map(t->PerformanceWatcher.printStacktrace("", t, t.getStackTrace())).collect(Collectors.joining()));
    }
  }

  public void testManyAlarmsDoNotStartTooManyThreads() {
    Set<Thread> used = ContainerUtil.newConcurrentSet();
    AtomicInteger executed = new AtomicInteger();
    int N = 100000;
    List<Alarm> alarms = Stream.generate(() -> new Alarm(getTestRootDisposable())).limit(N).toList();
    alarms.forEach(alarm -> alarm.addRequest(() -> {
      executed.incrementAndGet();
      used.add(Thread.currentThread());
    }, 10));

    while (executed.get() != N) {
      UIUtil.dispatchAllInvocationEvents();
    }
    if (used.size() > 10) {
      fail(used.size()+" threads created: "+used.stream().map(t->PerformanceWatcher.printStacktrace("", t, t.getStackTrace())).collect(Collectors.joining()));
    }
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
    alarm.drainRequestsInTest();
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

  public void testExceptionDuringAlarmExecutionMustManifestItselfInTests() {
    Alarm alarm = new Alarm(getTestRootDisposable());
    Throwable error = LoggedErrorProcessor.executeAndReturnLoggedError(() -> {
      alarm.addRequest(() -> {
        throw new RuntimeException("wtf");
      }, 1);
      boolean caught = false;
      while (!alarm.isEmpty()) {
        try {
          UIUtil.dispatchAllInvocationEvents();
        }
        catch (RuntimeException e) {
          caught |= "wtf".equals(e.getMessage());
        }
      }
      assertTrue(caught);
    });
    assertEquals("wtf", error.getMessage());
  }

  public void testSingleAlarmMustRefuseToInstantiateWithWrongModality() {
    assertThrows(IllegalArgumentException.class, () -> new SingleAlarm(() -> {}, 1, null, Alarm.ThreadToUse.SWING_THREAD, null));
  }
}
