// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionWithDelegate
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.structureView.frontend.uiModel.StructureTreeAction
import com.intellij.platform.structureView.frontend.uiModel.StructureUiModel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class StructureTreeActionWrapper(protected val myAction: StructureTreeAction, private val myModel: StructureUiModel) : ToggleAction(), DumbAware, ActionWithDelegate<StructureTreeAction>, ActionIdProvider {
  init {
    getTemplatePresentation().setText(myAction.presentation.getText())
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val presentation = e.presentation
    val actionPresentation = myAction.presentation
    if (!e.isFromContextMenu && presentation.getClientProperty(MenuItemPresentationFactory.HIDE_ICON) == null) {
      presentation.setIcon(actionPresentation.getIcon())
    }
    presentation.setText(actionPresentation.getText())
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return myModel.isActionEnabled(myAction)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    myModel.setActionEnabled(myAction, myAction.isReverted != state, false)
  }

  override fun getDelegate(): StructureTreeAction {
    return myAction
  }

  override fun getId(): String = myAction.name
}