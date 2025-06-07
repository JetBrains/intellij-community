// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.smartupdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.smartUpdate.SmartUpdateBundle
import com.intellij.smartUpdate.StepOption
import com.intellij.smartUpdate.beforeRestart
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.actions.updateFromSources.UpdateFromSourcesSettings
import org.jetbrains.idea.devkit.actions.updateFromSources.optionsPanel
import org.jetbrains.idea.devkit.actions.updateFromSources.updateFromSources
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
    updateFromSources(project, ::beforeRestart, restartAutomatically = true)
  }

  override fun isAvailable(project: Project) = IntelliJProjectUtil.isIntelliJPlatformProject(project)

  override fun getDetailsComponent(project: Project): JComponent = panel {
    optionsPanel(project, UpdateFromSourcesSettings.getState()).apply { childComponent.setMinimumAndPreferredWidth(-1) }
  }
}