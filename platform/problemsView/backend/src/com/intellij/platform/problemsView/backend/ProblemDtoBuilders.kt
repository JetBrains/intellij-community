// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.FileProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.analysis.problemsView.toolWindow.splitApi.GenericProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus

internal data class IntentionActionWithOptions(
  val descriptor: HighlightInfo.IntentionActionDescriptor,
  val options: List<IntentionAction>,
  val text: String,
  val familyName: String
)

internal data class IntentionOptionWithId(
  val action: IntentionAction,
  val intentionId: String,
  val text: String,
  val familyName: String
)

internal data class IntentionActionWithIds(
  val descriptor: HighlightInfo.IntentionActionDescriptor,
  val intentionId: String,
  val options: List<IntentionOptionWithId>,
  val text: String,
  val familyName: String
)
private val LOG = Logger.getInstance("com.intellij.platform.problemsView.backend.ProblemDtoBuilders")

@ApiStatus.Internal
suspend fun buildChangelistFromEventsBatch(
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
        val problemId = ProblemLifetimeManager.getInstance(project).removeProblemId(event.problem)
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

internal suspend fun buildHighlightingProblemDto(
  problem: HighlightingProblem,
  lifetime: ProblemLifetime,
  project: Project
): HighlightingProblemDto {
  val lifecycleManager = ProblemLifetimeManager.getInstance(project)
  val problemId = lifecycleManager.getOrCreateHighlightingProblemId(problem, lifetime)

  val quickFixes = getQuickFixesOfProblem(problem).map { intention ->
    val intentionId = lifecycleManager.createIntentionId(intention.descriptor.action, lifetime, problemId)

    val optionsWithIds = intention.options.map { optionAction ->
      val optionId = lifecycleManager.createIntentionId(optionAction, lifetime, problemId)
      IntentionOptionWithId(optionAction, optionId, optionAction.text, optionAction.familyName)
    }

    IntentionActionWithIds(intention.descriptor, intentionId, optionsWithIds, intention.text, intention.familyName)
  }

  return convertHighlightingProblemToDto(problem, problemId, quickFixes)
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

internal suspend fun buildProblemDto(problem: Problem, lifetime: ProblemLifetime, project: Project): ProblemDto {
  return when (problem) {
    is HighlightingProblem -> buildHighlightingProblemDto(problem, lifetime, project)
    is FileProblem -> buildFileProblemDto(problem, lifetime, project)
    else -> buildGenericProblemDto(problem, lifetime, project)
  }
}

private suspend fun getQuickFixesOfProblem(problem: HighlightingProblem): List<IntentionActionWithOptions> = readAction {
  val psiFile = PsiManager.getInstance(problem.provider.project).findFile(problem.file)
  if (psiFile == null) return@readAction emptyList()

  val editor = ProblemsViewEditorUtils.getEditor(psiFile, showEditor = false)
  if (editor == null) return@readAction emptyList()

  val intentionActionsWithOptions = mutableListOf<IntentionActionWithOptions>()

  problem.info?.findRegisteredQuickFix { intentionAction, _ ->
    val action = intentionAction.action
    val isActionAvailable = runCatching {
      action.isAvailable(psiFile.project, editor, psiFile)
    }.getOrDefault(false)

    if (isActionAvailable) {
      val options = intentionAction.getOptions(psiFile, editor).toList()
      intentionActionsWithOptions.add(
        IntentionActionWithOptions(
          descriptor = intentionAction,
          options = options,
          text = action.text,
          familyName = action.familyName
        )
      )
    }
    null
  }

  return@readAction intentionActionsWithOptions
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
    if (problem is HighlightingProblem) {
      val info = problem.info
      appendLine("  highlighterHash=${problem.highlighter.hashCode()}, highlighterValid=${problem.highlighter.isValid}")
      appendLine("  infoPresent=${info != null}, descriptionNull=${info?.description == null}, severity=${problem.severity}")
    }
    appendLine("Lifetime: active=${lifetime.coroutineScope.isActive}, scope=${lifetime.coroutineScope}")
  }

  LOG.debug(message)
}
