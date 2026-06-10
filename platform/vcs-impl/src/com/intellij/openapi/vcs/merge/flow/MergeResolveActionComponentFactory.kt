// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.openapi.vcs.merge.MergeResolveActionPresentation
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vcs.merge.MergeResolveActionSupport
import javax.swing.JButton
import javax.swing.JComponent

internal const val ONE_SHOT_MERGE_DIALOG_ACTION_PLACE: String = "Merge.OneShotDialog"
internal const val ITERATIVE_MERGE_DIALOG_ACTION_PLACE: String = "Merge.Dialog.Iterative"

internal class MergeResolveActionComponentController(
  val component: JComponent,
  private val updater: () -> Unit,
) {
  fun update() {
    updater()
    component.parent?.revalidate()
    component.parent?.repaint()
  }
}

internal fun createMergeResolveActionComponentControllers(
  mergeContext: MergeResolveActionContext,
  place: String,
): List<MergeResolveActionComponentController> {
  // Merge dialogs build their UI before the tree applies initial selection. Keep every
  // contributed component and let delegates refresh it when merge selection changes.
  return MergeResolveActionProvider.EP_NAME.extensionList
    .sortedBy(MergeResolveActionProvider::order)
    .map { provider -> createResolveActionComponentController(provider, mergeContext, place) }
}

private fun createResolveActionComponentController(
  provider: MergeResolveActionProvider,
  mergeContext: MergeResolveActionContext,
  place: String,
): MergeResolveActionComponentController {
  val action = provider.action
  return if (action is CustomComponentAction) {
    createResolveActionCustomComponentController(action, action, mergeContext, place)
  }
  else {
    createResolveActionButtonController(provider, mergeContext, place)
  }
}

private fun createResolveActionButtonController(
  provider: MergeResolveActionProvider,
  mergeContext: MergeResolveActionContext,
  place: String,
): MergeResolveActionComponentController {
  val button = JButton()
  val controller = MergeResolveActionComponentController(button) {
    updateResolveActionButton(provider, mergeContext, place, button)
  }
  button.addActionListener {
    MergeResolveActionSupport.performAction(provider, mergeContext, button, place)
    controller.update()
  }
  controller.update()
  return controller
}

private fun createResolveActionCustomComponentController(
  customComponentAction: CustomComponentAction,
  action: AnAction,
  mergeContext: MergeResolveActionContext,
  place: String,
): MergeResolveActionComponentController {
  val presentation = MergeResolveActionSupport.getUpdatedPresentation(action, mergeContext, null, place)
                     ?: action.templatePresentation.clone().apply { isVisible = false }
  val component = customComponentAction.createCustomComponent(presentation, place)
  customComponentAction.updateCustomComponent(component, presentation)
  val wrappedComponent = wrapResolveActionComponent(component, mergeContext)
  return MergeResolveActionComponentController(wrappedComponent) {
    updateResolveActionCustomComponent(customComponentAction, action, component, wrappedComponent, mergeContext, place)
  }.also { it.update() }
}

private fun updateResolveActionCustomComponent(
  action: CustomComponentAction,
  anAction: AnAction,
  component: JComponent,
  wrappedComponent: JComponent,
  mergeContext: MergeResolveActionContext,
  place: String,
) {
  val presentation = MergeResolveActionSupport.getUpdatedPresentation(anAction, mergeContext, component, place)
  if (presentation == null) {
    component.isVisible = false
    wrappedComponent.isVisible = false
    return
  }

  component.isVisible = true
  action.updateCustomComponent(component, presentation)
  wrappedComponent.isVisible = component.isVisible
}

private fun updateResolveActionButton(
  provider: MergeResolveActionProvider,
  mergeContext: MergeResolveActionContext,
  place: String,
  button: JButton,
) {
  syncResolveActionButton(button, MergeResolveActionSupport.createActionPresentation(provider, mergeContext, button, place))
}

private fun syncResolveActionButton(button: JButton, presentation: MergeResolveActionPresentation?) {
  if (presentation == null) {
    button.isVisible = false
    return
  }

  button.apply {
    text = presentation.text
    icon = presentation.icon
    setToolTipText(presentation.description?.let(HtmlChunk::text))
    isEnabled = presentation.isEnabled
    isVisible = true
  }
}

private fun wrapResolveActionComponent(
  component: JComponent,
  mergeContext: MergeResolveActionContext,
): JComponent {
  return UiDataProvider.wrapComponent(component) { sink ->
    sink[CommonDataKeys.PROJECT] = mergeContext.project
    sink[PlatformCoreDataKeys.CONTEXT_COMPONENT] = component
    sink[MergeResolveActionContext.KEY] = mergeContext
  }
}
