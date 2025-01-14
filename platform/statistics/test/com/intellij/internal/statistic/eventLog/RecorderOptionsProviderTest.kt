// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecorderOptionsProviderTest {

  @Test
  fun testStringOption() {
    val recorderOptionProvider = RecorderOptionProvider(mapOf("name" to "john smith"))
    val name = recorderOptionProvider.getStringOption("name")
    assertTrue(name != null)
    assertEquals("john smith", name)
  }

  @Test
  fun testIntOption() {
    val recorderOptionProvider = RecorderOptionProvider(mapOf("number" to "1234567890"))
    val number = recorderOptionProvider.getIntOption("number")
    assertTrue(number != null)
    assertEquals(1234567890, number)
  }

  @Test
  fun testIncorrectIntOption() {
    val recorderOptionProvider = RecorderOptionProvider(mapOf("number" to "12345678901234567890"))
    val number = recorderOptionProvider.getIntOption("number")
    assertTrue(number == null)
  }

  @Test
  fun testListOption() {
    val recorderOptionProvider = RecorderOptionProvider(mapOf("test" to "foo,bar,baz"))
    val option = recorderOptionProvider.getListOption("test")
    assertTrue(option != null)
    assertEquals(listOf("foo", "bar", "baz"), option)
  }

  @Test
  fun testEmptyListOption() {
    val recorderOptionProvider = RecorderOptionProvider(mapOf("test" to ", , ,,,,"))
    val option = recorderOptionProvider.getListOption("test")
    assertTrue(option != null)
    assertEquals(emptyList<String>(), option)
  }

  @Test
  fun testUpdatedListOption() {
    val recorderOptionProvider = RecorderOptionProvider(mapOf("test" to "foo,bar,baz"))
    recorderOptionProvider.update(mapOf("test" to "foo,baz"))
    val option = recorderOptionProvider.getListOption("test")
    assertTrue(option != null)
    assertEquals(listOf("foo", "baz"), option)
  }
}
