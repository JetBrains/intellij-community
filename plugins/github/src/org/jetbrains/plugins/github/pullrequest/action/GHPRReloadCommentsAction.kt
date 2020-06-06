// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.util.function.Supplier

class GHPRReloadCommentsAction
  : RefreshAction(GithubBundle.messagePointer("pull.request.refresh.comments.action"),
                  Supplier<String?> { null },
                  AllIcons.Actions.Refresh) {

  override fun update(e: AnActionEvent) {
    val selection = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    e.presentation.isEnabled = selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER).reviewData.resetReviewThreads()
  }
}