// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.Disposable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.Alarm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@TestApplication
@ExtendWith(ThreadContextPropagationTest.Enabler::class)
class AlarmContextPropagationTest {

  @Test
  fun `alarm request`(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    timeoutRunBlocking {
      doPropagationTest {
        alarm.addRequest(it, 100)
      }
    }
  }
}
