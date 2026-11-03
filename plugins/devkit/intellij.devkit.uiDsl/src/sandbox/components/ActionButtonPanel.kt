// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.actionsButton
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.Icon
import javax.swing.JComponent

private const val TOOLBAR_PLACE: @NonNls String = "UISandbox.ActionButton"

internal class ActionButtonPanel : UISandboxPanel {

  override val title: String = "ActionButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.action.button.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.action.button.group.toolbar")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.action.button.toolbar.note"))
        }
        row {
          cell(createToolbar(
            SampleAction(DevkitUiDslBundle.message("sandbox.action.button.action.add"), AllIcons.General.Add),
            SampleAction(DevkitUiDslBundle.message("sandbox.action.button.action.edit"), AllIcons.Actions.Edit),
            Separator.getInstance(),
            SampleAction(DevkitUiDslBundle.message("sandbox.action.button.action.refresh"), AllIcons.Actions.Refresh),
          ))
        }
      }

      group(DevkitUiDslBundle.message("sandbox.action.button.group.states")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.action.button.states.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.action.button.label.default")) {
          cell(createToolbar(ShowDetailsAction(selected = false, enabled = true)))
        }
        row(DevkitUiDslBundle.message("sandbox.action.button.label.selected")) {
          cell(createToolbar(ShowDetailsAction(selected = true, enabled = true)))
            .comment(DevkitUiDslBundle.message("sandbox.action.button.comment.toggle"))
        }
        row(DevkitUiDslBundle.message("sandbox.action.button.label.disabled")) {
          cell(createToolbar(ShowDetailsAction(selected = false, enabled = false)))
        }
      }

      group(DevkitUiDslBundle.message("sandbox.action.button.group.ui.dsl")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.action.button.ui.dsl.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.action.button.label.one.action")) {
          actionButton(SampleAction(DevkitUiDslBundle.message("sandbox.action.button.action.refresh"), AllIcons.Actions.Refresh))
        }
        row(DevkitUiDslBundle.message("sandbox.action.button.label.menu")) {
          actionsButton(
            SampleAction(DevkitUiDslBundle.message("sandbox.action.button.action.add"), null),
            SampleAction(DevkitUiDslBundle.message("sandbox.action.button.action.edit"), null),
          )
        }
      }
    }
  }

  private fun createToolbar(vararg actions: AnAction): JComponent {
    val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, DefaultActionGroup(*actions), true)
    toolbar.targetComponent = toolbar.component
    return toolbar.component
  }
}

private class SampleAction(text: @NlsActions.ActionText String, icon: Icon?) : DumbAwareAction(text, null, icon) {
  override fun actionPerformed(e: AnActionEvent) {
  }
}

private class ShowDetailsAction(private var selected: Boolean, private val enabled: Boolean) : DumbAwareToggleAction(
  DevkitUiDslBundle.message("sandbox.action.button.action.show.details"),
  null,
  AllIcons.Actions.PreviewDetails,
) {
  override fun isSelected(e: AnActionEvent): Boolean = selected

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    selected = state
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = enabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
