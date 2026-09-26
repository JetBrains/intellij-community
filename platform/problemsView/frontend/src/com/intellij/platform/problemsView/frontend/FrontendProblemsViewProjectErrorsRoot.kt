// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.platform.problemsView.shared.ProblemsViewCoroutineScopeHolder
import com.intellij.analysis.problemsView.toolWindow.Root
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemDto
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import com.intellij.platform.project.projectId
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class FrontendProblemsViewProjectErrorsRoot(
  panel: ProblemsViewPanel,
  private val project: Project
) : Root(panel), FrontendProblemsViewRoot, Disposable {

  private val cs: CoroutineScope =
    ProblemsViewCoroutineScopeHolder
      .getInstance()
      .createNamedChildScope("Frontend Problems View Project Errors")

  private val problems = ConcurrentHashMap<String, FrontendProblemWithId>()

  init {
    cs.launch {
      subscribeToBackendEvents()
    }
  }

  private suspend fun subscribeToBackendEvents() {
    ProblemsViewApi.getInstance()
      .getProjectErrorsFlow(project.projectId())
      .collect { events ->
        handleProblemEvents(events)
        updateUI()
      }
  }

  override fun handleProblemAppeared(problemDto: ProblemDto) {
    val problem = convertDtoToProjectProblem(problemDto, project) ?: return
    problems[problem.id] = problem
    super.problemAppeared(problem)
  }

  override fun handleProblemDisappeared(problemId: String) {
    val problem = problems.remove(problemId)
    if (problem != null) {
      super.problemDisappeared(problem)
    }
  }

  override fun handleProblemUpdated(problemDto: ProblemDto) {
    val problem = convertDtoToProjectProblem(problemDto, project) ?: return
    problems[problem.id] = problem
    super.problemUpdated(problem)
  }

  private fun updateUI() {
    panel.treeModel.structureChanged(null)
  }

  override fun getProblemCount(): Int {
    return problems.values.count { it is FileProblem }
  }

  override fun getProblemFiles(): Collection<VirtualFile> {
    return problems.values
      .filterIsInstance<FileProblem>()
      .map { it.file }
      .distinct()
  }

  override fun getFileProblemCount(file: VirtualFile): Int {
    return problems.values.count { problem ->
      problem is FileProblem && problem.file == file
    }
  }

  override fun getFileProblems(file: VirtualFile): Collection<Problem> {
    return problems.values.filter { problem ->
      problem is FileProblem && problem.file == file
    }
  }

  override fun getOtherProblemCount(): Int {
    return problems.values.count { it !is FileProblem }
  }

  override fun getOtherProblems(): Collection<Problem> {
    return problems.values.filter { it !is FileProblem }
  }

  override fun dispose() {
    cs.cancel()
    problems.clear()
  }
}
