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

import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ui.UIUtil;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

  private static void assertRequestsExecuteSequentially(Alarm alarm) throws InterruptedException, ExecutionException, TimeoutException {
    int N = 100000;
    StringBuffer log = new StringBuffer(N*4);
    StringBuilder expected = new StringBuilder(N * 4);

    for (int i = 0; i < N; i++) {
      final int finalI = i;
      alarm.addRequest(() -> log.append(finalI+" "), 0);
    }
    for (int i = 0; i < N; i++) {
      expected.append(i).append(" ");
    }
    alarm.waitForAllExecuted(100, TimeUnit.SECONDS);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(0, alarm.getActiveRequestCount());
    assertEquals(expected.toString(), log.toString());
  }
}