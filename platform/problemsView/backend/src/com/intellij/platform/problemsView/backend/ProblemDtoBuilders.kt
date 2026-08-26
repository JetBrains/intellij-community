// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.splitApi.FileProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.GenericProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.isActive

private val LOG = Logger.getInstance("com.intellij.platform.problemsView.backend.ProblemDtoBuilders")

internal fun buildChangelistFromEventsBatch(
  eventsBatch: List<ProblemEvent>,
  project: Project,
  lifetime: ProblemLifetime
): List<ProblemEventDto> {
  return eventsBatch.mapNotNull { event ->
    if(!lifetime.isActive()) {
      LOG.warn("Lifetime is no longer active, skipping any remaining events associated with this scope: $lifetime")
      return emptyList()
    }

    when (event) {
      is ProblemEvent.Appeared -> {
        val problemDto = buildProblemDto(event.problem, lifetime, project)
        ProblemEventDto.Appeared(problemDto)
      }
      is ProblemEvent.Disappeared -> {
        val problemId = ProblemLifetimeManager.getInstance(project).removeProblemId(event.problem, lifetime)
        if (problemId == null) {
          logMissingIdErrorWithDiagnostic(
            problem = event.problem,
            lifetime = lifetime
          )
          return@mapNotNull null
        }
        ProblemEventDto.Disappeared(problemId)
      }
      is ProblemEvent.Updated -> {
        val problemDto = buildProblemDto(event.problem, lifetime, project)
        ProblemEventDto.Updated(problemDto)
      }
    }
  }
}

internal fun buildFileProblemDto(problem: FileProblem, lifetime: ProblemLifetime, project: Project): FileProblemDto {
  val lifecycleManager = ProblemLifetimeManager.getInstance(project)
  val problemId = lifecycleManager.getOrCreateProblemId(problem, lifetime)
  return convertFileProblemToDto(problem, problemId)
}

internal fun buildGenericProblemDto(problem: Problem, lifetime: ProblemLifetime, project: Project): GenericProblemDto {
  val lifecycleManager = ProblemLifetimeManager.getInstance(project)
  val problemId = lifecycleManager.getOrCreateProblemId(problem, lifetime)
  return convertGenericProblemToDto(problem, problemId)
}

internal fun buildProblemDto(problem: Problem, lifetime: ProblemLifetime, project: Project): ProblemDto {
  return when (problem) {
    is FileProblem -> buildFileProblemDto(problem, lifetime, project)
    else -> buildGenericProblemDto(problem, lifetime, project)
  }
}

private fun logMissingIdErrorWithDiagnostic(problem: Problem, lifetime: ProblemLifetime){
  val message = buildString {
    appendLine("Problem ID not found for Disappeared event.")

    appendLine("Problem:")
    appendLine("  type=${problem.javaClass.name}")
    appendLine("  hashCode=${problem.hashCode()}")
    appendLine("  text='${problem.text.take(100)}${if (problem.text.length > 100) "..." else ""}'")
    if (problem is FileProblem) {
      appendLine("  file='${problem.file.path}', line=${problem.line}, column=${problem.column}")
    }
    appendLine("Lifetime: active=${lifetime.coroutineScope.isActive}, scope=${lifetime.coroutineScope}")
  }

  LOG.debug(message)
}
