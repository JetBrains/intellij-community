// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionLifecycleTestDSL
import com.intellij.codeInsight.inline.completion.logs.TestInlineCompletionLogs.noSessionLogs
import com.intellij.codeInsight.inline.completion.logs.TestInlineCompletionLogs.singleSessionLog
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.util.Disposer
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import io.kotest.common.runBlocking
import org.jetbrains.annotations.ApiStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * See [noSessionLogs] and [singleSessionLog].
 */
@ApiStatus.Internal
object TestInlineCompletionLogs {
  private fun getSessionLogs(events: List<LogEvent>): List<LogEvent> = events.filter {
    it.group.id == InlineCompletionLogs.GROUP.id
    it.event.id == InlineCompletionLogs.Session.SESSION_EVENT.eventId
  }

  /**
   * Use to assert that [block] does not produce session logs.
   * Also see [singleSessionLog].
   */
  fun InlineCompletionLifecycleTestDSL.noSessionLogs(block: suspend InlineCompletionLifecycleTestDSL.() -> Unit) {
    val events = FUCollectorTestCase.collectLogEvents("ML", fixture.testRootDisposable) {
      runBlocking {
        block()
      }
    }
    val sessionLogs = getSessionLogs(events)
    assertTrue(sessionLogs.isEmpty(), "There should be no session loggers but found: $sessionLogs")
  }

  /**
   * Use to assert that [block] produce exactly one session log.
   * Then assert more things on return value [SingleSessionLog].
   * Also see [noSessionLogs].
   */
  fun InlineCompletionLifecycleTestDSL.singleSessionLog(block: suspend InlineCompletionLifecycleTestDSL.() -> Unit): SingleSessionLog {
    val sessionsLogs = sessionsLog(block)
    assertEquals(1, sessionsLogs.size, "There should be exactly session log, but found: $sessionsLogs")
    return sessionsLogs.single()
  }

  fun InlineCompletionLifecycleTestDSL.firstSessionLog(block: suspend InlineCompletionLifecycleTestDSL.() -> Unit): SingleSessionLog {
    val sessionLog = sessionsLog(block).firstOrNull()
    assertNotNull(sessionLog, "There should be at least one session log, but found nothing.")
    return sessionLog
  }

  private fun InlineCompletionLifecycleTestDSL.sessionsLog(
    block: suspend InlineCompletionLifecycleTestDSL.() -> Unit
  ): List<SingleSessionLog> {
    val disposable = Disposer.newDisposable()
    val events = try {
      FUCollectorTestCase.collectLogEvents("ML", disposable) {
        runBlocking {
          block()
        }
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
    return getSessionLogs(events).map { SingleSessionLog(it) }
  }

  class SingleSessionLog internal constructor(private val logEvent: LogEvent) {
    private fun idToValueForPhase(phase: InlineCompletionLogsContainer.Phase): List<Pair<String, Any>> {
      return (logEvent.event.data[phase.name.lowercase()] as? Map<*, *>?)?.entries?.mapNotNull { entry ->
        val key = entry.key as? String ?: return@mapNotNull null
        val value = entry.value ?: return@mapNotNull null
        key to value
      }.orEmpty()
    }

    private val idToValue: Map<String, Any> = InlineCompletionLogsContainer.Phase.entries.flatMap { idToValueForPhase(it) }.associate { it }


    fun getValue(id: String): Any? {
      return idToValue[id]
    }

    fun assert(id: String, value: Any?) {
      assertEquals(value, idToValue[id])
    }

    fun assertPresent(id: String): Any? {
      assertTrue(idToValue.containsKey(id), "Log for $id is absent")
      return idToValue[id]
    }

    fun assertAbsence(id: String) {
      assertTrue(!idToValue.containsKey(id), "Log for $id is present with value ${idToValue[id]} ")
    }
  }
}