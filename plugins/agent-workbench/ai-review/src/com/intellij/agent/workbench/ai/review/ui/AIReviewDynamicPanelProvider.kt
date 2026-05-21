// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.agent.workbench.ai.review.model.AIReviewRequest
import com.intellij.agent.workbench.ai.review.model.AIReviewSession
import com.intellij.agent.workbench.ai.review.model.AIReviewViewModel
import com.intellij.agent.workbench.ai.review.model.AIReviewViewModel.State
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.toolWindow.CollectorBasedRoot
import com.intellij.analysis.problemsView.toolWindow.FileNode
import com.intellij.analysis.problemsView.toolWindow.Node
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanelProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.util.ui.StatusText.DEFAULT_ATTRIBUTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.ActionListener
import java.util.Collections

/**
 * Dynamic panel provider for a single AI review session tab in the Problems tool window.
 * Each session creates its own panel with independent state, tree model, and problems storage.
 */
@ApiStatus.Internal
class AIReviewDynamicPanelProvider(
  private val project: Project,
  private val session: AIReviewSession,
  private val tabName: @Nls String,
) : ProblemsViewPanelProvider {

  override fun create(): ProblemsViewTab {
    val state = ProblemsViewState()
    val panel = AIReviewProblemsViewPanel(project, session.cs, session.sessionId, state, tabName, session)

    setDisplayWelcomeText(panel)
    val treeModel = panel.treeModel
    val storage = AIReviewProblemsStorage(state)
    treeModel.root = AIReviewProblemRootNode(panel, storage)

    session.cs.launch {
      session.viewModel.state.collectLatest { viewState ->
        val review = session.viewModel
        if (viewState is State.Running) {
          storage.clear()
          session.problemsHolder.clear()
        }
        val newProblems = review.problems.value
        session.problemsHolder.addProblems(newProblems)
        val newProblemsNodes = createProblemsNodes(project, review)
        storage.addProblems(newProblemsNodes)
        applyStateToUi(project, panel, viewState, storage)
      }
    }

    return panel
  }
}

private suspend fun applyStateToUi(
  project: Project,
  panel: AIReviewProblemsViewPanel,
  state: State,
  storage: AIReviewProblemsStorage,
) {
  withContext(Dispatchers.EDT) {
    val request = (state as? State.RequestHolder)?.request
    with(panel) {
      when (state) {
        is State.NotStarted -> {
          setDisplayWelcomeText(this)
        }
        is State.Running -> tree.isRootVisible = true
        is State.Cancelled -> {
          tree.isRootVisible = false
          updateEmptyText(AIReviewBundle.message("aiReview.problems.analyzing.cancelled"), panel, request)
        }
        is State.Error -> {
          tree.isRootVisible = false
          updateEmptyText(state.message, panel, request)
        }
        is State.WithPartialReview -> {
          updateEmptyText(getNoProblemsFoundEmptyText(storage), panel, request)
        }
        is State.WithFullReview -> {
          tree.isRootVisible = false
          updateEmptyText(getNoProblemsFoundEmptyText(storage), panel, request)
        }
        is State.FilterApplied -> {
          updateEmptyText(getNoProblemsFoundEmptyText(storage), panel, request)
        }
        else -> {
          tree.isRootVisible = false
          updateEmptyText(getNoProblemsFoundEmptyText(storage), panel, request)
        }
      }

      treeModel.structureChanged(null)
      statusPanel.reviewState.update { if (storage.getProblemCount() > 0) state else null }
    }
  }
}

private fun getNoProblemsFoundEmptyText(storage: AIReviewProblemsStorage): @NlsContexts.StatusText String {
  return if (storage.getTotalProblemCount() == 0) AIReviewBundle.message("aiReview.problems.no.problems.found")
  else AIReviewBundle.message("aiReview.problems.no.problems.filters.applied")
}

private fun ProblemsViewPanel.updateEmptyText(
  newText: @NlsContexts.StatusText String,
  panel: AIReviewProblemsViewPanel,
  request: AIReviewRequest? = null,
) {
  tree.emptyText.text = newText

  if (panel.session.canRetryReview(request)) {
    tree.emptyText.appendSecondaryText(AIReviewBundle.message("aiReview.problems.analyzing.retry"), LINK_PLAIN_ATTRIBUTES, ActionListener {
      panel.session.retryReview(request)
    })
  }
}

private fun setDisplayWelcomeText(panel: ProblemsViewPanel) {
  val statusText = panel.tree.emptyText
  statusText.clear()

  val message = AIReviewBundle.message("aiReview.problems.tab.welcome.text")

  // Strip placeholder references for the community version (no VCS/Git dependency)
  val cleanMessage = message
    .replace("<0>", "Commit")
    .replace("<1>", "Version Control")

  @Suppress("HardCodedStringLiteral")
  statusText.appendText(cleanMessage, DEFAULT_ATTRIBUTES)
}

