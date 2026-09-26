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
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.problemsView.frontend.FrontendHighlightingProblem
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import com.intellij.platform.problemsView.shared.ProblemsViewCoroutineScopeHolder
import com.intellij.psi.PsiFile
import com.intellij.platform.project.projectId
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class FrontendIntentionAction : CustomizableIntentionAction, PriorityAction {
  constructor(quickFixDto: QuickFixDto, file: VirtualFile, problemId: String) {
    this.text = quickFixDto.text
    this.familyName = quickFixDto.familyName
    this.isSelectable = quickFixDto.isSelectable
    this.hasOptions = quickFixDto.hasOptions
    this.priority = when (quickFixDto.priority) {
      PriorityDto.TOP -> PriorityAction.Priority.TOP
      PriorityDto.HIGH -> PriorityAction.Priority.HIGH
      PriorityDto.NORMAL -> PriorityAction.Priority.NORMAL
      PriorityDto.LOW -> PriorityAction.Priority.LOW
      PriorityDto.BOTTOM -> PriorityAction.Priority.BOTTOM
      null -> PriorityAction.Priority.NORMAL
    }
    this.intentionId = quickFixDto.intentionId
    this.file = file
    this.problemId = problemId
    this.icon = quickFixDto.iconId?.icon()
  }

  val icon: Icon?
  private val text: String
  private val familyName: String
  private val isSelectable: Boolean
  private val hasOptions: Boolean
  private val priority: PriorityAction.Priority
  private val intentionId: String
  private val file: VirtualFile
  private val problemId: String

  override fun getText(): @IntentionName String = text

  override fun getFamilyName(): @IntentionFamilyName String = familyName

  override fun isSelectable(): Boolean = isSelectable

  override fun isShowSubmenu(): Boolean = hasOptions

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean = true

  override fun startInWriteAction(): Boolean = false

  override fun getPriority(): PriorityAction.Priority = priority

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?, ) {
    val virtualFile = psiFile?.virtualFile ?: file

    ProblemsViewCoroutineScopeHolder.getInstance().launch {
      ProblemsViewApi.getInstance().executeQuickFix(
        project.projectId(),
        virtualFile.rpcId(),
        problemId,
        intentionId
      )
    }
  }
}

@ApiStatus.Internal
fun dtoToIntentionActionDescriptor(quickFixDto: QuickFixDto, problem: FrontendHighlightingProblem): HighlightInfo.IntentionActionDescriptor{
  val intentionAction = FrontendIntentionAction(quickFixDto, problem.file, problem.id)

  val intentionOptions = quickFixDto.options.map { optionDto ->
    FrontendIntentionAction(optionDto, problem.file, problem.id)
  }

  return HighlightInfo.IntentionActionDescriptor(
    intentionAction,
    intentionOptions,
    null,
    intentionAction.icon,
    null,
    null,
    null,
    null
  )
}