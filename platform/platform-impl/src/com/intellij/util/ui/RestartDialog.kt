// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.CommonBundle
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.MessageConstants
import com.intellij.openapi.ui.Messages
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class RestartDialog {

  companion object {

    @JvmStatic
    fun restartWithConfirmation() {
      if (GeneralSettings.getInstance().isConfirmExit) {
        if (Messages.showYesNoDialog(
            IdeBundle.message("dialog.message.restart.ide"),
            IdeBundle.message("dialog.title.restart.ide"),
            IdeBundle.message("dialog.action.restart.yes"),
            IdeBundle.message("dialog.action.restart.cancel"),
            Messages.getWarningIcon()
          ) != Messages.YES) {
          return
        }
      }

      val app = ApplicationManager.getApplication() as ApplicationEx
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
