// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend.actions

import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.PriorityDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.QuickFixDto
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.problemsView.frontend.FrontendProblemsViewQuickFixService
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class FrontendIntentionAction internal constructor(
  quickFixDto: QuickFixDto,
  private val quickFixModelId: String,
) : CustomizableIntentionAction, PriorityAction {

  val icon: Icon? = quickFixDto.iconId?.icon()
  private val text: String = quickFixDto.text
  private val familyName: String = quickFixDto.familyName
  private val isSelectable: Boolean = quickFixDto.isSelectable
  private val hasOptions: Boolean = quickFixDto.hasOptions
  private val intentionId: String = quickFixDto.intentionId
  private val priority: PriorityAction.Priority = when (quickFixDto.priority) {
    PriorityDto.TOP -> PriorityAction.Priority.TOP
    PriorityDto.HIGH -> PriorityAction.Priority.HIGH
    PriorityDto.NORMAL -> PriorityAction.Priority.NORMAL
    PriorityDto.LOW -> PriorityAction.Priority.LOW
    PriorityDto.BOTTOM -> PriorityAction.Priority.BOTTOM
    null -> PriorityAction.Priority.NORMAL
  }

  override fun getText(): @IntentionName String = text

  override fun getFamilyName(): @IntentionFamilyName String = familyName

  override fun isSelectable(): Boolean = isSelectable

  override fun isShowSubmenu(): Boolean = hasOptions

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean = true

  override fun startInWriteAction(): Boolean = false

  override fun getPriority(): PriorityAction.Priority = priority

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    FrontendProblemsViewQuickFixService.getInstance(project).executeQuickFix(quickFixModelId, intentionId)
  }
}

internal fun dtoToIntentionActionDescriptor(
  quickFixDto: QuickFixDto,
  quickFixModelId: String,
): HighlightInfo.IntentionActionDescriptor {
  val intentionAction = FrontendIntentionAction(quickFixDto, quickFixModelId)
  val intentionOptions = quickFixDto.options.map { optionDto ->
    FrontendIntentionAction(optionDto, quickFixModelId)
  }
  return createDescriptor(intentionAction, intentionOptions)
}

private fun createDescriptor(
  intentionAction: FrontendIntentionAction,
  intentionOptions: List<FrontendIntentionAction>,
): HighlightInfo.IntentionActionDescriptor {
  return HighlightInfo.IntentionActionDescriptor(
    intentionAction,
    intentionOptions,
    null,
    intentionAction.icon,
    null,
    null,
    null,
    null,
  )
}
