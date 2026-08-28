// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.splitApi.FileProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.GenericProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

private val LOG = Logger.getInstance("com.intellij.platform.problemsView.backend.ProblemDtoBuilders")

internal fun buildChangelistFromEventsBatch(
  eventsBatch: List<ProblemEvent>,
  project: Project,
): List<ProblemEventDto> {
  val idStore = ProjectErrorsIdStore.getInstance(project)

  return eventsBatch.mapNotNull { event ->
    val problem = event.problem
    when (event) {
      is ProblemEvent.Appeared -> {
        val problemId = idStore.getOrCreate(problem)
        ProblemEventDto.Appeared(buildProblemDto(problem, problemId))
      }
      is ProblemEvent.Disappeared -> {
        val problemId = idStore.remove(problem)
        if (problemId == null) {
          logMissingProblemId(problem)
          null
        }
        else {
          ProblemEventDto.Disappeared(problemId)
        }
      }
      is ProblemEvent.Updated -> {
        val problemId = idStore.getOrCreate(problem)
        ProblemEventDto.Updated(buildProblemDto(problem, problemId))
      }
    }
  }
}

internal fun buildProblemDto(problem: Problem, problemId: String): ProblemDto {
  return when (problem) {
    is FileProblem -> convertFileProblemToDto(problem, problemId)
    else -> convertGenericProblemToDto(problem, problemId)
  }
}

private fun convertFileProblemToDto(problem: FileProblem, problemId: String): FileProblemDto {
  return FileProblemDto(
    id = problemId,
    text = problem.text,
    description = problem.description ?: "",
    icon = problem.icon.rpcIdOrNull(),
    filePath = problem.file.path,
    fileId = problem.file.rpcId(),
    line = problem.line,
    column = problem.column,
  )
}

private fun convertGenericProblemToDto(problem: Problem, problemId: String): GenericProblemDto {
  return GenericProblemDto(
    id = problemId,
    text = problem.text,
    description = problem.description ?: "",
    icon = problem.icon.rpcIdOrNull(),
  )
}

private fun logMissingProblemId(problem: Problem) {
  LOG.debug(buildString {
    appendLine("Problem ID not found for Disappeared event.")
    appendLine("Problem:")
    appendLine("  type=${problem.javaClass.name}")
    appendLine("  hashCode=${problem.hashCode()}")
    appendLine("  text='${problem.text.take(100)}${if (problem.text.length > 100) "..." else ""}'")
    if (problem is FileProblem) {
      appendLine("  file='${problem.file.path}', line=${problem.line}, column=${problem.column}")
    }
  })
}
