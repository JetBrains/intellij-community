// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.CommonBundle
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.MessageConstants
import com.intellij.openapi.ui.Messages
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface RestartDialog {
  fun showRestartRequired()
}

@ApiStatus.Experimental
class RestartDialogImpl : RestartDialog {
  override fun showRestartRequired() {
    showRestartRequired(showCancelButton = false, launchRestart = true)
  }

  companion object {

    @JvmStatic
    fun restartWithConfirmation() {
      val app = ApplicationManagerEx.getApplicationEx()

      if (GeneralSettings.getInstance().isConfirmExit) {
        val answer = Messages.showYesNoDialog(
          IdeBundle.message(if (app.isRestartCapable()) "dialog.message.restart.ide" else "dialog.message.restart.alt"),
          IdeBundle.message("dialog.title.restart.ide"),
          IdeBundle.message(if (app.isRestartCapable()) "ide.restart.action" else "ide.shutdown.action"),
          CommonBundle.getCancelButtonText(),
          Messages.getQuestionIcon()
        )
        if (answer != Messages.YES) {
          return
        }
      }

      app.restart(true)
    }

    @MagicConstant(intValues = [MessageConstants.YES.toLong(), MessageConstants.NO.toLong(), MessageConstants.CANCEL.toLong()])
    @JvmStatic
    fun showRestartRequired(showCancelButton: Boolean = false, launchRestart: Boolean = true): Int {
      val title = IdeBundle.message("dialog.title.restart.required")
      val message = IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                                      ApplicationNamesInfo.getInstance().fullProductName)
      val yesText = if (ApplicationManager.getApplication().isRestartCapable)
        IdeBundle.message("ide.restart.action")
      else IdeBundle.message("ide.shutdown.action")
      val noText = IdeBundle.message("ide.notnow.action")
      val icon = Messages.getQuestionIcon()

      val result = if (showCancelButton) {
        Messages.showYesNoCancelDialog(message, title, yesText, noText, CommonBundle.getCancelButtonText(), icon)
      }
      else {
        Messages.showYesNoDialog(message, title, yesText, noText, icon)
      }

      if (result == Messages.YES && launchRestart) {
        ApplicationManagerEx.getApplicationEx().restart(true)
      }
      return result
    }
  }
}
