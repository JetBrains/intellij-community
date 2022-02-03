// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction

internal class CheckSuggestedPluginsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
      PluginsAdvertiserStartupActivity().checkSuggestedPlugins(project, true)
    }, IdeBundle.message("plugins.advertiser.check.progress"), true, project)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }
}