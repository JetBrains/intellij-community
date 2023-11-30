// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.UsageView
import com.intellij.usages.impl.UsageViewCoroutineScopeProvider
import com.intellij.usages.impl.UsageViewImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author gregsh
 */
class RerunSearchAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val usageView = e.getData(UsageView.USAGE_VIEW_KEY)
    if (usageView is UsageViewImpl) {
      UsageViewCoroutineScopeProvider.getInstance(project).coroutineScope.launch {
        if (!readAction { usageView.canPerformReRun() }) {
          withContext(Dispatchers.EDT) {
            Messages.showErrorDialog(e.project, UsageViewBundle.message("dialog.message.targets.have.been.invalidated"), UsageViewBundle.message("dialog.title.cannot.re.run.search"))
          }
          return@launch
        }

        withContext(Dispatchers.EDT) {
          usageView.refreshUsages()
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val usageView = e.getData(UsageView.USAGE_VIEW_KEY)
    e.presentation.setEnabledAndVisible(usageView is UsageViewImpl && usageView.canPerformReRun())
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
