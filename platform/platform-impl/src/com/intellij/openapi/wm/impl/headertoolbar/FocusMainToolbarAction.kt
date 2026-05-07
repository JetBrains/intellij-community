// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.UIUtil
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

internal class FocusMainToolbarAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val toolbar = findMainToolbar(project) ?: return
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, toolbar)) {
      return
    }
    toolbar.focusFirstItem()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = ExperimentalUI.isNewUI() && e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun findMainToolbar(project: Project): MainToolbar? {
  val rootPane = WindowManager.getInstance().getFrame(project)?.rootPane ?: return null
  return UIUtil.uiTraverser(rootPane).find {
    it is MainToolbar && it.isVisible && it.isShowing
  } as? MainToolbar
}
