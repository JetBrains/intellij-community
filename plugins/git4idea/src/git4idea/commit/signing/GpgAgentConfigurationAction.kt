// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import git4idea.config.GitExecutableManager

internal class GpgAgentConfigurationAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && project.service<GpgAgentConfigurator>().canBeConfigured(project)
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<GpgAgentConfigurator>()?.configure()
  }
}
