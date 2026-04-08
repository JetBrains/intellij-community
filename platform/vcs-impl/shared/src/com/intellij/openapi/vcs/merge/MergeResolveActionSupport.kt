// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
data class MergeResolveActionPresentation(
  val provider: MergeResolveActionProvider,
  val text: @Nls String,
  val description: @Nls String?,
  val icon: Icon?,
  val isEnabled: Boolean,
)

@ApiStatus.Internal
object MergeResolveActionSupport {
  fun collectActionPresentations(
    mergeContext: MergeResolveWithAgentContext,
    contextComponent: JComponent?,
    place: String,
  ): List<MergeResolveActionPresentation> {
    return MergeResolveActionProvider.EP_NAME.extensionList
      .sortedBy(MergeResolveActionProvider::order)
      .mapNotNull { provider -> createActionPresentation(provider, mergeContext, contextComponent, place) }
  }

  fun createActionPresentation(
    provider: MergeResolveActionProvider,
    mergeContext: MergeResolveWithAgentContext,
    contextComponent: JComponent?,
    place: String,
  ): MergeResolveActionPresentation? {
    val presentation = getUpdatedPresentation(provider.action, mergeContext, contextComponent, place) ?: return null

    val text = presentation.text?.takeIf(String::isNotBlank) ?: return null
    return MergeResolveActionPresentation(
      provider = provider,
      text = text,
      description = presentation.description,
      icon = presentation.icon,
      isEnabled = presentation.isEnabled,
    )
  }

  fun getUpdatedPresentation(
    action: AnAction,
    mergeContext: MergeResolveWithAgentContext,
    contextComponent: JComponent?,
    place: String,
  ): Presentation? {
    val event = createActionEvent(action, mergeContext, contextComponent, place, null)
    val updateResult = ActionUtil.updateAction(action, event)
    val presentation = event.presentation
    if (!updateResult.isPerformed || !presentation.isVisible) return null
    return presentation
  }

  fun performAction(
    provider: MergeResolveActionProvider,
    mergeContext: MergeResolveWithAgentContext,
    contextComponent: JComponent?,
    place: String,
    inputEvent: InputEvent? = null,
  ): Boolean {
    val action = provider.action
    val event = createActionEvent(action, mergeContext, contextComponent, place, inputEvent)
    val updateResult = ActionUtil.updateAction(action, event)
    if (!updateResult.isPerformed || !event.presentation.isEnabledAndVisible) return false

    ActionUtil.performAction(action, event)
    return true
  }

  private fun createActionEvent(
    action: AnAction,
    mergeContext: MergeResolveWithAgentContext,
    contextComponent: JComponent?,
    place: String,
    inputEvent: InputEvent?,
  ): AnActionEvent {
    val presentation = action.templatePresentation.clone()
    val dataContext = DataContext { dataId ->
      when {
        CommonDataKeys.PROJECT.`is`(dataId) -> mergeContext.project
        PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> contextComponent
        MergeResolveWithAgentContext.KEY.`is`(dataId) -> mergeContext
        else -> null
      }
    }
    return AnActionEvent.createEvent(action, dataContext, presentation, place, ActionUiKind.NONE, inputEvent)
  }
}
