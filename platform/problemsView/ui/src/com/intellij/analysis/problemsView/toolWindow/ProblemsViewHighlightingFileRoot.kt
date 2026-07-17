// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingFileRoot
import com.intellij.analysis.problemsView.toolWindow.splitApi.MissingIdDiagnostics
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.build.FlowWithHistory
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class ProblemsViewHighlightingFileRoot(
  panel: ProblemsViewPanel,
  override val file: VirtualFile,
  override val document: Document
) : Root(panel), HighlightingFileRoot {
  private val problems = mutableSetOf<HighlightingProblem>()
  private val filter = ProblemFilter(panel.state)

  private val provider: ProblemsProvider = object : ProblemsProvider {
    override val project = panel.project
  }

  init {
    Disposer.register(this, provider)
  }

  val lifetime: ProblemLifetime = HighlightingProblemsLifetimeService.getInstance(panel.project).createRootLifetime(this)
  private val problemEventsFlow = HighlightingProblemEventFlow(lifetime.coroutineScope)
  val problemEvents: Flow<ProblemEvent> = problemEventsFlow.getFlowWithHistory()

  private val watcher: ProblemsViewHighlightingWatcher =
    ProblemsViewHighlightingWatcher(provider, this, file, document, HighlightSeverity.TEXT_ATTRIBUTES.myVal + 1)

  override fun findProblem(highlighter: RangeHighlighterEx): Problem? = watcher.findProblem(highlighter)

  override fun getProblemCount(): Int = synchronized(problems) { problems.count(filter) }

  override fun getProblemFiles(): List<VirtualFile> = when (getProblemCount() > 0) {
    true -> listOf(file)
    else -> emptyList()
  }

  override fun getFileProblemCount(file: VirtualFile): Int = when (this.file == file) {
    true -> getProblemCount()
    else -> 0
  }

  override fun getFileProblems(file: VirtualFile): List<HighlightingProblem> = when (this.file == file) {
    true -> synchronized(problems) {
      problems.filter(filter)
    }
    else -> emptyList()
  }

  override fun getOtherProblemCount(): Int = 0

  override fun getOtherProblems(): Collection<Problem> = emptyList()

  override fun problemAppeared(problem: Problem) {
    MissingIdDiagnostics.trace("FileRoot", "problemAppeared:in", MissingIdDiagnostics.STEP_APPEARED, problem, "file=${file.name}")
    if (problem !is HighlightingProblem || problem.file != file) {
      return
    }
    notify(problem = problem, state = synchronized(problems) { SetUpdateState.add(problem, problems) })
  }

  override fun problemDisappeared(problem: Problem) {
    MissingIdDiagnostics.trace("FileRoot", "problemDisappeared:in", MissingIdDiagnostics.STEP_DISAPPEARED, problem, "file=${file.name}")
    if (problem is HighlightingProblem && problem.file == file) {
      notify(problem, synchronized(problems) { SetUpdateState.remove(problem, problems) })
    }
  }

  override fun problemUpdated(problem: Problem) {
    MissingIdDiagnostics.trace("FileRoot", "problemUpdated:in", MissingIdDiagnostics.STEP_UPDATED, problem, "file=${file.name}")
    if (problem is HighlightingProblem && problem.file == file) {
      notify(problem, synchronized(problems) { SetUpdateState.update(problem, problems) })
    }
  }

  private fun notify(problem: Problem, state: SetUpdateState) {
    when (state) {
      SetUpdateState.ADDED -> {
        MissingIdDiagnostics.trace("FileRoot", "set:ADDED", MissingIdDiagnostics.STEP_APPEARED, problem, "file=${file.name}")
        super.problemAppeared(problem)
        problemEventsFlow.problemAppeared(problem as HighlightingProblem)
      }
      SetUpdateState.REMOVED -> {
        MissingIdDiagnostics.trace("FileRoot", "set:REMOVED", MissingIdDiagnostics.STEP_DISAPPEARED, problem, "file=${file.name}")
        super.problemDisappeared(problem)
        problemEventsFlow.problemDisappeared(problem as HighlightingProblem)
      }
      SetUpdateState.UPDATED -> {
        MissingIdDiagnostics.trace("FileRoot", "set:UPDATED", MissingIdDiagnostics.STEP_UPDATED, problem, "file=${file.name}")
        super.problemUpdated(problem)
        problemEventsFlow.problemUpdated(problem as HighlightingProblem)
      }
      SetUpdateState.IGNORED -> {
        MissingIdDiagnostics.trace("FileRoot", "set:IGNORED", MissingIdDiagnostics.STEP_UPDATED, problem, "file=${file.name}")
      }
    }
  }

  override fun getChildren(node: FileNode): Collection<Node> {
    val fileProblems = getFileProblems(node.file)
    val groupByToolId = panel.state.groupByToolId
    return ProblemsViewHighlightingChildrenBuilder.prepareChildrenForFileRoot(fileProblems, node, groupByToolId)
  }

  private class HighlightingProblemEventFlow(scope: CoroutineScope) : FlowWithHistory<ProblemEvent>(scope) {
    private val problems = mutableSetOf<HighlightingProblem>()

    override fun getHistory(): List<ProblemEvent> {
      MissingIdDiagnostics.trace("FileRoot.flow", "history-replay/re-subscription", MissingIdDiagnostics.STEP_SUBSCRIPTION,
                                 "historySize=${problems.size}")
      return problems.map { ProblemEvent.Appeared(it) }
    }

    fun problemAppeared(problem: HighlightingProblem) =
      problemAppearedOrUpdated(
        problem,
        "ProblemEvent.Appeared",
        shouldAlreadyExist = false
      ) { ProblemEvent.Appeared(it) }

    fun problemDisappeared(problem: HighlightingProblem) = updateHistoryAndEmit {
      if (problems.remove(problem)) {
        ProblemEvent.Disappeared(problem)
      }
      else {
        logDroppedEvent("ProblemEvent.Disappeared", problem, "the problem is not in history")
        null
      }
    }

    fun problemUpdated(problem: HighlightingProblem) =
      problemAppearedOrUpdated(
        problem,
        "ProblemEvent.Updated",
        shouldAlreadyExist = true
      ) { ProblemEvent.Updated(it) }

    private fun problemAppearedOrUpdated(
      problem: HighlightingProblem,
      eventName: String,
      shouldAlreadyExist: Boolean,
      eventFactory: (HighlightingProblem) -> ProblemEvent,
    ) = updateHistoryAndEmit {
      if (problems.contains(problem) != shouldAlreadyExist) {
        val reason = when (shouldAlreadyExist) {
          true -> "the problem is not in history"
          false -> "the problem is already in history"
        }
        logDroppedEvent(eventName, problem, reason)
        return@updateHistoryAndEmit null
      }
      problems.remove(problem)
      problems.add(problem)
      eventFactory(problem)
    }

    private fun logDroppedEvent(eventName: String, problem: HighlightingProblem, reason: String) {
      thisLogger().debug("Dropping $eventName event because $reason: $problem")
    }
  }
}
