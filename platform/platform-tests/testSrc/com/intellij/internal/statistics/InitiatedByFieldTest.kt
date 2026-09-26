// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.concurrency.ExecutionInitiator
import com.intellij.concurrency.installThreadContext
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsFileEventLogger
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistics.StatisticsTestEventFactory.DEFAULT_SESSION_ID
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.fus.reporting.FeatureUsageLogWriter
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@TestApplication
@Suppress("EventLogDescription")
class InitiatedByFieldTest {
  @Test
  fun `zero-field event carries initiator`() {
    val group = EventLogGroup("initiated.by.test", 1)

    val events = withInitiator(ExecutionInitiator.USER) {
      collectLogEvents { logger ->
        logger.logAsync(group, "zero.field", false)
      }
    }

    assertEquals("USER", events.single().event.data[INITIATED_BY])
  }

  @Test
  fun `zero-field event without initiator carries no data`() {
    val group = EventLogGroup("initiated.by.test", 1)

    val events = collectLogEvents { logger ->
      logger.logAsync(group, "zero.field", false)
    }

    assertFalse(events.single().event.data.containsKey(INITIATED_BY))
  }

  @Test
  fun `zero-field event with group data carries initiator and group data`() {
    val groupField = EventFields.Int("group_value")
    val group = EventLogGroup(
      "initiated.by.test",
      1,
      groupData = listOf(groupField to { addData(groupField.name, 1) }),
    )
    val events = withInitiator(ExecutionInitiator.USER) {
      collectLogEvents { logger ->
        logger.logAsync(group, "zero.field", mapOf(groupField.name to 1), false)
      }
    }

    val data = events.single().event.data
    assertEquals("USER", data[INITIATED_BY])
    assertEquals(1, data[groupField.name])
  }

  @Test
  fun `deferred vararg event carries captured initiator`() {
    val group = EventLogGroup("initiated.by.test", 1)

    val events = withInitiator(ExecutionInitiator.MCP) {
      collectLogEvents { logger ->
        logger.logAsync(group, "deferred", mapOf("value" to 1), false)
      }
    }

    assertEquals("MCP", events.single().event.data[INITIATED_BY])
  }

  @Test
  fun `different initiators prevent event merging`() {
    val group = EventLogGroup("initiated.by.test", 1)
    val events = collectLogEvents { logger ->
      withInitiator(ExecutionInitiator.USER) {
        logger.logAsync(group, "merge", false)
      }
      withInitiator(ExecutionInitiator.MCP) {
        logger.logAsync(group, "merge", false)
      }
    }

    assertEquals(2, events.size)
    assertTrue(events.all { it.event.count == 1 })
  }

  private fun <T> withInitiator(initiator: ExecutionInitiator, action: () -> T): T {
    return installThreadContext(initiator.contextElement, replace = true, action)
  }

  private fun collectLogEvents(action: (TestFeatureUsageFileEventLogger) -> Unit): List<LogEvent> {
    val logger = TestFeatureUsageFileEventLogger()
    try {
      action(logger)
    }
    finally {
      Disposer.dispose(logger)
    }
    return logger.testWriter.logged
  }

  companion object {
    private const val INITIATED_BY = "initiated_by"
  }
}

private const val TEST_RECORDER = "TEST"

class TestFeatureUsageFileEventLogger(session: String = DEFAULT_SESSION_ID,
                                      build: String = "999.999",
                                      bucket: String = "0",
                                      recorderVersion: String = "1",
                                      writer: TestFeatureUsageEventWriter = TestFeatureUsageEventWriter(),
                                      eventLogDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "fus-test-event-logs")) :
  StatisticsFileEventLogger(TEST_RECORDER, session, build, bucket, recorderVersion, writer, eventLogDir) {
  val testWriter = writer

  override fun dispose() {
    super.dispose()
    logExecutor.awaitTermination(10, TimeUnit.SECONDS)
  }
}

/**
 * Test seam capturing the events the logger forwards. Keeps the `...EventWriter` name for continuity with existing
 * call sites; it is a [FeatureUsageLogWriter] fake (the logger now talks to a FusClient through that interface).
 */
class TestFeatureUsageEventWriter : FeatureUsageLogWriter<LogEvent> {
  val logged = ArrayList<LogEvent>()

  override fun queueEvent(event: LogEvent) {
    logged.add(event)
  }
}
