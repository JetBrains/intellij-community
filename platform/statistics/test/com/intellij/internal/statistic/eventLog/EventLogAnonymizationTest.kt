// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.trace.TraceEventLoggerProvider
import com.intellij.internal.statistic.eventLog.trace.TracePiiFilteringEventLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.TestMutableComponentManager
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestApplication
@SystemProperty(propertyKey = "idea.headless.statistics.salt", propertyValue = "fus-test-salt")
@SystemProperty(propertyKey = "idea.headless.statistics.salt.trace", propertyValue = "trace-test-salt")
internal class EventLogAnonymizationTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    (StatisticsEventLoggerProvider.EP_NAME.point as ExtensionPointImpl<StatisticsEventLoggerProvider>)
      .maskAll(listOf(TraceEventLoggerProvider()), disposable, true)
  }

  @Test
  fun `FUS then TRACE share IDs when fields are built first`() {
    checkLoggedIds("FUS", loggerFirst = false)
  }

  @Test
  fun `TRACE then FUS share IDs when fields are built first`() {
    checkLoggedIds("TRACE", loggerFirst = false)
  }

  @Test
  fun `FUS then TRACE share IDs when the logger is initialized first`() {
    checkLoggedIds("FUS", loggerFirst = true)
  }

  @Test
  fun `TRACE then FUS share IDs when the logger is initialized first`() {
    checkLoggedIds("TRACE", loggerFirst = true)
  }

  private fun checkLoggedIds(firstRecorder: String, loggerFirst: Boolean) {
    val firstEvents = recordEvents(firstRecorder, loggerFirst)
    val reconnectedEvents = recordEvents(if (firstRecorder == "FUS") "TRACE" else "FUS", !loggerFirst)

    for (events in listOf(firstEvents, reconnectedEvents)) {
      val fus = events.getValue("FUS").event.data
      val trace = events.getValue("TRACE").event.data
      assertEquals(fus["agent_session_id"], trace["chain_id"])
      assertEquals(fus["turn_id"], trace["issue_id"])
      assertEquals(12, (fus["agent_session_id"] as String).length)
      assertEquals(12, (fus["turn_id"] as String).length)
      assertNotEquals("session-id", fus["agent_session_id"])
      assertNotEquals("turn-id", fus["turn_id"])
    }
    for (recorder in listOf("FUS", "TRACE")) {
      assertEquals(firstEvents.getValue(recorder).event.data, reconnectedEvents.getValue(recorder).event.data)
    }
  }

  @Test
  fun `configuration resolves the recorder before logger access`() {
    val configuration = EventLogConfiguration()
    val trace = configuration.getOrCreate("TRACE")
    val fus = configuration.getOrCreate("FUS")

    assertEquals("FUS", trace.alternativeRecorderId)
    assertEquals(fus.anonymize("session-id"), trace.anonymize("session-id"))
    assertEquals(fus.deviceId, trace.deviceId)
    assertEquals(fus.machineId, trace.machineId)
    assertSame(trace, configuration.getOrCreate("TRACE", "FUS"))
  }

  @Test
  @SystemProperty(propertyKey = "idea.headless.statistics.device.id", propertyValue = "fus-test-device")
  @SystemProperty(propertyKey = "idea.headless.statistics.device.id.trace", propertyValue = "trace-test-device")
  fun `concurrent recorder initialization shares one TRACE configuration`() {
    val callerCount = 12
    val executor = Executors.newFixedThreadPool(callerCount) { task ->
      Thread(task, "TRACE Initialization Test").apply { isDaemon = true }
    }
    Disposer.register(disposable) {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "The callers did not stop")
    }
    repeat(20) {
      val configuration = EventLogConfiguration()
      val fus = configuration.getOrCreate("FUS")
      val ready = CountDownLatch(callerCount)
      val start = CountDownLatch(1)
      val callers = List(callerCount) { index ->
        executor.submit<EventLogRecorderConfiguration> {
          ready.countDown()
          assertTrue(start.await(10, TimeUnit.SECONDS), "The start gate did not open")
          val recorder = when (index % 3) {
            0 -> configuration.getOrCreate("FUS")
            1 -> configuration.getOrCreate("TRACE")
            else -> configuration.getOrCreate("TRACE", "FUS")
          }
          assertEquals("fus-test-device", recorder.deviceId)
          assertEquals(fus.machineId, recorder.machineId)
          assertEquals(fus.anonymize("session-id", true), recorder.anonymize("session-id", true))
          assertEquals(fus.anonymize("turn-id", true), recorder.anonymize("turn-id", true))
          recorder
        }
      }

      assertTrue(ready.await(10, TimeUnit.SECONDS), "The callers did not reach the start gate")
      start.countDown()
      val recorders = callers.map { it.get(10, TimeUnit.SECONDS) }
      val trace = configuration.getOrCreate("TRACE")
      assertEquals("FUS", trace.alternativeRecorderId)
      recorders.forEachIndexed { index, recorder ->
        assertSame(if (index % 3 == 0) fus else trace, recorder)
      }
      assertSame(trace, configuration.getOrCreate("TRACE", "FUS"))
    }
  }

  @Test
  fun `an explicit alternative recorder takes precedence`() {
    val configuration = EventLogConfiguration()
    val independent = configuration.getOrCreate("INDEPENDENT")
    val trace = configuration.getOrCreate("TRACE", "INDEPENDENT")

    assertEquals("INDEPENDENT", trace.alternativeRecorderId)
    assertEquals(independent.anonymize("session-id"), trace.anonymize("session-id"))
    assertNotEquals(configuration.getOrCreate("FUS").anonymize("session-id"), independent.anonymize("session-id"))
  }

  @Suppress("EventLogDescription")
  private fun recordEvents(firstRecorder: String, loggerFirst: Boolean): Map<String, LogEvent> {
    val configuration = EventLogConfiguration()
    val container = (ApplicationManager.getApplication() as ComponentManagerEx).getMutableComponentContainer() as TestMutableComponentManager
    container.replaceServiceInstance(EventLogConfiguration::class.java, configuration, disposable)
    val traceProvider = StatisticsEventLogProviderUtil.getEventLogProvider("TRACE")
    val fusLogger = RecordingLogger()
    val traceLogger = RecordingLogger()
    var traceDelegateInitialized = false
    val trace = TracePiiFilteringEventLogger(
      delegateProvider = {
        configuration.getOrCreate("TRACE", if (traceProvider.useDefaultRecorderId) "FUS" else null)
        traceDelegateInitialized = true
        traceLogger
      },
      recorderOptionsProvider = traceProvider.recorderOptionsProvider,
    )
    if (loggerFirst) {
      trace.getLogFilesProvider()
    }
    val recorders = if (firstRecorder == "FUS") listOf("FUS", "TRACE") else listOf("TRACE", "FUS")
    for (recorder in recorders) {
      val sessionField = EventFields.ShortAnonymizedField(if (recorder == "FUS") "agent_session_id" else "chain_id")
      val turnField = EventFields.ShortAnonymizedField(if (recorder == "FUS") "turn_id" else "issue_id")
      val group = EventLogGroup("test.agent.ids", 1, recorder)
      group.registerVarargEvent("turn.started", sessionField, turnField)
      val data = FeatureUsageData(recorder)
      sessionField.addData(data, "session-id")
      turnField.addData(data, "turn-id")
      if (recorder == "TRACE" && !loggerFirst) {
        assertFalse(traceDelegateInitialized)
      }
      val logger = if (recorder == "FUS") fusLogger else trace
      logger.logAsync(group, "turn.started", data.build(), false).join()
    }
    return mapOf("FUS" to fusLogger.events.single(), "TRACE" to traceLogger.events.single())
  }

  private class RecordingLogger : StatisticsEventLogger by EmptyStatisticsEventLogger() {
    val events = ArrayList<LogEvent>()

    override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
      events.add(LogEvent("test-session", "999.999", "1", 0, LogEventGroup(group.id, group.version.toString()),
                          "1", LogEventAction(eventId, isState, HashMap(data))))
      return CompletableFuture.completedFuture(null)
    }

    override fun logAsync(
      group: EventLogGroup,
      eventId: String,
      dataProvider: () -> Map<String, Any>?,
      isState: Boolean,
    ): CompletableFuture<Void> = dataProvider()?.let { logAsync(group, eventId, it, isState) } ?: CompletableFuture.completedFuture(null)
  }
}
