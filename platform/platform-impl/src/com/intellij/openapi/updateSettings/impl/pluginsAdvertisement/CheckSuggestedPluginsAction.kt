// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

internal class CheckSuggestedPluginsAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project?.let { TrustedProjects.isProjectTrusted(it) } ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project?.takeIf { TrustedProjects.isProjectTrusted(it) } ?: return

    runWithModalProgressBlocking(project, IdeBundle.message("plugins.advertiser.check.progress")) {
      checkSuggestedPlugins(project = project, includeIgnored = true)
    }
  }
}