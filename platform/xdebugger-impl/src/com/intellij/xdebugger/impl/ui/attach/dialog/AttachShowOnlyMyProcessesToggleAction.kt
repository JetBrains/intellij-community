// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.execution.process.ProcessInfo
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate

@ApiStatus.Internal
class AttachShowOnlyMyProcessesToggleAction :
  DumbAware, ProcessPredicate, ToggleAction(XDebuggerBundle.message("xdebugger.attach.show.only.my.processes")) {

  companion object {
    const val DEFAULT = false
    const val SETTINGS_KEY = "ATTACH_DIALOG_SHOW_ONLY_MY_PROCESSES"
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return isSelected()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    PropertiesComponent.getInstance().setValue(SETTINGS_KEY, state, DEFAULT)
    updateProcesses(e)
  }

  override fun get(): Predicate<ProcessInfo> {
    val showAllProcesses = !isSelected()
    return Predicate { showAllProcesses || it.isOwnedByCurrentUser() == ThreeState.YES }
  }

  private fun isSelected(): Boolean {
    return PropertiesComponent.getInstance().getBoolean(SETTINGS_KEY, DEFAULT)
  }

  private fun updateProcesses(e: AnActionEvent) {
    e.project?.getService(AttachToProcessDialogFactory::class.java)?.getOpenDialog()?.updateProcesses()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
