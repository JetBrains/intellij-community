// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.vcs.VcsShowToolWindowTabAction
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsLogContentProvider
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.VcsLogUtil

class VcsShowLogAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = VcsLogContentProvider.TAB_NAME

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (project != null) {
      val providers = VcsProjectLog.getLogProviders(project)
      val vcsName = VcsLogUtil.getVcsDisplayName(project, providers.values)
      e.presentation.text = VcsLogBundle.message("action.Vcs.Show.Log.text", vcsName)
    }
  }
}