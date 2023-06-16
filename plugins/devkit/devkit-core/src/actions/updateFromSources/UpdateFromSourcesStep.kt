// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager
import com.intellij.smartUpdate.SmartUpdateStep
import com.intellij.smartUpdate.beforeRestart
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil

class UpdateFromSourcesStep: SmartUpdateStep {
  override val id = "update.from.sources"
  override val stepName: @Nls String = DevKitBundle.message("update.ide.from.sources")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    if (e == null) { // skip after restart
      onSuccess()
      return
    }
    updateFromSources(project, ::beforeRestart) { Notification("Update from Sources", it, NotificationType.ERROR) }
  }

  override fun isAvailable(project: Project) = ExternalUpdateManager.ACTUAL == null && PsiUtil.isIdeaProject(project)
}