// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel

class GHPRUpdateTimelineAction
  : RefreshAction({ GithubBundle.message("pull.request.timeline.refresh.action") },
                  { GithubBundle.message("pull.request.timeline.refresh.action.description") },
                  AllIcons.Actions.Refresh) {
  override fun update(e: AnActionEvent) {
    val vm = e.getData(GHPRTimelineViewModel.DATA_KEY)
    e.presentation.isEnabled = vm != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GHPRTimelineViewModel.DATA_KEY).updateAll()
  }
}