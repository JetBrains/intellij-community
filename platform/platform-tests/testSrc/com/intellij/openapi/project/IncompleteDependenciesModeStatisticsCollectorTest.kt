// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
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
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      runBlocking(Dispatchers.EDT) {
        performInIncompleteMode {
          delay(100)
        }
        performInIncompleteMode {
          delay(100)
        }
      }
    }

    assertThat(events).hasSize(4)
    assertThat(events).allMatch { event ->
      event.group.id == IncompleteDependenciesModeStatisticsCollector.GROUP.id
    }
    val activityId = events.first().event.data["ide_activity_id"] as Int
    assertThat(events.take(2)).allMatch { event ->
      event.event.data["ide_activity_id"] == activityId
    }
    assertThat(events.drop(2)).allMatch { event ->
      event.event.data["ide_activity_id"] == activityId + 1
    }
    assertModeChanged(events[0], DependenciesState.COMPLETE, DependenciesState.INCOMPLETE)
    assertModeChanged(events[1], DependenciesState.INCOMPLETE, DependenciesState.COMPLETE)
    assertModeChanged(events[2], DependenciesState.COMPLETE, DependenciesState.INCOMPLETE)
    assertModeChanged(events[3], DependenciesState.INCOMPLETE, DependenciesState.COMPLETE)
  }

  fun testRecursiveIncompleteMode() {
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      runBlocking(Dispatchers.EDT) {
        performInIncompleteMode {
          performInIncompleteMode {
            performInIncompleteMode {
              delay(100)
            }
          }
        }
      }
    }
    assertThat(events).hasSize(6)
    assertThat(events).allMatch { event ->
      event.group.id == IncompleteDependenciesModeStatisticsCollector.GROUP.id
    }
    val activityIdStack = mutableListOf<Int>()
    for (event in events) {
      val activityId = event.event.data["ide_activity_id"] as Int
      if (activityIdStack.lastOrNull() == activityId) {
        activityIdStack.removeLast()
      }
      else activityIdStack.add(activityId)
    }
    assertThat(activityIdStack).isEmpty()
    assertModeChanged(events.first(), DependenciesState.COMPLETE, DependenciesState.INCOMPLETE)
    assertModeChanged(events.last(), DependenciesState.INCOMPLETE, DependenciesState.COMPLETE)

    assertThat(events.drop(1).dropLast(1)).allMatch { event ->
      event.event.data["state_before"] == "INCOMPLETE"
      event.event.data["state_after"] == "INCOMPLETE"
    }
  }

  fun testIntertwinedIncompleteMode() {
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      runBlocking(Dispatchers.EDT) {
        val token1 = writeAction { project.service<IncompleteDependenciesService>().enterIncompleteState() }
        val token2 = writeAction { project.service<IncompleteDependenciesService>().enterIncompleteState() }
        delay(100)
        writeAction { token1.finish() }
        writeAction { token2.finish() }
      }
    }

    assertThat(events).hasSize(4)
    assertThat(events).allMatch { event ->
      event.group.id == IncompleteDependenciesModeStatisticsCollector.GROUP.id
    }
    assertModeChanged(events[0], DependenciesState.COMPLETE, DependenciesState.INCOMPLETE)
    assertModeChanged(events[1], DependenciesState.INCOMPLETE, DependenciesState.INCOMPLETE)
    assertModeChanged(events[2], DependenciesState.INCOMPLETE, DependenciesState.INCOMPLETE)
    assertModeChanged(events[3], DependenciesState.INCOMPLETE, DependenciesState.COMPLETE)
  }

  private fun assertModeChanged(event: LogEvent, before: DependenciesState, after: DependenciesState) {
    assertThat(event).matches { event -> event.event.data["state_before"] == before.toString() }
    assertThat(event).matches { event -> event.event.data["state_after"] == after.toString() }
  }

  private suspend fun performInIncompleteMode(action: suspend () -> Unit) {
    val token = writeAction { project.service<IncompleteDependenciesService>().enterIncompleteState() }
    action()
    writeAction { token.finish() }
  }
}