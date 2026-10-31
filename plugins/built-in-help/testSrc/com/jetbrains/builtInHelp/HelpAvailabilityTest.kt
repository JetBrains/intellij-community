// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class HelpAvailabilityTest {
  @Test
  fun `availability expires and uses the actual destination`(): Unit = timeoutRunBlocking {
    var now = 0L
    var online = false
    val requested = mutableListOf<String>()
    val availability = HelpAvailability(probe = { requested.add(it); online }, nanoTime = { now })
    val url = "https://docs.example.com/idea/263/?editor.settings"
    assertFalse(availability.isAvailable(url))
    online = true
    assertFalse(availability.isAvailable(url))
    assertEquals(listOf(url), requested)
    now = TimeUnit.SECONDS.toNanos(31)
    assertTrue(availability.isAvailable(url))
    assertEquals(listOf(url, url), requested)
  }
}
