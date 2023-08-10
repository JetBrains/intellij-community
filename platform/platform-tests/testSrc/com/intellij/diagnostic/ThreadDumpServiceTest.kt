// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.testFramework.ApplicationRule
import com.intellij.util.ui.EDT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThreadDumpServiceTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Test
  fun dumpSpecificThread() {
    val cookie = ThreadDumpService.getInstance().start(0, 50, 3, EDT.getEventDispatchThread())
    Thread.sleep(500)
    cookie.close()
    assertEquals(3, cookie.traces.size)
    for (throwable in cookie.traces) {
      assertTrue(throwable.stackTraceToString(), throwable.stackTraceToString().contains("EventDispatchThread"))
    }
  }
}