// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.MissingResourceException

@OptIn(ExperimentalCoroutinesApi::class)
@TestApplication
class RegistryValueFlowTest {

  @Test
  fun `asStringFlow emits current value and changes and is distinctUntilChanged`() = runTest {
    val key = "registry.flow.test.string"
    val rv = Registry.get(key)
    try {
      rv.setValue("initial")
      val emissions = mutableListOf<String>()

      val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        rv.asStringFlow().collect { emissions.add(it) }
      }

      // Two unique values, with a duplicate in-between
      rv.setValue("a")
      yield()
      rv.setValue("a")
      yield()
      rv.setValue("b")
      yield()

      advanceUntilIdle()
      Assertions.assertEquals(listOf("initial", "a", "b"), emissions)
      collector.cancelAndJoin()
    }
    finally {
      rv.resetToDefault()
    }
  }

  @Test
  fun `asBooleanFlow emits current value and maps changes and is distinctUntilChanged`() = runTest {
    val key = "registry.flow.test.boolean"
    val rv = Registry.get(key)
    try {
      rv.setValue("false")
      val emissions = mutableListOf<Boolean>()
      val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        rv.asBooleanFlow().collect { emissions.add(it) }
      }

      rv.setValue("true")
      yield()
      rv.setValue("true")
      yield()
      rv.setValue("false")
      yield()

      advanceUntilIdle()
      Assertions.assertEquals(listOf(false, true, false), emissions)
      collector.cancelAndJoin()
    }
    finally {
      rv.resetToDefault()
    }
  }

  @Test
  fun `asIntegerFlow emits mapped integers and throws on invalid integer`() = runTest {
    val key = "registry.flow.test.int"
    val rv = Registry.get(key)
    try {
      rv.setValue(0)
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
  fun `asDoubleFlow emits mapped doubles and throws on invalid double`() = runTest {
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
      Assertions.assertTrue(thrown is NumberFormatException, "Expected NumberFormatException, but was: $thrown")
    }
    finally {
      rv.resetToDefault()
    }
  }

  @Test
  fun `asStringFlow emits default value`() = runTest {
    val key = "registry.flow.test.string.initial"
    val rv = Registry.get(key)
    try {
      rv.setValue("initial")
      val emissions = mutableListOf<String>()

      val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        rv.asStringFlow().collect { emissions.add(it) }
      }
      yield()

      advanceUntilIdle()
      Assertions.assertEquals(listOf("initial"), emissions)
      collector.cancelAndJoin()
    }
    finally {
      rv.resetToDefault()
    }
  }

  @Test
  fun `asStringFlow throws on invalid Registry`() = runTest {
    val key = "registry.flow.test.string.invalid"
    val rv = Registry.get(key)
    try {
      var thrown: Throwable? = null
      supervisorScope {
        val emissions = mutableListOf<String>()

        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
          try {
            rv.asStringFlow().collect { emissions.add(it) }
          }
          catch (t: Throwable) {
            thrown = unwrapCancellation(t)
          }
        }
        yield()

        advanceUntilIdle()
        collector.cancelAndJoin()
      }
      Assertions.assertTrue(thrown is MissingResourceException, "Expected MissingResourceException, but was: $thrown")
    }
    finally {
      rv.resetToDefault()
    }
  }

  @Test
  fun `asChangeEventsFlow emits change events without distinction`() = runTest {
    val key = "registry.flow.test.events"
    val rv = Registry.get(key)
    try {
      rv.setValue("initial")
      val emissions = mutableListOf<Unit>()

      val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        rv.asChangeEventsFlow().collect { emissions.add(it) }
      }
      yield()
      Assertions.assertTrue(emissions.isEmpty())

      rv.setValue("a")
      yield()
      rv.setValue("a")
      yield()
      rv.setValue("a")
      yield()

      advanceUntilIdle()
      Assertions.assertEquals(listOf(Unit, Unit, Unit), emissions)
      collector.cancelAndJoin()
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
