// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.smartUpdate.SmartUpdateBundle
import com.intellij.smartUpdate.StepOption
import com.intellij.smartUpdate.beforeRestart
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil
import javax.swing.JComponent

internal class UpdateFromSourcesStep: StepOption {
  override val id = "update.from.sources"
  override val stepName: @Nls String = DevKitBundle.message("update.ide.from.sources")
  override val optionName: @Nls String = DevKitBundle.message("update.ide.from.sources.option")
  override val groupName: @Nls String = SmartUpdateBundle.message("update.ide.group")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    if (e == null) { // skip after restart
      onSuccess()
      return
    }
    updateFromSources(project, ::beforeRestart, { Notification("Update from Sources", it, NotificationType.ERROR) }, true)
  }

  override fun isAvailable(project: Project) = PsiUtil.isIdeaProject(project)

  override fun getDetailsComponent(project: Project): JComponent {
    return panel {
      optionsPanel(project, UpdateFromSourcesSettings.getState()).apply { childComponent.setMinimumAndPreferredWidth(-1) }
    }
  }
}