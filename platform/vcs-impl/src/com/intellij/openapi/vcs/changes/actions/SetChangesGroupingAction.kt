// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport

abstract class SetChangesGroupingAction : ToggleAction(), DumbAware {
  init {
    isEnabledInModalContext = true
  }
  abstract val groupingKey: String

  override fun update(e: AnActionEvent): Unit = super.update(e).also {
    e.presentation.isEnabledAndVisible = getGroupingSupport(e)?.isAvailable(groupingKey) ?: false
  }

  override fun isSelected(e: AnActionEvent): Boolean = e.presentation.isEnabledAndVisible && getGroupingSupport(e)?.get(groupingKey) ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getGroupingSupport(e)!![groupingKey] = state
  }

  protected fun getGroupingSupport(e: AnActionEvent): ChangesGroupingSupport? = e.getData(ChangesGroupingSupport.KEY)
}

class SetDirectoryChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = "directory"
}

class SetModuleChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = "module"
}