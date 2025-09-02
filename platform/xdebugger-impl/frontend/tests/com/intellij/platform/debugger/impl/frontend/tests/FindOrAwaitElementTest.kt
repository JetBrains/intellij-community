// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.tests

import com.intellij.openapi.util.Ref
import com.intellij.platform.debugger.impl.frontend.findOrAwaitElement
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FindOrAwaitElementTest {
  @Test
  fun testFindOrAwaitElement() = runBlocking {
    repeat(1_000) {
      doTest(withDelayInCreation = false)
    }
  }

  @Test
  fun testFindOrAwaitElementWithDelay() = runBlocking {
    repeat(1_000) {
      doTest(withDelayInCreation = true)
    }
  }

  private suspend fun doTest(withDelayInCreation: Boolean) {
    val flow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val storage = ConcurrentHashMap<Int, String>()
    val elements = 10
    coroutineScope {
      repeat(elements) { i ->
        launch {
          if (withDelayInCreation) {
            yield()
          }
          storage[i] = "i = $i"
          flow.tryEmit(Unit)
        }
        val element = findOrAwaitElement(flow, logMessage = "$i", timeoutS = 3) {
          val value = storage[i]
          if (value != null) Ref.create(value) else null
        }
        assertEquals("i = $i", element)
      }
    }
  }
}
