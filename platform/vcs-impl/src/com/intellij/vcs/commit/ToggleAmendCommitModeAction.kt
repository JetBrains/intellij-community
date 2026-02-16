// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.actions.commit.getContextCommitWorkflowHandler
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox
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
      description = null // prevents default tooltip on `checkBox`
      text = message("checkbox.amend")
      isVisible = amendCommitHandler?.isAmendCommitModeSupported() == true
      isEnabled = isVisible && amendCommitHandler?.isAmendCommitModeTogglingEnabled == true
      putClientProperty(AMEND_COMMIT_HANDLER, amendCommitHandler)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val mode = getAmendCommitHandler(e)?.commitToAmend
    return mode != null && mode !is CommitToAmend.None
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val handler = getAmendCommitHandler(e)!!
    handler.commitToAmend = if (state) {
      check(handler.commitToAmend is CommitToAmend.None)
      CommitToAmend.Last
    }
    else CommitToAmend.None

    e.project?.let { project ->
      CommitSessionCollector.getInstance(project).logCommitOptionToggled(CommitOption.AMEND, state)
    }
  }

  private fun getAmendCommitHandler(e: AnActionEvent) = e.getContextCommitWorkflowHandler()?.amendCommitHandler

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val checkBox = super.createCustomComponent(presentation, place)
    val amendHandler = presentation.getClientProperty(AMEND_COMMIT_HANDLER)
    val isAmendSpecificCommitSupported = amendHandler?.isAmendSpecificCommitSupported() == true
    return if (isAmendSpecificCommitSupported) ToggleAmendPanel(checkBox, amendHandler) else checkBox.also { installHelpTooltip(it) }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val panel = component as? ToggleAmendPanel
    val checkBox = panel?.checkBox ?: component as JCheckBox
    super.updateCustomComponent(checkBox, presentation)

    panel?.updateFrom(presentation)
  }

  private fun installHelpTooltip(it: JComponent) {
    HelpTooltip()
      .setTitle(templatePresentation.text)
      .setShortcut(getFirstKeyboardShortcutText("Vcs.ToggleAmendCommitMode"))
      .setDescription(templatePresentation.description)
      .installOn(it)
  }

  private class ToggleAmendPanel(val checkBox: JComponent, amendHandler: AmendCommitHandler) :
    JBPanel<ToggleAmendPanel>(HorizontalLayout(JBUI.scale(0))) {

    private val linkLabel = AmendCommitModeDropDownLink(amendHandler)

    init {
      val spaceWidth = checkBox.getFontMetrics(checkBox.font).charWidth(' ')
      checkBox.border = JBUI.Borders.emptyRight(spaceWidth)

      add(checkBox)
      add(linkLabel)
    }

    fun updateFrom(presentation: Presentation) {
      val isAmendSpecificCommitSupported = presentation.getClientProperty(AMEND_COMMIT_HANDLER)?.isAmendSpecificCommitSupported() == true
      isVisible = presentation.isVisible
      isEnabled = presentation.isEnabled
      linkLabel.isVisible = isAmendSpecificCommitSupported
    }
  }
}

private val AMEND_COMMIT_HANDLER = Key.create<AmendCommitHandler>("vcs.amend.commit.handler")