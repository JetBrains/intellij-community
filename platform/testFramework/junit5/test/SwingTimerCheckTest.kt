// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.common.checkJavaSwingTimersAreDisposed
import org.assertj.core.api.Assertions.assertThat
import org.cef.CefAppPumpStub
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.swing.Timer

internal class SwingTimerCheckTest {
  @Test
  fun `a started timer is reported and stopped`() {
    val timer = Timer(60_000) {}
    timer.start()
    try {
      val error = assertThrows<AssertionError> { checkJavaSwingTimersAreDisposed() }
      assertThat(error).hasMessageStartingWith("Not disposed javax.swing.Timer: Timer (listeners: [")
      assertThat(timer.isRunning).isFalse()
    }
    finally {
      timer.stop()
    }
  }

  @Test
  fun `the message pump of JCEF is skipped and keeps running, and a timer after it is reported`() {
    val pump = Timer(30_000, CefAppPumpStub())
    pump.start()
    try {
      checkJavaSwingTimersAreDisposed()
      assertThat(pump.isRunning).describedAs("the pump alone is no leak").isTrue()

      val leaked = Timer(60_000) {}
      leaked.start()
      try {
        assertThrows<AssertionError> { checkJavaSwingTimersAreDisposed() }
        assertThat(leaked.isRunning).describedAs("the timer after the pump in the queue").isFalse()
        assertThat(pump.isRunning).describedAs("the pump").isTrue()
      }
      finally {
        leaked.stop()
      }
    }
    finally {
      pump.stop()
    }
  }
}
