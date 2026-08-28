// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.problemsView.collector.ProjectErrorsCollector
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class ProjectErrorsCollectorTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project by projectFixture

  @Test
  @Timeout(30)
  fun `project error appeared during history replay is delivered`(): Unit = timeoutRunBlocking {
    val collector = ProblemsCollector.getInstance(project) as ProjectErrorsCollector
    val provider = TestProblemsProvider(project)
    val existingProblems = (1..10).map { TestProblem(provider, "existing problem $it") }
    val lateProblem = TestProblem(provider, "late problem")
    val markerProblem = existingProblems.first()
    val receivedEvents = mutableListOf<ProblemEvent>()
    val historyReplayStarted = CompletableDeferred<Unit>()
    val releaseHistoryReplay = CompletableDeferred<Unit>()
    val liveMarkerEventReceived = CompletableDeferred<Unit>()
    val lateProblemDisappeared = CompletableDeferred<Unit>()
    var collectorJob: Job? = null

    suspend fun waitForCondition(condition: () -> Boolean) {
      requireNotNull(withTimeoutOrNull(3.seconds) {
        while (!condition()) {
          delay(10.milliseconds)
        }
      }) { "Condition was not met within 3s" }
    }

    suspend fun waitForLiveEventsCollection() {
      requireNotNull(withTimeoutOrNull(3.seconds) {
        while (!liveMarkerEventReceived.isCompleted) {
          collector.problemUpdated(markerProblem)
          withTimeoutOrNull(10.milliseconds) { liveMarkerEventReceived.await() }
        }
      }) { "Live problem events were not collected" }
    }

    try {
      existingProblems.forEach { collector.problemAppeared(it) }

      collectorJob = launch {
        collector.getProblemEventsFlow().collect { event ->
          if (event is ProblemEvent.Appeared && event.problem !== lateProblem && !historyReplayStarted.isCompleted) {
            historyReplayStarted.complete(Unit)
            releaseHistoryReplay.await()
          }
          receivedEvents.add(event)
          if (event is ProblemEvent.Updated && event.problem === markerProblem) {
            liveMarkerEventReceived.complete(Unit)
          }
          if (event is ProblemEvent.Disappeared && event.problem === lateProblem) {
            lateProblemDisappeared.complete(Unit)
          }
        }
      }

      requireNotNull(withTimeoutOrNull(3.seconds) { historyReplayStarted.await() }) { "History replay did not start" }
      collector.problemAppeared(lateProblem)
      releaseHistoryReplay.complete(Unit)
      waitForCondition { receivedEvents.count { it is ProblemEvent.Appeared && it.problem !== lateProblem } == existingProblems.size }

      waitForLiveEventsCollection()

      collector.problemDisappeared(lateProblem)
      requireNotNull(withTimeoutOrNull(3.seconds) { lateProblemDisappeared.await() }) {
        "Late problem Disappeared event was not delivered"
      }

      val errorMessage = "Problem appeared during initial history replay should be delivered by the time " +
                         "the corresponding Disappeared event is received"
      assertTrue(receivedEvents.any { it is ProblemEvent.Appeared && it.problem === lateProblem }, errorMessage)
    }
    finally {
      releaseHistoryReplay.complete(Unit)
      collectorJob?.cancel()
      existingProblems.forEach { collector.problemDisappeared(it) }
      collector.problemDisappeared(lateProblem)
    }
  }

  private class TestProblemsProvider(override val project: Project) : ProblemsProvider

  private class TestProblem(override val provider: ProblemsProvider, override val text: String) : Problem
}
