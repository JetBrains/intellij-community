// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class IncompleteDependenciesModeStatisticsCollectorTest : LightPlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testConsecutiveIncompleteMode() {
    val duration1: Long = 100
    val duration2: Long = 200
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      runBlocking(Dispatchers.EDT) {
        performInIncompleteMode {
          delay(duration1)
        }
        performInIncompleteMode {
          delay(duration2)
        }
      }
    }

    assertThat(events).allMatch { event ->
      event.group.id == IncompleteDependenciesModeStatisticsCollector.GROUP.id
    }
    assertThat(events).hasSize(8)
    checkIncompleteMode(events.take(4), minimumDuration = duration1)
    checkIncompleteMode(events.drop(4), minimumDuration = duration2)
  }

  private fun checkIncompleteMode(events: List<LogEvent>, minimumDuration: Long) {
    assertThat(events.first().event.id).isEqualTo("incomplete_dependencies_mode.started")
    assertThat(events.last().event.id).isEqualTo("incomplete_dependencies_mode.finished")
    assertThat(events.last().event.data["duration_ms"] as Long).isGreaterThanOrEqualTo(minimumDuration)

    val activityEvents = events.drop(1).dropLast(1)
    val seenIds = mutableSetOf<Int>()
    val ids = mutableSetOf<Int>()
    for (event in activityEvents) {
      val id = event.event.data["step_id"] as Int
      if (ids.contains(id)) ids.remove(id)
      else {
        assertThat(seenIds).doesNotContain(id)
        seenIds.add(id)
        ids.add(id)
      }

      assertThat(event.event.data["requestor"]).isEqualTo(IncompleteDependenciesModeStatisticsCollectorTest::class.java.name)
    }
    assertThat(ids).isEmpty()

    assertModeChanged(activityEvents.first(), DependenciesState.COMPLETE, DependenciesState.INCOMPLETE)
    for (middleActivity in activityEvents.drop(1).dropLast(1)) {
      assertModeChanged(middleActivity, DependenciesState.INCOMPLETE, DependenciesState.INCOMPLETE)
    }
    assertModeChanged(activityEvents.last(), DependenciesState.INCOMPLETE, DependenciesState.COMPLETE)
  }

  fun testRecursiveIncompleteMode() {
    val duration: Long = 100
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      runBlocking(Dispatchers.EDT) {
        performInIncompleteMode {
          performInIncompleteMode {
            performInIncompleteMode {
              delay(duration)
            }
          }
        }
      }
    }
    assertThat(events).allMatch { event ->
      event.group.id == IncompleteDependenciesModeStatisticsCollector.GROUP.id
    }
    assertThat(events).hasSize(8)
    checkIncompleteMode(events, minimumDuration = duration)
  }

  fun testIntertwinedIncompleteMode() {
    val duration: Long = 100
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      runBlocking(Dispatchers.EDT) {
        val token1 = edtWriteAction { project.service<IncompleteDependenciesService>().enterIncompleteState(this@IncompleteDependenciesModeStatisticsCollectorTest) }
        val token2 = edtWriteAction { project.service<IncompleteDependenciesService>().enterIncompleteState(this@IncompleteDependenciesModeStatisticsCollectorTest) }
        delay(duration)
        edtWriteAction { token1.finish() }
        edtWriteAction { token2.finish() }
      }
    }

    assertThat(events).allMatch { event ->
      event.group.id == IncompleteDependenciesModeStatisticsCollector.GROUP.id
    }
    assertThat(events).hasSize(6)
    checkIncompleteMode(events, minimumDuration = duration)
  }

  private fun assertModeChanged(event: LogEvent, before: DependenciesState, after: DependenciesState) {
    assertThat(event).matches { event -> event.event.data["state_before"] == before.toString() }
    assertThat(event).matches { event -> event.event.data["state_after"] == after.toString() }
  }

  private suspend fun performInIncompleteMode(action: suspend () -> Unit) {
    val token = edtWriteAction { project.service<IncompleteDependenciesService>().enterIncompleteState(this@IncompleteDependenciesModeStatisticsCollectorTest) }
    action()
    edtWriteAction { token.finish() }
  }
}