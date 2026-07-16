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
import com.intellij.openapi.diagnostic.Attachment
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
            lifetime = lifetime,
            project = project,
            eventsBatch = eventsBatch
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

private fun logMissingIdErrorWithDiagnostic(problem: Problem, lifetime: ProblemLifetime, project: Project, eventsBatch: List<ProblemEvent>){
  val message = buildString {
    append("Problem ID not found for Disappeared event. ")
    append("Problem: text='${problem.text.take(100)}${if (problem.text.length > 100) "..." else ""}', ")
    append("hashCode=${problem.hashCode()}, ")
    if (problem is FileProblem) {
      append(", file='${problem.file.path}'")
    }
    append(", batchSize=${eventsBatch.size}, ")
    append("lifetime.active=${lifetime.coroutineScope.isActive}")
  }

  if (!LOG.isDebugEnabled) {
    LOG.error(message)
    return
  }

  val stackTrace = Attachment("problem-ids-stacktrace.txt", Throwable("Call stack for missing problem ID"))

  val problemDetails = buildString {
    appendLine("Type: ${problem.javaClass.name}")
    appendLine("Text: ${problem.text}")
    appendLine("HashCode: ${problem.hashCode()}")
    appendLine()
    if (problem is FileProblem) {
      appendLine("File Path: ${problem.file.path}")
      appendLine("File Name: ${problem.file.name}")
      appendLine("Line: ${problem.line}")
      appendLine("Column: ${problem.column}")
    }
    if (problem is HighlightingProblem) {
      appendLine("Highlighter HashCode: ${problem.highlighter.hashCode()}")
    }

    appendLine()
    appendLine("Lifetime:")
    appendLine("  Scope: ${lifetime.coroutineScope}")
    appendLine("  Scope Active: ${lifetime.coroutineScope.isActive}")

  }
  val problemAttachment = Attachment("problem-details.txt", problemDetails)

  val idManagerState = ProblemLifetimeManager.getInstance(project).getDiagnosticSnapshot()
  val idManagerAttachment = Attachment("lifetime-manager-state.txt", idManagerState)

  LOG.error(message, stackTrace, problemAttachment, idManagerAttachment)
}
