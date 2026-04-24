// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import com.intellij.internal.statistic.eventLog.EventLogFile
import com.intellij.internal.statistic.eventLog.EventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.RecorderOptionProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class TracePiiFilteringEventLoggerTest {

  @Test
  fun `wrapper keeps filtering lazy for data map overload`() {
    val delegate = CapturingLogger()
    val filterInvocations = AtomicInteger(0)
    val wrapper = TracePiiFilteringEventLogger(
      delegateProvider = { delegate },
      dataFilter = { _, _, data ->
        filterInvocations.incrementAndGet()
        LinkedHashMap(data).also { it["filtered"] = true }
      },
    )

    wrapper.logAsync(EventLogGroup("event.log", 1), "event", mapOf("field" to "value"), false)

    assertEquals(0, filterInvocations.get())
    assertTrue(delegate.capturedDataProvider != null)

    val filteredData = delegate.capturedDataProvider!!.invoke()!!
    assertEquals(1, filterInvocations.get())
    assertEquals(true, filteredData["filtered"])
    assertEquals("value", filteredData["field"])
  }

  @Test
  fun `wrapper delegates control methods`() {
    val delegate = CapturingLogger()
    val wrapper = TracePiiFilteringEventLogger(delegateProvider = { delegate }, dataFilter = { _, _, data -> data })

    wrapper.cleanup()
    wrapper.rollOver()

    assertTrue(delegate.cleanupCalled)
    assertTrue(delegate.rollOverCalled)
    assertFalse(delegate.computeCalled)
  }

  @Test
  fun `filters by event fields from event log group`() {
    val group = EventLogGroup("trace.event.log", 1, "TRACE")
    group.registerVarargEvent("metadata.load.failed", LlmParametersField("request"), EventFields.Int("count"))
    val provider = RecorderOptionProvider(
      mapOf(TracePiiRegexRedactor.TRACE_PII_REGEXES_JSON_OPTION to """["eyJ[A-Za-z0-9._-]+"]""")
    )
    val filter = TraceLlmPiiDataFilter.createFilter(provider)

    val input = mapOf<String, Any>(
      "request" to "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.signature-part.more-payload",
      "count" to 1,
    )

    val filtered = filter(group, "metadata.load.failed", input)

    assertEquals("Authorization: Bearer [REDACTED]", filtered["request"])
    assertEquals(1, filtered["count"])
  }

  @Test
  fun `filters nested paths from object list fields`() {
    val group = EventLogGroup("trace.event.log", 1, "TRACE")
    val itemsField = ObjectListEventField("items", LlmParametersField("content"), EventFields.Int("type"))
    group.registerVarargEvent("metadata.updated", itemsField)
    val provider = RecorderOptionProvider(
      mapOf(TracePiiRegexRedactor.TRACE_PII_REGEXES_JSON_OPTION to """["sk_live_[a-z]+"]""")
    )
    val filter = TraceLlmPiiDataFilter.createFilter(provider)

    val input = mapOf<String, Any>(
      "items" to listOf(mapOf("content" to "sk_live_abcdefghijklmnopqrstuvwx", "type" to 1)),
    )

    val filtered = filter(group, "metadata.updated", input)
    val item = (filtered["items"] as List<*>).first() as Map<*, *>

    assertEquals("[REDACTED]", item["content"])
    assertEquals(1, item["type"])
  }

  @Test
  fun `uses regex rules from TRACE recorder option`() {
    val provider = RecorderOptionProvider(
      mapOf(TracePiiRegexRedactor.TRACE_PII_REGEXES_JSON_OPTION to """["MYSECRET\\d+"]""")
    )
    val redactor = TracePiiRegexRedactor(provider)

    assertEquals("token [REDACTED]", redactor.redact("token MYSECRET42"))
  }

  @Test
  fun `remote regex rules are reloaded after recorder options update`() {
    val provider = RecorderOptionProvider(
      mapOf(TracePiiRegexRedactor.TRACE_PII_REGEXES_JSON_OPTION to """["FIRST\\d+"]""")
    )
    val redactor = TracePiiRegexRedactor(provider)

    assertEquals("[REDACTED]", redactor.redact("FIRST1"))
    assertEquals("SECOND2", redactor.redact("SECOND2"))

    provider.update(
      mapOf(TracePiiRegexRedactor.TRACE_PII_REGEXES_JSON_OPTION to """["SECOND\\d+"]""")
    )

    assertEquals("FIRST1", redactor.redact("FIRST1"))
    assertEquals("[REDACTED]", redactor.redact("SECOND2"))
  }

  @Test
  fun `supports regexes with commas in TRACE recorder option`() {
    val provider = RecorderOptionProvider(
      mapOf(TracePiiRegexRedactor.TRACE_PII_REGEXES_JSON_OPTION to """["foo,bar"]""")
    )
    val redactor = TracePiiRegexRedactor(provider)

    assertEquals("value [REDACTED] baz", redactor.redact("value foo,bar baz"))
  }

  @Test
  fun `does not redact when TRACE recorder option is missing`() {
    val redactor = TracePiiRegexRedactor(RecorderOptionProvider(emptyMap()))

    val rawValue = "token AKIA1234567890ABCDEF"
    assertEquals(rawValue, redactor.redact(rawValue))
  }

  @Test
  fun `does not redact when TRACE recorder option is invalid`() {
    val provider = RecorderOptionProvider(
      mapOf(TracePiiRegexRedactor.TRACE_PII_REGEXES_JSON_OPTION to "this is not json")
    )
    val redactor = TracePiiRegexRedactor(provider)

    val rawValue = "token AKIA1234567890ABCDEF"
    assertEquals(rawValue, redactor.redact(rawValue))
  }

  @Test
  fun `does not filter fields from other event ids`() {
    val group = EventLogGroup("trace.event.log", 1, "TRACE")
    group.registerVarargEvent("metadata.update.failed", LlmParametersField("request"))
    group.registerVarargEvent("metadata.loaded", EventFields.Int("count"))
    val provider = RecorderOptionProvider(
      mapOf(TracePiiRegexRedactor.TRACE_PII_REGEXES_JSON_OPTION to """["AKIA[0-9A-Z]{16}"]""")
    )
    val filter = TraceLlmPiiDataFilter.createFilter(provider)

    val input = mapOf<String, Any>(
      "request" to "token AKIA1234567890ABCDEF",
      "count" to 1,
    )

    val filtered = filter(group, "metadata.loaded", input)

    assertEquals(input, filtered)
  }
}

private class LlmParametersField(override val name: String) : StringEventField(name) {
  override val validationRule: List<String>
    get() = listOf("{util#llm_parameters}")
}

private class CapturingLogger : StatisticsEventLogger {
  var capturedDataProvider: (() -> Map<String, Any>?)? = null
  var cleanupCalled = false
  var rollOverCalled = false
  var computeCalled = false

  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<*> {
    capturedDataProvider = { data }
    return CompletableFuture.completedFuture(null)
  }

  override fun logAsync(
    group: EventLogGroup,
    eventId: String,
    dataProvider: () -> Map<String, Any>?,
    isState: Boolean,
  ): CompletableFuture<*> {
    capturedDataProvider = dataProvider
    return CompletableFuture.completedFuture(null)
  }

  override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) {
    computeCalled = true
  }

  override fun getActiveLogFile(): EventLogFile? = null

  override fun getLogFilesProvider(): EventLogFilesProvider = object : EventLogFilesProvider {
    override fun getLogFiles(): List<File> = emptyList()

    override fun getLogFilesExceptActive(): List<File> = emptyList()
  }

  override fun cleanup() {
    cleanupCalled = true
  }

  override fun rollOver() {
    rollOverCalled = true
  }
}
