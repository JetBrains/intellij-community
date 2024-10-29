// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.GotItTooltipService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class TipAndTrickManagerImpl : TipAndTrickManager {
  private var openedDialog: TipDialog? = null

  override suspend fun showTipDialog(project: Project?) = showTipDialog(project = project, tips = TipAndTrickBean.EP_NAME.extensionList)

  override suspend fun showTipDialog(project: Project, tip: TipAndTrickBean) = showTipDialog(project = project, tips = listOf(tip))

  private suspend fun showTipDialog(project: Project?, tips: List<TipAndTrickBean>) {
    val sortingResult = if (tips.size > 1) {
      TipOrderUtil.getInstance().sort(tips, project).also { result ->
        val tipsUsageManager = TipsUsageManager.getInstance()
        if (!tipsUsageManager.wereTipsShownToday()) {
          tipsUsageManager.fireTipProposed(result.tips[0])
        }
      }
    }
    else {
      TipsSortingResult.create(tips)
    }
    withContext(Dispatchers.EDT) {
      if (project?.isDisposed != true) {
        closeTipDialog()
        val dialog = TipDialog(project, sortingResult)
        openedDialog = dialog
        // clear link to not leak the project
        Disposer.register(dialog.disposable, Disposable { openedDialog = null })
        dialog.showWhenTipInstalled()
      }
    }
  }

  override fun closeTipDialog() {
    openedDialog?.close(DialogWrapper.OK_EXIT_CODE)
  }

  override fun canShowDialogAutomaticallyNow(project: Project): Boolean {
    return Registry.`is`("tips.of.the.day.force.show", false)
           || (GeneralSettings.getInstance().isShowTipsOnStartup
               && !TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT.get(project, false)
               && !GotItTooltipService.getInstance().isFirstRun
               && (openedDialog?.isVisible != true)
               && !TipsUsageManager.getInstance().wereTipsShownToday())
  }
}