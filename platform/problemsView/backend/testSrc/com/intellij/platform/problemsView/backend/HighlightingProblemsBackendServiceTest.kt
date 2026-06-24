// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class HighlightingProblemsBackendServiceTest {

  private val projectFixture = projectFixture()
  private val project by projectFixture
  private val testFile by projectFixture
    .moduleFixture("testModule")
    .sourceRootFixture()
    .psiFileFixture("testFile.java", "\n" )

  private suspend fun createTestHighlightingProblems(problemCount: Int): List<HighlightInfo> = readAction {
    (1..problemCount).mapNotNull { i ->
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(0, 0)
        .description("problem $i")
        .create()
    }
  }

  private suspend fun addProblemsToDocument(problemCount: Int) {
    val document = readAction { testFile.viewProvider.document }
    val highlightInfos = createTestHighlightingProblems(problemCount = problemCount)

    withContext(Dispatchers.EDT) {
      UpdateHighlightersUtil.setHighlightersToEditor(
        project,
        document,
        0,
        0,
        highlightInfos,
        null,
        1
      )
      thisLogger().debug("added ${highlightInfos.size} problems to ${testFile.name}")
    }
  }

  private suspend fun waitForAllExpectedProblemEvents(
    batches: MutableList<List<ProblemEventDto>>,
    expectedCount: Int,
    timeout: Duration = 3.seconds
  ): List<ProblemEventDto> {
    return requireNotNull(
      withTimeoutOrNull(timeout) {
        while (batches.flatten().size != expectedCount) {
          delay(50.milliseconds)
        }
        batches.flatten()
      }
    ) { "Expected $expectedCount events within $timeout, but got ${batches.flatten().size}" } }


    private suspend fun closeFile(file: PsiFile, project: Project) {
    withContext(Dispatchers.EDT) {
      project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
        .fileClosed(FileEditorManager.getInstance(project), file.virtualFile)
    }

    delay(300.milliseconds)
  }

  @Test
  fun `all problems appeared after subscription are collected`() = runBlocking {
    val flow = HighlightingProblemsBackendService
      .getInstance(project)
      .getOrCreateEventFlowForFile(
        testFile.virtualFile.rpcId()
      )
    val batches = mutableListOf<List<ProblemEventDto>>()

    val collectorJob = launch {
      flow.collect { batch ->
        thisLogger().debug("received a batch of ${batch.size} events")
        batches.add(batch)
      }
    }

    val problemCount = 10
    addProblemsToDocument(problemCount = problemCount)

    val allEvents = waitForAllExpectedProblemEvents(batches = batches, expectedCount = problemCount)

    collectorJob.cancel()

    val problemAppearedEvents = allEvents.filterIsInstance<ProblemEventDto.Appeared>()
    assertTrue(allEvents == problemAppearedEvents, "there should only be problem appeared events")
  }

  @Test
  fun `problems collected with a delay from emission are not lost`() = runBlocking {
    val problemCount = 100
    addProblemsToDocument(problemCount = problemCount)

    val flow = HighlightingProblemsBackendService
      .getInstance(project)
      .getOrCreateEventFlowForFile(
        testFile.virtualFile.rpcId()
      )

    val batches = mutableListOf<List<ProblemEventDto>>()

    val collectorJob = launch {
      delay(3.seconds) // delay to imitate a slow frontend subscription to the backend events

      flow.collect { batch ->
        thisLogger().debug("received a batch of ${batch.size} events")
        batches.add(batch)
      }
    }

    val allEvents = waitForAllExpectedProblemEvents(batches = batches, expectedCount = problemCount, timeout = 4.seconds)

    collectorJob.cancel()

    val problemAppearedEvents = allEvents.filterIsInstance<ProblemEventDto.Appeared>()
    assertTrue(allEvents == problemAppearedEvents, "there should only be problem appeared events")
  }

  @Test
  fun `re-subscription delivers all existing problems`() = runBlocking {
    val service = HighlightingProblemsBackendService.getInstance(project)
    val fileId = testFile.virtualFile.rpcId()

    val flow1 = service.getOrCreateEventFlowForFile(fileId)
    val batches1 = mutableListOf<List<ProblemEventDto>>()

    val collectorJob1 = launch {
      flow1.collect { batch ->
        thisLogger().debug("first subscription received batch of ${batch.size} events")
        batches1.add(batch)
      }
    }

    val problemCount = 50
    addProblemsToDocument(problemCount = problemCount)

    waitForAllExpectedProblemEvents(batches = batches1, expectedCount = problemCount)

    collectorJob1.cancel()

    val flow2 = service.getOrCreateEventFlowForFile(fileId)
    val batches2 = mutableListOf<List<ProblemEventDto>>()

    val collectorJob2 = launch {
      flow2.collect { batch ->
        thisLogger().debug("second subscription received batch of ${batch.size} events")
        batches2.add(batch)
      }
    }

    val secondBatchEvents = waitForAllExpectedProblemEvents(batches = batches2, expectedCount = problemCount, timeout = 2.seconds)

    collectorJob2.cancel()

    val problemAppearedEvents = secondBatchEvents.filterIsInstance<ProblemEventDto.Appeared>()
    assertTrue(secondBatchEvents == problemAppearedEvents, "there should only be problem appeared events")
  }

  @Test
  fun `closing file removes all problem ids from storage`() = runBlocking {
    val lifetimeManager = ProblemLifetimeManager.getInstance(project)

    val flow = HighlightingProblemsBackendService
      .getInstance(project)
      .getOrCreateEventFlowForFile(
        testFile.virtualFile.rpcId()
      )
    val batches = mutableListOf<List<ProblemEventDto>>()

    val collectorJob = launch {
      flow.collect { batch ->
        thisLogger().debug("received batch of ${batch.size} events")
        batches.add(batch)
      }
    }

    val problemCount = 50
    addProblemsToDocument(problemCount = problemCount)

    val allEvents = waitForAllExpectedProblemEvents(batches = batches, expectedCount = problemCount)
    val problemIds = allEvents.filterIsInstance<ProblemEventDto.Appeared>()
      .map { it.problemDto.id }

    assertEquals(problemCount, problemIds.size, "should have collected all problem IDs")

    val problemsBeforeClose = problemIds.mapNotNull { id ->
      lifetimeManager.findProblemById(id)
    }
    assertEquals(problemCount, problemsBeforeClose.size, "all problems should be findable by ID before closing")

    collectorJob.cancel()

    closeFile(file = testFile, project = project)

    val problemsAfterClose = problemIds.mapNotNull { id ->
      lifetimeManager.findProblemById(id)
    }
    assertEquals(0, problemsAfterClose.size, "all problem IDs should be removed from storage after file close")

    problemIds.forEach { id ->
      val problem = lifetimeManager.findProblemById(id)
      assertNull(problem, "problem with ID $id should not be findable after file close")
    }
  }

  @Test
  fun `quick fixes becoming available updates the problem`() = runBlocking {
    val lifetimeManager = ProblemLifetimeManager.getInstance(project)

    val flow = HighlightingProblemsBackendService
      .getInstance(project)
      .getOrCreateEventFlowForFile(
        testFile.virtualFile.rpcId()
      )
    val batches = mutableListOf<List<ProblemEventDto>>()

    val collectorJob = launch {
      flow.collect { batch -> batches.add(batch) }
    }

    addProblemsToDocument(problemCount = 1)

    val problemId = waitForAllExpectedProblemEvents(batches = batches, expectedCount = 1)
      .filterIsInstance<ProblemEventDto.Appeared>()
      .single()
      .problemDto.id

    // simulate lazy quick-fixes finishing their background computation
    val problem = lifetimeManager.findProblemById(problemId) as HighlightingProblem
    val highlighter = problem.highlighter
    val info = requireNotNull(readAction { HighlightInfo.fromRangeHighlighter(highlighter) }) {
      "the added highlighter should have an associated HighlightInfo"
    }
    project.messageBus.syncPublisher(LazyQuickFixUpdater.TOPIC).quickFixesAvailable(info, highlighter.document)

    fun updatesOfProblem() = batches.flatten()
      .filterIsInstance<ProblemEventDto.Updated>()
      .filter { it.problemDto.id == problemId }

    val updated = withTimeoutOrNull(3.seconds) {
        while (updatesOfProblem().isEmpty()) {
          delay(50.milliseconds)
        }
        updatesOfProblem().first()
      }

    collectorJob.cancel()

    requireNotNull(updated) {"Expected an Updated event for problem $problemId after quick fixes became available" }
    assertEquals(problemId, updated.problemDto.id, "the same problem should be updated, not a new one")
  }

  @Test
  fun `updating a problem does not leak intention ids`() = runBlocking {
    val lifetimeManager = ProblemLifetimeManager.getInstance(project)

    val flow = HighlightingProblemsBackendService
      .getInstance(project)
      .getOrCreateEventFlowForFile(
        testFile.virtualFile.rpcId()
      )
    val batches = mutableListOf<List<ProblemEventDto>>()

    val collectorJob = launch {
      flow.collect { batch -> batches.add(batch) }
    }

    addProblemsToDocument(problemCount = 1)

    val appearedId = waitForAllExpectedProblemEvents(batches = batches, expectedCount = 1)
      .filterIsInstance<ProblemEventDto.Appeared>()
      .single()
      .problemDto.id
    collectorJob.cancel()

    val problem = lifetimeManager.findProblemById(appearedId) as HighlightingProblem

    val lifetimeScope = childScope("test lifetime")
    val lifetime = ProblemLifetime(lifetimeScope)
    try {
      val problemId = lifetimeManager.getOrCreateHighlightingProblemId(problem, lifetime)
      val staleIntentionId = lifetimeManager.createIntentionId(EmptyIntentionAction("test"), lifetime, problemId)

      assertNotNull(lifetimeManager.findIntentionById(staleIntentionId),
                    "intention id should be resolvable right after creation")

      // updating the problem removes stale intention ids
      lifetimeManager.getOrCreateHighlightingProblemId(problem, lifetime)
      assertNull(lifetimeManager.findIntentionById(staleIntentionId),
                 "intention id from the previous version of the problem should be removed after its update")
    }
    finally {
      lifetimeScope.cancel()
    }
  }
}
