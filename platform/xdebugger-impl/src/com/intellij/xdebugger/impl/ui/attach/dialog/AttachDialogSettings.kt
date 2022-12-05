package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ExperimentalUI
import com.intellij.xdebugger.XDebuggerBundle

class AttachDialogSettings : DefaultActionGroup(
  null,
  XDebuggerBundle.message(
    "XDebugger.Attach.Dialog.Settings.text"),
  if (ExperimentalUI.isNewUI())
    AllIcons.Actions.More
  else
    AllIcons.General.GearPlain), RightAlignedToolbarAction, TooltipDescriptionProvider, DumbAware {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getChildren(e).any()
  }
}