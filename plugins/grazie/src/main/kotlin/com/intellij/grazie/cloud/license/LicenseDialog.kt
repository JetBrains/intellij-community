package com.intellij.grazie.cloud.license

import com.intellij.grazie.GrazieBundle
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.future.await

internal fun notifyOnLicensingError(@NlsContexts.DialogMessage error: String) {
  UIUtil.invokeLaterIfNeeded {
    Messages.showErrorDialog(error, GrazieBundle.message("grazie.licensing.error.dialog.title"))
  }
}

internal suspend fun openLogInDialog() {
  val jbaService = JBAccountInfoService.getInstance() ?: return
  jbaService.startLoginSession(JBAccountInfoService.LoginMode.AUTO)
    .onCompleted()
    .await()
}