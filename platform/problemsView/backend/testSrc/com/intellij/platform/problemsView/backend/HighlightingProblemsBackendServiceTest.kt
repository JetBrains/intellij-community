// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater
import com.intellij.codeInspection.CustomSuppressableInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.platform.problemsView.collector.ProjectErrorsCollector
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPlainText
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.enableInspectionTool
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
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
    .psiFileFixture("testFile.java", "class TestMe {}\n")

  private suspend fun createTestHighlightingProblems(problemCount: Int): List<HighlightInfo> = readAction {
    (1..problemCount).mapNotNull { i ->
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(0, 0)
        .description("problem $i")
        .create()
    }
  }

  private suspend fun addProblemsToDocument(problemCount: Int) {
    val highlightInfos = createTestHighlightingProblems(problemCount = problemCount)
    addProblemsToDocument(highlightInfos)
  }

  private suspend fun addProblemsToDocument(highlightInfos: List<HighlightInfo>) {
    val document = readAction { testFile.viewProvider.document }
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
    timeout: Duration = 3.seconds,
  ): List<ProblemEventDto> {
    return requireNotNull(
      withTimeoutOrNull(timeout) {
        while (batches.flatten().size != expectedCount) {
          delay(50.milliseconds)
        }
        batches.flatten()
      }
    ) { "Expected $expectedCount events within $timeout, but got ${batches.flatten().size}" }
  }

  private suspend fun waitForCondition(timeout: Duration = 3.seconds, condition: () -> Boolean) {
    requireNotNull(
      withTimeoutOrNull(timeout) {
        while (!condition()) {
          delay(10.milliseconds)
        }
      }
    ) { "Condition was not met within $timeout" }
  }

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
  fun `project error appeared during history replay is delivered`() = runBlocking {
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
    var collectorJob: kotlinx.coroutines.Job? = null

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

      assertTrue(receivedEvents.any { it is ProblemEvent.Appeared && it.problem === lateProblem },
                 "Problem appeared during initial history replay should be delivered by the time the corresponding Disappeared event is received")
    }
    finally {
      releaseHistoryReplay.complete(Unit)
      collectorJob?.cancel()
      existingProblems.forEach { collector.problemDisappeared(it) }
      collector.problemDisappeared(lateProblem)
    }
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

    requireNotNull(updated) { "Expected an Updated event for problem $problemId after quick fixes became available" }
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

  @Test
  @Timeout(30)
  fun `inspection suppression uses the problem element`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(testFile.virtualFile)

    }
    val lifetimeManager = ProblemLifetimeManager.getInstance(project)

    val flow = HighlightingProblemsBackendService.getInstance(project)
      .getOrCreateEventFlowForFile(testFile.virtualFile.rpcId())
    val batches = mutableListOf<List<ProblemEventDto>>()
    val collectorJob = launch {
      flow.collect { batch -> batches.add(batch) }
    }

    class TestSuppress: SuppressIntentionAction() {
      override fun getText(): String = familyName

      override fun getFamilyName(): String = "Suppress test class problem"

      override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean = true

      override fun invoke(project: Project, editor: Editor?, element: PsiElement) = Unit
    }

    class TestClassInspection : LocalInspectionTool(), CustomSuppressableInspectionTool {
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
          override fun visitPlainText(content: PsiPlainText) {
            holder.registerProblem(content, "Test class problem")
          }
        }
      }

      override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction> {
        return if (element is PsiFile) emptyArray() else arrayOf(TestSuppress())
      }

      override fun isSuppressedFor(element: PsiElement): Boolean = false
    }


    val inspection = TestClassInspection()
    enableInspectionTool(project, inspection, disposable)
    val key = requireNotNull(HighlightDisplayKey.find(inspection.shortName))

    val problems = readAction {
      listOfNotNull(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                      .range(0, 0)
                      .description("test problem with quick fixes")
                      .registerFix(EmptyIntentionAction("Test fix"), null, "", null, key)
                      .create())
    }

    addProblemsToDocument(problems)

    val appearedId = waitForAllExpectedProblemEvents(batches = batches, expectedCount = 1)
      .filterIsInstance<ProblemEventDto.Appeared>()
      .single()
      .problemDto.id
    collectorJob.cancel()

    val problem = lifetimeManager.findProblemById(appearedId) as HighlightingProblem

    assertTrue(problem.info?.findRegisteredQuickFix { descriptor, _ ->
      val intentionAction = descriptor.getOptions(testFile.findElementAt(0)!!, null)
      intentionAction.any { it is TestSuppress }
    } == true)
  }

  private class TestProblemsProvider(override val project: Project) : ProblemsProvider

  private class TestProblem(override val provider: ProblemsProvider, override val text: String) : Problem
}
