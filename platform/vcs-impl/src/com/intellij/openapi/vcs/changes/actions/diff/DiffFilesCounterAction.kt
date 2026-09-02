// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Non-interactive file counter shown between "Compare Previous File" and "Compare Next File".
 *
 * Used in split mode, where the changes tree lives on the frontend, and the "Go to Changed File" pop-up is not yet implemented due
 * to the high cost of refactoring, and so there is nothing to click.
 * See [PresentableGoToChangePopupAction] for the variant that opens the pop-up.
 */
@ApiStatus.Internal
class DiffFilesCounterAction(
  private val counterSupplier: () -> DiffFilesCounterState?,
) : ToolbarLabelAction() {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    super.createCustomComponent(presentation, place).apply { font = JBUI.Fonts.label() }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)

    val counterState = counterSupplier()
    e.presentation.isVisible = counterState != null
    @Suppress("HardCodedStringLiteral")
    e.presentation.text = counterState?.text.orEmpty()
  }
}
