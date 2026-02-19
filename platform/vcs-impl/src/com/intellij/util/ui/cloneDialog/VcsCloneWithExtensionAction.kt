// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.cloneDialog

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension

abstract class VcsCloneWithExtensionAction : DumbAwareAction() {
  abstract fun getExtension(): Class<out VcsCloneDialogExtension>

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val checkoutListener = ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener

    val dialog = VcsCloneDialog.Builder(project).forExtension(getExtension())

    if (dialog.showAndGet()) {
      dialog.doClone(checkoutListener)
    }
  }

  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}