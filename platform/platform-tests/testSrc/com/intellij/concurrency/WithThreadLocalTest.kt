// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WithThreadLocalTest {

  private val testThreadLocal: ThreadLocal<Any?> = ThreadLocal.withInitial {
    42
  }

  @AfterEach
  fun clear() {
    testThreadLocal.remove()
  }

  private fun test(v: Int?, nested: () -> Unit) {
    withThreadLocal(testThreadLocal) { v }.use {
      assertEquals(v, testThreadLocal.get())
      nested()
      assertEquals(v, testThreadLocal.get())
    }
  }

  @Test
  fun `set and revert value`() {
    assertEquals(42, testThreadLocal.get())
    test(43) {
      test(44) {
        test(45) {}
      }
    }
    assertEquals(42, testThreadLocal.get())
  }

  @Test
  fun `revert to null`() {
    assertEquals(42, testThreadLocal.get())
    test(43) {
      test(null) {
        test(44) {}
      }
    }
    assertEquals(42, testThreadLocal.get())
  }

  @Test
  fun `unordered revert`() {
    val revert1 = withThreadLocal(testThreadLocal) { 43 }
    val revert2 = withThreadLocal(testThreadLocal) { 44 }
    assertThrows<IllegalStateException> {
      revert1.close()
    }
    assertEquals(42, testThreadLocal.get()) // reverted to state before revert1
    assertThrows<IllegalStateException> {
      revert2.close()
    }
    assertEquals(43, testThreadLocal.get()) // reverted to state before revert2
  }
}
