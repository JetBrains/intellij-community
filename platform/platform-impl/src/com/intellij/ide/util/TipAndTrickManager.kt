// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.GotItTooltipService
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
class TipAndTrickManager {
  private var openedDialog: TipDialog? = null

  fun showTipDialog(project: Project) = showTipDialog(project, TipAndTrickBean.EP_NAME.extensionList)

  /**
   * Show the dialog with one tip without "Next tip" and "Previous tip" buttons
   */
  fun showTipDialog(project: Project, tip: TipAndTrickBean) = showTipDialog(project, listOf(tip))

  private fun showTipDialog(project: Project, tips: List<TipAndTrickBean>) {
    closeTipDialog()
    openedDialog = TipDialog(project, tips).also { dialog ->
      Disposer.register(dialog.disposable, Disposable { openedDialog = null })  // clear link to not leak the project
      dialog.show()
    }
  }

  fun closeTipDialog() {
    openedDialog?.close(DialogWrapper.OK_EXIT_CODE)
  }

  fun canShowDialogAutomaticallyNow(project: Project): Boolean {
    return GeneralSettings.getInstance().isShowTipsOnStartup
           && !DISABLE_TIPS_FOR_PROJECT.get(project, false)
           && !GotItTooltipService.getInstance().isFirstRun
           && openedDialog?.isVisible != true
           && !TipsUsageManager.getInstance().wereTipsShownToday()
  }

  companion object {
    val DISABLE_TIPS_FOR_PROJECT = Key.create<Boolean>("DISABLE_TIPS_FOR_PROJECT")

    @JvmStatic
    fun getInstance(): TipAndTrickManager = service()
  }
}