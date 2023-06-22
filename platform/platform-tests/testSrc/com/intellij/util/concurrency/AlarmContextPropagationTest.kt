// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Alarm

class AlarmContextPropagationTest : BasePlatformTestCase() {

  fun testAlarm() = runWithContextPropagationEnabled {
    timeoutRunBlocking {
      doPropagationTest {
        val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, testRootDisposable)
        alarm.addRequest(it, 100)
      }
    }
  }
}