// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.util.ui.cloneDialog.VcsCloneDialog

class GetFromVersionControlAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val isEnabled = CheckoutProvider.EXTENSION_POINT_NAME.hasAnyExtensions() && Registry.`is`("vcs.use.new.clone.dialog")
    e.presentation.isEnabledAndVisible = isEnabled
    if (!isEnabled)
      return
    e.presentation.icon = if (e.place == ActionPlaces.WELCOME_SCREEN) AllIcons.Vcs.Clone else null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: ProjectManager.getInstance().defaultProject
    val cloneDialog = VcsCloneDialog.Builder(project).forExtension()
    if (cloneDialog.showAndGet()) {
      cloneDialog.doClone()
    }
  }
}
