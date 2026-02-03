package com.intellij.remoteDev.downloader.exceptions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.util.application

object CodeWithMeDownloaderExceptionHandler {
  fun handle(@DialogTitle product: String, t: Throwable) {
    application.invokeLater({
      when (t) {
        is CodeWithMeUnavailableException -> handle(product, t)
        else -> handleDefault(product, t)
      }
    }, ModalityState.any())
  }

  private fun handle(@DialogTitle product: String, t: CodeWithMeUnavailableException) {
    val message = StringBuilder().apply {
      append(t.message ?: RemoteDevUtilBundle.message("error.not.available", product))
      if (t.reason != null) {
        append("\n")
        append(t.reason)
      }
    }.toString() // NON-NLS, the message comes from the server

    val buttons = if (t.learnMoreLink != null) arrayOf(Messages.getOkButton(), RemoteDevUtilBundle.message("error.learn.more"))
      else arrayOf(Messages.getOkButton())

    val result = MessagesService
      .getInstance()
      .showMessageDialog(null, null,
                         message, RemoteDevUtilBundle.message("error.not.available", product),
                         buttons,
                         0, 0, Messages.getErrorIcon(),
                         null, false, null)

    if (buttons.size == 2 && result == 1 && t.learnMoreLink != null) {
      BrowserUtil.browse(t.learnMoreLink)
    }
  }

  private fun handleDefault(@DialogTitle product: String, t: Throwable) {
    Messages.showErrorDialog(
      RemoteDevUtilBundle.message("error.url.issue", t.message ?: "Unknown"),
      product)
  }
}