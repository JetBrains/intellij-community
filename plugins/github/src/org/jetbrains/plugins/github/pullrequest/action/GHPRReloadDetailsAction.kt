// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent

class GHPRReloadDetailsAction : RefreshAction("Refresh Pull Request Details", null, AllIcons.Actions.Refresh) {
  override fun update(e: AnActionEvent) {
    val selection = e.getData(GHPRActionKeys.ACTION_DATA_CONTEXT)?.pullRequestDataProvider
    e.presentation.isEnabled = selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GHPRActionKeys.ACTION_DATA_CONTEXT).pullRequestDataProvider?.reloadDetails()
  }
}