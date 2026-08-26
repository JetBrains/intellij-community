// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.PriorityDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.QuickFixDto
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.openapi.util.Iconable
import com.intellij.platform.problemsView.backend.actions.IntentionActionWithIds

internal fun convertIntentionActionToDto(intentionWithIds: IntentionActionWithIds): QuickFixDto {
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
      priority = null,
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
    priority = priority,
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
