// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.FileNode
import com.intellij.analysis.problemsView.toolWindow.Node
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewHighlightingChildrenBuilder
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.Root
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingFileRoot
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemDto
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import com.intellij.platform.problemsView.shared.ProblemsViewCoroutineScopeHolder
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class FrontendProblemsViewHighlightingFileRoot(
  panel: ProblemsViewPanel,
  override val file: VirtualFile,
  override val document: Document
) : Root(panel), HighlightingFileRoot, FrontendProblemsViewRoot {

  private val cs : CoroutineScope = ProblemsViewCoroutineScopeHolder.getInstance().createNamedChildScope("Frontend Problems View Highlighting File Root ${file.name}")
  private val problems = ConcurrentHashMap<String, FrontendHighlightingProblem>()

  init {
    cs.launch {
      subscribeToBackendEvents()
    }
  }

  private suspend fun subscribeToBackendEvents(){
    ProblemsViewApi.getInstance()
      .getFileProblemsFlow(project.projectId(), file.rpcId())
      .collect { events ->
        handleProblemEvents(events)
        updateUI()
      }
  }

  private fun severityFilter(problem: FrontendHighlightingProblem): Boolean {
    return problem.severity !in panel.state.hideBySeverity
  }

  override fun handleProblemAppeared(problemDto: ProblemDto){
    val problem = convertDtoToHighlightingProblem(problemDto, file, project) ?: return
    problems[problem.id] = problem
    super.problemAppeared(problem)
  }

  override fun handleProblemDisappeared(problemId: String){
    val problem = problems.remove(problemId)
    if (problem != null) {
      super.problemDisappeared(problem)
    }
  }

  override fun handleProblemUpdated(problemDto: ProblemDto){
    val problem = convertDtoToHighlightingProblem(problemDto, file, project) ?: return
    problems[problem.id] = problem
    super.problemUpdated(problem)
  }

  private fun updateUI() {
    panel.treeModel.structureChanged(null)
    (panel as? FrontendHighlightingPanel)?.powerSaveStateChanged()
  }

  override fun getProblemCount(): Int {
    return problems.values.count(::severityFilter)
  }

  override fun getFileProblems(file: VirtualFile): List<FrontendHighlightingProblem> {
    return when (this.file == file) {
      true -> problems.values.filter(::severityFilter)
      else -> emptyList()
    }
  }

  override fun getProblemFiles(): Collection<VirtualFile> {
    return when (getProblemCount() > 0) {
      true -> listOf(file)
      else -> emptyList()
    }
  }

  override fun getFileProblemCount(file: VirtualFile): Int {
    return when (this.file == file) {
      true -> getProblemCount()
      else -> 0
    }
  }

  override fun getOtherProblemCount(): Int = 0

  override fun getOtherProblems(): Collection<Problem> = emptyList()

  override fun getChildren(node: FileNode): Collection<Node> {
    val fileProblems = getFileProblems(node.file)
    val groupByToolId = panel.state.groupByToolId
    return ProblemsViewHighlightingChildrenBuilder.prepareChildrenForFileRoot(fileProblems, node, groupByToolId)
  }

  override fun dispose() {
    cs.cancel("Frontend root disposed")
    super.dispose()
  }
}


