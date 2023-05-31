// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.DumbAwareAction

internal class CheckSuggestedPluginsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    runBlockingModal(project, IdeBundle.message("plugins.advertiser.check.progress")) {
      PluginsAdvertiserStartupActivity().checkSuggestedPlugins(project = project, includeIgnored = true)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}