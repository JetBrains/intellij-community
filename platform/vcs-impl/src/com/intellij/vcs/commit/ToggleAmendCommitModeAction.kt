// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.actions.commit.getContextCommitWorkflowHandler
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class ToggleAmendCommitModeAction : CheckboxAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    val amendCommitHandler = getAmendCommitHandler(e)
    with(e.presentation) {
      isVisible = amendCommitHandler?.isAmendCommitModeSupported() == true
      isEnabled = isVisible && amendCommitHandler?.isAmendCommitModeTogglingEnabled == true
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean = getAmendCommitHandler(e)?.isAmendCommitMode == true

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getAmendCommitHandler(e)!!.isAmendCommitMode = state

    e.project?.let { project ->
      CommitSessionCollector.getInstance(project).logCommitOptionToggled(CommitOption.AMEND, state)
    }
  }

  private fun getAmendCommitHandler(e: AnActionEvent) = e.getContextCommitWorkflowHandler()?.amendCommitHandler

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    super.createCustomComponent(presentation, place).also { installHelpTooltip(it) }

  override fun updateCustomComponent(checkBox: JComponent, presentation: Presentation) {
    presentation.text = message("checkbox.amend")
    presentation.description = null // prevents default tooltip on `checkBox`

    super.updateCustomComponent(checkBox, presentation)
  }

  private fun installHelpTooltip(it: JComponent) {
    HelpTooltip()
      .setTitle(templatePresentation.text)
      .setShortcut(getFirstKeyboardShortcutText("Vcs.ToggleAmendCommitMode"))
      .setDescription(templatePresentation.description)
      .installOn(it)
  }
}