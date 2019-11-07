// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent

class GHPRReloadListAction : RefreshAction("Refresh List", null, AllIcons.Actions.Refresh) {
  override fun update(e: AnActionEvent) {
    val context = e.getData(GithubPullRequestKeys.ACTION_DATA_CONTEXT)
    e.presentation.isEnabled = context != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GithubPullRequestKeys.ACTION_DATA_CONTEXT).resetAllData()
  }
}