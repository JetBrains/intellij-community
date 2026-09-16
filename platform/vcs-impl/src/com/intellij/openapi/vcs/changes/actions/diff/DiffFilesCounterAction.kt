// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.openapi.util.Key
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

private val HAS_FILES = Key.create<Boolean>("DiffFilesCounterAction.hasFiles")

/**
 * Non-interactive file counter shown between "Compare Previous File" and "Compare Next File".
 *
 * Used in split mode, where the changes tree lives on the frontend, and the "Go to Changed File" pop-up is not yet implemented due
 * to the high cost of refactoring, and so there is nothing to click.
 * See [PresentableGoToChangePopupAction] for the variant that opens the pop-up.
 *
 * @param counterSupplier the values to show, or `null` to hide the label. Use [DiffFilesCounterState.NO_FILES] to show
 * "No files" instead of hiding it.
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
    e.presentation.putClientProperty(HAS_FILES, counterState?.hasFiles == true)
    @Suppress("HardCodedStringLiteral")
    e.presentation.text = counterState?.text.orEmpty()
  }

  /**
   * Paints "No files" in the disabled text color, like the disabled navigation buttons next to it. Monolith mode does
   * the same with a disabled link, see [PresentableGoToChangePopupAction].
   */
  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)

    component.isEnabled = presentation.getClientProperty(HAS_FILES) != false
  }
}
