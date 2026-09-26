// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightSeverityDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.FileProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.GenericProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.PriorityDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.QuickFixDto
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.ide.vfs.rpcId
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Iconable

internal fun convertHighlightingProblemToDto(
  problem: HighlightingProblem,
  problemId: String,
  quickFixes: List<IntentionActionWithIds>
): HighlightingProblemDto {
  val severity = problem.info?.severity ?: HighlightSeverity.INFORMATION
  val severityDto = HighlightSeverityDto(
    name = severity.name,
    value = severity.myVal
  )

  val quickFixDtos = quickFixes.map { convertIntentionActionToDto(it) }

  return HighlightingProblemDto(
    id = problemId,
    text = problem.text,
    line = problem.line,
    column = problem.column,
    severity = severityDto,
    group = problem.group,
    contextGroup = problem.contextGroup?.toString(),
    description = problem.description,
    filePath = problem.file.path,
    iconId = problem.icon.rpcIdOrNull(),
    quickFixes = quickFixDtos,
    quickFixOffset = problem.info?.actualStartOffset ?: -1
  )
}

internal fun convertFileProblemToDto(
  problem: FileProblem,
  problemId: String
): FileProblemDto {
  return FileProblemDto(
    id = problemId,
    text = problem.text,
    description = problem.description ?: "",
    icon = problem.icon.rpcIdOrNull(),
    filePath = problem.file.path,
    fileId = problem.file.rpcId(),
    line = problem.line,
    column = problem.column
  )
}

internal fun convertGenericProblemToDto(
  problem: Problem,
  problemId: String
): GenericProblemDto {
  return GenericProblemDto(
    id = problemId,
    text = problem.text,
    description = problem.description ?: "",
    icon = problem.icon.rpcIdOrNull()
  )
}

private fun convertIntentionActionToDto(intentionWithIds: IntentionActionWithIds): QuickFixDto {
  val action = intentionWithIds.descriptor.action

  val optionDtos = intentionWithIds.options.map { optionWithId ->
    val optionIcon = (optionWithId.action as? Iconable)?.getIcon(0)

    QuickFixDto(
      text = optionWithId.text,
      familyName = optionWithId.familyName,
      intentionId = optionWithId.intentionId,
      options = emptyList(),
      displayName = null,
      iconId = optionIcon?.rpcIdOrNull(),
      hasOptions = false,
      isSelectable = true,
      priority = null
    )
  }

  val hasOptions = optionDtos.isNotEmpty()
  val isSelectable = (action as? CustomizableIntentionAction)?.isSelectable ?: true

  val unwrappedAction = QuickFixWrapper.unwrap(action) ?: action
  val icon = (unwrappedAction as? Iconable)?.getIcon(0)
  val priority = (unwrappedAction as? PriorityAction)?.priority?.let { convertPriority(it) }

  return QuickFixDto(
    text = intentionWithIds.text,
    familyName = intentionWithIds.familyName,
    intentionId = intentionWithIds.intentionId,
    options = optionDtos,
    displayName = intentionWithIds.descriptor.displayName,
    iconId = icon?.rpcIdOrNull(),
    hasOptions = hasOptions,
    isSelectable = isSelectable,
    priority = priority
  )
}

private fun convertPriority(priority: PriorityAction.Priority): PriorityDto {
  return when (priority) {
    PriorityAction.Priority.TOP -> PriorityDto.TOP
    PriorityAction.Priority.HIGH -> PriorityDto.HIGH
    PriorityAction.Priority.NORMAL -> PriorityDto.NORMAL
    PriorityAction.Priority.LOW -> PriorityDto.LOW
    PriorityAction.Priority.BOTTOM -> PriorityDto.BOTTOM
  }
}
