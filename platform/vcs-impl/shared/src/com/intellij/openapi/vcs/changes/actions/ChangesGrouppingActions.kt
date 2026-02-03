// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import org.jetbrains.annotations.NonNls

internal class SelectChangesGroupingActionGroup : DefaultActionGroup(),
                                                  DumbAware,
                                                  ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun update(e: AnActionEvent) {
    e.presentation.isPopupGroup = e.isFromActionToolbar
    e.presentation.isEnabled = getGroupingSupport(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

abstract class SetChangesGroupingAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  init {
    isEnabledInModalContext = true
  }

  abstract val groupingKey: @NonNls String

  override fun update(e: AnActionEvent): Unit = super.update(e).also {
    e.presentation.isEnabledAndVisible = getGroupingSupport(e)?.isAvailable(groupingKey) ?: false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    getGroupingSupport(e)?.let { it.isAvailable(groupingKey) && it[groupingKey] } ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getGroupingSupport(e)?.let { it[groupingKey] = state }
  }
}

internal class SetDirectoryChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = "directory"
}

internal class SetModuleChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = "module"
}

private fun getGroupingSupport(e: AnActionEvent): ChangesGroupingSupport? = e.getData(ChangesGroupingSupport.KEY)