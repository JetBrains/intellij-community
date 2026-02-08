// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class RegistryValueFlowTest {

  @Test
  fun `asStringFlow emits on changes and is distinctUntilChanged`() = timeoutRunBlocking {
    val key = "registry.flow.test.string"
    val rv = Registry.get(key)
    try {
      val emissions = mutableListOf<String>()

      val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        rv.asStringFlow().take(2).toList(emissions)
      }

      // Two unique values, with a duplicate in-between
      rv.setValue("a")
      yield()
      rv.setValue("a")
      yield()
      rv.setValue("b")
      yield()

      collector.join()
      Assertions.assertEquals(listOf("a", "b"), emissions)
    }
    finally {
      rv.resetToDefault()
    }
  }

  @Test
  fun `asBooleanFlow maps values and is distinctUntilChanged`() = timeoutRunBlocking {
    val key = "registry.flow.test.boolean"
    val rv = Registry.get(key)
    try {
      val emissions = mutableListOf<Boolean>()
      val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        rv.asBooleanFlow().take(2).toList(emissions)
      }

      rv.setValue("true")
      yield()
      rv.setValue("true")
      yield()
      rv.setValue("false")
      yield()

      collector.join()
      Assertions.assertEquals(listOf(true, false), emissions)
    }
    finally {
      rv.resetToDefault()
    }
  }

  @Test
  fun `asIntegerFlow emits mapped integers and throws on invalid integer`() = timeoutRunBlocking {
    val key = "registry.flow.test.int"
    val rv = Registry.get(key)
    try {
      var thrown: Throwable? = null
      supervisorScope {
        try {
          val job = async(start = CoroutineStart.UNDISPATCHED) {
            rv.asIntegerFlow().collect { value ->
              // After receiving a valid value, push an invalid one to cause failure
              if (value == 5) {
                rv.setValue("oops")
              }
            }
          }
          // Start emissions
          rv.setValue(5)
          job.await()
        }
        catch (t: Throwable) {
          thrown = unwrapCancellation(t)
        }
      }
      Assertions.assertTrue(thrown is NumberFormatException, "Expected NumberFormatException, but was: $thrown")
    }
    finally {
      rv.resetToDefault()
    }
  }

  @Test
  fun `asDoubleFlow emits mapped doubles and throws on invalid double`() = timeoutRunBlocking {
    val key = "registry.flow.test.double"
    val rv = Registry.get(key)
    try {
      var thrown: Throwable? = null
      supervisorScope {
        try {
          val job = async(start = CoroutineStart.UNDISPATCHED) {
            rv.asDoubleFlow().collect { value ->
              if (value == 3.14) {
                rv.setValue("not_a_double")
              }
            }
          }
          rv.setValue("3.14")
          job.await()
        }
        catch (t: Throwable) {
          thrown = unwrapCancellation(t)
        }
      }
      Assertions.assertTrue(thrown is NumberFormatException, "Expected NumberFormatException, but was: ${'$'}thrown")
    }
    finally {
      rv.resetToDefault()
    }
  }

  private fun unwrapCancellation(t: Throwable): Throwable {
    // In coroutine tests, exceptions from child coroutines could be wrapped in CancellationException
    return if (t is CancellationException && t.cause != null) t.cause!! else t
  }
}
