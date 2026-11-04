// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SplitModeApiUsage")

package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingFileRoot
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
open class ProblemsViewHighlightingFileRoot(
  panel: ProblemsViewPanel,
  override val file: VirtualFile,
  override val document: Document
) : Root(panel), HighlightingFileRoot {
  private val problems = mutableSetOf<HighlightingProblem>()
  private val filter = ProblemFilter(panel.state)

  private val shownContexts = mutableSetOf<CodeInsightContext?>() // guarded by problems; the problem contexts that the tree can show

  private val provider: ProblemsProvider = object : ProblemsProvider {
    override val project = panel.project
  }

  init {
    Disposer.register(this, provider)
  }

  // without shared sources, a file has one context, and the context rule has nothing to do
  private val visibilityTracker: ProblemsViewContextVisibilityTracker? = when {
    isSharedSourceSupportEnabled(panel.project) -> ProblemsViewContextVisibilityTracker(panel.project, file, document, this, ::updateShownContexts)
    else -> null
  }

  // the watcher delivers the existing highlighters on the EDT at once, so the tracker must exist before it
  private val watcher: ProblemsViewHighlightingWatcher =
    ProblemsViewHighlightingWatcher(provider, this, file, document, HighlightSeverity.TEXT_ATTRIBUTES.myVal + 1)

  @RequiresEdt
  private fun updateShownContexts() {
    // a pooled thread can replace the root while the update runs, and the tree belongs to the new root then
    if (!panel.treeModel.isRoot(this)) return
    val visibility = visibilityTracker?.visibility
    val contexts = synchronized(problems) {
      problems.mapTo(HashSet()) { it.contextGroup }
    }
    val newVisibleContexts = contexts.filterTo(HashSet()) { isVisibleContext(it, visibility) }
    synchronized(problems) {
      if (newVisibleContexts != shownContexts) {
        shownContexts.clear()
        shownContexts.addAll(newVisibleContexts)
      }
      else {
        return
      }
    }
    fileProblemsChanged(file) // also refreshes the tab count
  }

  /** Waits until the root applies the last change of the context visibility. */
  @TestOnly
  suspend fun awaitVisibilityUpdate() {
    visibilityTracker?.awaitUpdate()
  }

  private fun isVisibleContext(context: CodeInsightContext?, visibility: ProblemsViewContextVisibility?): Boolean =
    visibility == null || visibility.isVisible(context) { ProblemsViewContextVisibility.isAnalyzed(panel.project, document, it) }

  private fun isVisible(problem: HighlightingProblem): Boolean =
    filter(problem) && isVisibleContext(problem.contextGroup, visibilityTracker?.visibility)

  /** The filter of one query. It asks the daemon once per context, not once per problem. */
  private fun visibleProblemFilter(): (HighlightingProblem) -> Boolean {
    val visibility = visibilityTracker?.visibility ?: return filter
    val visibleByContext = HashMap<CodeInsightContext?, Boolean>()
    return { problem ->
      filter(problem) && visibleByContext.getOrPut(problem.contextGroup) { isVisibleContext(problem.contextGroup, visibility) }
    }
  }

  override fun findProblem(highlighter: RangeHighlighterEx): Problem? = watcher.findProblem(highlighter)

  override fun getProblemCount(): Int = synchronized(problems) { problems.count(visibleProblemFilter()) }

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
      problems.filter(visibleProblemFilter())
    }
    else -> emptyList()
  }

  override fun getOtherProblemCount(): Int = 0

  override fun getOtherProblems(): Collection<Problem> = emptyList()

  // a problem of a hidden context enters the set silently. The next updateShownContexts shows it when its context becomes visible

  override fun problemAppeared(problem: Problem) {
    if (problem !is HighlightingProblem || problem.file != file) {
      return
    }
    val visible = isVisible(problem)
    val [state, shown] = synchronized(problems) {
      val shown = problem.contextGroup in shownContexts
      if (visible) shownContexts.add(problem.contextGroup)
      SetUpdateState.add(problem, problems) to shown
    }
    when {
      // a repeated report of a problem whose context the tree did not show adds it, as in problemUpdated
      visible && !shown && state == SetUpdateState.UPDATED -> notify(problem, SetUpdateState.ADDED)
      visible -> notify(problem, state)
    }
  }

  override fun problemDisappeared(problem: Problem) {
    if (problem is HighlightingProblem && problem.file == file) {
      val visible = isVisible(problem)
      val [state, shown] = synchronized(problems) { SetUpdateState.remove(problem, problems) to (problem.contextGroup in shownContexts) }
      // the tree can show the problem of a context that became hidden after the last updateShownContexts
      if (visible || shown) notify(problem, state)
    }
  }

  override fun problemUpdated(problem: Problem) {
    if (problem is HighlightingProblem && problem.file == file) {
      val visible = isVisible(problem)
      val [state, shown] = synchronized(problems) {
        val shown = problem.contextGroup in shownContexts
        if (visible) shownContexts.add(problem.contextGroup)
        SetUpdateState.update(problem, problems) to shown
      }
      when {
        // the tree has no node of a problem whose context it did not show, so the update adds it
        visible && !shown && state == SetUpdateState.UPDATED -> notify(problem, SetUpdateState.ADDED)
        visible || shown -> notify(problem, state)
      }
    }
  }

  private fun notify(problem: Problem, state: SetUpdateState) {
    when (state) {
      SetUpdateState.ADDED -> super.problemAppeared(problem)
      SetUpdateState.REMOVED -> super.problemDisappeared(problem)
      SetUpdateState.UPDATED -> super.problemUpdated(problem)
      SetUpdateState.IGNORED -> Unit
    }
  }

  override fun getChildren(node: FileNode): Collection<Node> {
    val fileProblems = getFileProblems(node.file)
    val groupByToolId = panel.state.groupByToolId
    return ProblemsViewHighlightingChildrenBuilder.prepareChildrenForFileRoot(fileProblems, node, groupByToolId)
  }
}
