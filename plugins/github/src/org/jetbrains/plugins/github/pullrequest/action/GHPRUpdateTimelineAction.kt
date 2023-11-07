// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineController

class GHPRUpdateTimelineAction
  : RefreshAction({ GithubBundle.message("pull.request.timeline.refresh.action") },
                  { GithubBundle.message("pull.request.timeline.refresh.action.description") },
                  AllIcons.Actions.Refresh) {
  override fun update(e: AnActionEvent) {
    val ctrl = e.getData(GHPRTimelineController.DATA_KEY)
    e.presentation.isEnabled = ctrl != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val ctrl = e.getRequiredData(GHPRTimelineController.DATA_KEY)
    ctrl.requestUpdate()
  }
}