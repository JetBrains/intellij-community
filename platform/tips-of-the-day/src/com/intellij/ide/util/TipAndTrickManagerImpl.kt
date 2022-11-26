// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ide.GeneralSettings
import com.intellij.ide.util.TipAndTrickManager.Companion.DISABLE_TIPS_FOR_PROJECT
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.GotItTooltipService

class TipAndTrickManagerImpl : TipAndTrickManager {
  private var openedDialog: TipDialog? = null

  override fun showTipDialog(project: Project?) = showTipDialog(project, TipAndTrickBean.EP_NAME.extensionList)

  override fun showTipDialog(project: Project, tip: TipAndTrickBean) = showTipDialog(project, listOf(tip))

  private fun showTipDialog(project: Project?, tips: List<TipAndTrickBean>) {
    val sortingResult = if (tips.size > 1) {
      TipsOrderUtil.getInstance().sort(tips, project)
    }
    else TipsSortingResult(tips)

    invokeLater {
      if (project?.isDisposed != true) {
        closeTipDialog()
        openedDialog = TipDialog(project, sortingResult).also { dialog ->
          Disposer.register(dialog.disposable, Disposable { openedDialog = null })  // clear link to not leak the project
          dialog.show()
        }
      }
    }
  }

  override fun closeTipDialog() {
    openedDialog?.close(DialogWrapper.OK_EXIT_CODE)
  }

  override fun canShowDialogAutomaticallyNow(project: Project): Boolean {
    return GeneralSettings.getInstance().isShowTipsOnStartup
           && !DISABLE_TIPS_FOR_PROJECT.get(project, false)
           && !GotItTooltipService.getInstance().isFirstRun
           && openedDialog?.isVisible != true
           && !TipsUsageManager.getInstance().wereTipsShownToday()
  }
}