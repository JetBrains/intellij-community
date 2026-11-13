// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.TestStatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.EventLogSystemCollector
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException.EventLogMetadataLoadErrorType
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
internal class EventLogSystemCollectorTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val loadError = EventLogMetadataLoadException(EventLogMetadataLoadErrorType.ERROR_ON_LOAD, 503)

  @Test
  fun `a metadata failure reports the retry count`() {
    val event = logSingle { it.logMetadataUpdateFailed(loadError, retryCount = 2) }

    assertEquals("metadata.update.failed", event.event.id)
    assertEquals(2, event.event.data["retry_count"])
    assertEquals(503, event.event.data["code"])
    assertFalse(event.event.data.containsKey("dictionary_name"))
  }

  @Test
  fun `a dictionary list failure reports the retry count`() {
    val event = logSingle { it.logDictionaryListLoadFailed(loadError, retryCount = 1) }

    assertEquals("dictionary.list.load.failed", event.event.id)
    assertEquals(1, event.event.data["retry_count"])
    assertFalse(event.event.data.containsKey("dictionary_name"))
  }

  @Test
  fun `a dictionary failure reports the dictionary name and the retry count`() {
    val event = logSingle { it.logDictionaryUpdateFailed(loadError, retryCount = 3, dictionaryName = "python_packages.ndjson") }

    assertEquals("dictionary.update.failed", event.event.id)
    assertEquals(3, event.event.data["retry_count"])
    assertEquals("python_packages.ndjson", event.event.data["dictionary_name"])
  }

  @Test
  fun `a dictionary failure without a dictionary name omits the field`() {
    val event = logSingle { it.logDictionaryLoadFailed(loadError, retryCount = 0, dictionaryName = null) }

    assertEquals("dictionary.load.failed", event.event.id)
    assertEquals(0, event.event.data["retry_count"])
    assertFalse(event.event.data.containsKey("dictionary_name"))
  }

  @Test
  fun `a dictionary update reports the dictionary name and the retry count`() {
    val event = logSingle { it.logDictionaryUpdated(lastModified = 1_000L, dictionaryName = "npm_packages.ndjson", retryCount = 4) }

    assertEquals("dictionary.updated", event.event.id)
    assertEquals(1_000L, event.event.data["dictionary_last_modified"])
    assertEquals("npm_packages.ndjson", event.event.data["dictionary_name"])
    assertEquals(4, event.event.data["retry_count"])
  }

  private fun logSingle(action: (EventLogSystemCollector) -> Unit): LogEvent {
    val collector = EventLogSystemCollector(TestStatisticsEventLoggerProvider("FUS", escapeChars = false))
    return FUCollectorTestCase.collectLogEvents(disposable) { action(collector) }.single()
  }
}
