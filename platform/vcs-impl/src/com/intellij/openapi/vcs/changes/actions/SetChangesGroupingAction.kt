// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import org.jetbrains.annotations.ApiStatus

abstract class SetChangesGroupingAction : ToggleAction(), DumbAware {
  init {
    isEnabledInModalContext = true
  }
  abstract val groupingKey: @NlsSafe String

  override fun update(e: AnActionEvent): Unit = super.update(e).also {
    e.presentation.isEnabledAndVisible = getGroupingSupport(e)?.isAvailable(groupingKey) ?: false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    getGroupingSupport(e)?.let { it.isAvailable(groupingKey) && it[groupingKey] } ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getGroupingSupport(e)!![groupingKey] = state
  }

  private fun getGroupingSupport(e: AnActionEvent): ChangesGroupingSupport? = e.getData(ChangesGroupingSupport.KEY)
}

@ApiStatus.Internal
class SetDirectoryChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = "directory" // NON-NLS
}

@ApiStatus.Internal
class SetModuleChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = "module" // NON-NLS
}