internal class AIReviewProblemsStorage(state: ProblemsViewState) : ProblemsCollector {

  private val filter = AIReviewProblemFilter(state)

  private val problems = Collections.synchronizedList(mutableListOf<AIReviewFileProblem>())

  fun addProblems(newProblems: Collection<AIReviewFileProblem>) {
    problems.addAll(newProblems)
  }

  fun clear() {
    problems.clear()
  }

  override fun getProblemCount(): Int = synchronized(problems) { problems.count { filter(it) } }

  fun getTotalProblemCount(): Int = synchronized(problems) { problems.size }

  override fun getProblemFiles(): Collection<VirtualFile> {
    return synchronized(problems) { problems.asSequence().filter { filter(it) }.map { it.file }.toSet() }
  }

  override fun getFileProblemCount(file: VirtualFile): Int {
    return synchronized(problems) { problems.count { filter(it) && it.file == file } }
  }

  override fun getFileProblems(file: VirtualFile): Collection<Problem> {
    return synchronized(problems) { problems.filter { it.file == file && filter(it) } }
  }

  override fun getOtherProblemCount(): Int = 0

  override fun getOtherProblems(): Collection<Problem> = emptyList()

  override fun problemAppeared(problem: Problem) {
    if (problem is AIReviewFileProblem) {
      problems.add(problem)
    }
  }

  override fun problemDisappeared(problem: Problem) {
    if (problem is AIReviewFileProblem) {
      problems.remove(problem)
    }
  }

  override fun problemUpdated(problem: Problem) {
    if (problem is AIReviewFileProblem) {
      val index = problems.indexOf(problem)
      if (index != -1) {
        problems[index] = problem
      }
    }
  }
}

private class AIReviewProblemRootNode(panel: ProblemsViewPanel, storage: AIReviewProblemsStorage)
  : CollectorBasedRoot(panel, storage) {

  private val durationUpdater = AIReviewDurationUpdater(panel.treeModel, this).also {
    Disposer.register(this, it)
  }

  private val feedbackNode = AIReviewFeedbackNode(this)

  override fun update(project: Project, presentation: PresentationData) {
    if (panel.tree.isRootVisible) {
      durationUpdater.ensureStarted()
    }
    else {
      durationUpdater.stop()
    }
    presentation.addText(AIReviewBundle.message("aiReview.problems.analyzing"), REGULAR_ATTRIBUTES)
    presentation.addText(" " + getElapsedText(), GRAYED_ATTRIBUTES)
    presentation.setIcon(AnimatedIcon.Default.INSTANCE)
  }

  private fun getElapsedText(): @NlsSafe String {
    val ts = durationUpdater.startTimestamp ?: return ""
    var elapsed = System.currentTimeMillis() - ts
    elapsed -= elapsed % 1000
    if (elapsed < 1000) return ""
    return StringUtil.formatDuration(elapsed)
  }

  override fun getChildren(): Collection<Node> {
    val children = super.getChildren()
    val showFeedback = children.isNotEmpty()
    return if (showFeedback) children + feedbackNode else children
  }

  override fun getChildren(node: FileNode): Collection<Node> {
    return getFileProblems(node.file)
      .asSequence()
      .filterIsInstance<AIReviewFileProblem>()
      .map { problem -> AIReviewProblemNode(node, node.file, problem) }
      .toList()
  }
}

private fun createProblemsNodes(project: Project, review: AIReviewViewModel): List<AIReviewFileProblem> {
  val projectRoot = project.guessProjectDir()?.toNioPath()
  val changes = review.changes.value
  val relativePathsToVf = getRelativePathToVirtualFile(changes, projectRoot)

  return review.problems.value
    .mapNotNull { problem -> relativePathsToVf[problem.path]?.let { vf -> AIReviewFileProblem(project, vf, problem) } }
}

/**
 * Maps relative file paths to their VirtualFile representations from the given changes.
 */
private fun getRelativePathToVirtualFile(changes: List<Change>, projectRoot: java.nio.file.Path?): Map<String, VirtualFile> =
  changes.asSequence()
    .mapNotNull { change ->
      val vf = ChangesUtil.getFilePath(change).virtualFile ?: return@mapNotNull null
      val relativePath = if (projectRoot != null) {
        try {
          projectRoot.relativize(vf.toNioPath()).toString().replace('\\', '/')
        }
        catch (_: IllegalArgumentException) {
          null
        }
      }
      else {
        vf.toNioPath().toString().replace('\\', '/')
      }
      relativePath?.let { it to vf }
    }
    .toMap()
