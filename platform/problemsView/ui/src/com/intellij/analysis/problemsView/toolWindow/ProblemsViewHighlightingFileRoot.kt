// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingFileRoot
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

  private val _problemEvents = MutableSharedFlow<ProblemEvent>(replay = Int.MAX_VALUE, extraBufferCapacity = Int.MAX_VALUE)
  val problemEvents: Flow<ProblemEvent> = _problemEvents

  private val watcher: ProblemsViewHighlightingWatcher =
    ProblemsViewHighlightingWatcher(provider, this, file, document, HighlightSeverity.TEXT_ATTRIBUTES.myVal + 1)

  val lifetime: ProblemLifetime

  init {
    Disposer.register(this, provider)
    lifetime = HighlightingProblemsLifetimeService.getInstance(panel.project).createRootLifetime(this)
  }

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

  @OptIn(ExperimentalCoroutinesApi::class)
  fun resetEventReplayCache() {
    _problemEvents.resetReplayCache()
    thisLogger().debug("reset replay cache for file: ${file.name}")
  }

  override fun problemAppeared(problem: Problem) {
    if (problem !is HighlightingProblem || problem.file != file) {
      return
    }
    notify(problem = problem, state = synchronized(problems) { SetUpdateState.add(problem, problems) })
  }

  override fun problemDisappeared(problem: Problem) {
    if (problem is HighlightingProblem && problem.file == file) {
      notify(problem, synchronized(problems) { SetUpdateState.remove(problem, problems) })
    }
  }

  override fun problemUpdated(problem: Problem) {
    if (problem is HighlightingProblem && problem.file == file) {
      notify(problem, synchronized(problems) { SetUpdateState.update(problem, problems) })
    }
  }

  private fun notify(problem: Problem, state: SetUpdateState) {
    when (state) {
      SetUpdateState.ADDED -> {
        super.problemAppeared(problem)
        _problemEvents.tryEmit(
          ProblemEvent.Appeared(problem as HighlightingProblem)
        )
      }
      SetUpdateState.REMOVED -> {
        super.problemDisappeared(problem)
        _problemEvents.tryEmit(
          ProblemEvent.Disappeared(problem as HighlightingProblem)
        )
      }
      SetUpdateState.UPDATED -> {
        super.problemUpdated(problem)
        _problemEvents.tryEmit(
          ProblemEvent.Updated(problem as HighlightingProblem)
        )
      }
      SetUpdateState.IGNORED -> {
      }
    }
  }

  override fun getChildren(node: FileNode): Collection<Node> {
    val fileProblems = getFileProblems(node.file)
    val groupByToolId = panel.state.groupByToolId
    return ProblemsViewHighlightingChildrenBuilder.prepareChildrenForFileRoot(fileProblems, node, groupByToolId)
  }
}
