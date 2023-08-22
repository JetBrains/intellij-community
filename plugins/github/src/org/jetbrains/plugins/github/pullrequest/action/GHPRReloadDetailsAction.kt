// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import java.util.function.Supplier

class GHPRReloadDetailsAction
  : RefreshAction(GithubBundle.messagePointer("pull.request.refresh.details.action"),
                  Supplier { null },
                  AllIcons.Actions.Refresh) {

  override fun update(e: AnActionEvent) {
    val selection = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    e.presentation.isEnabled = selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataProvider: GHPRDataProvider = e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    dataProvider.detailsData.reloadDetails()
    dataProvider.stateData.reloadMergeabilityState()
    dataProvider.reviewData.resetPendingReview()
    dataProvider.changesData.reloadChanges()
    dataProvider.viewedStateData.reset()
  }
}