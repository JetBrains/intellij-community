package com.intellij.remoteDev.downloader.exceptions

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Messages
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
    Messages.showErrorDialog(
      t.message ?: RemoteDevUtilBundle.message("error.not.available", product),
      RemoteDevUtilBundle.message("error.not.available", product)
    )
  }

  private fun handleDefault(@DialogTitle product: String, t: Throwable) {
    Messages.showErrorDialog(
      RemoteDevUtilBundle.message("error.url.issue", t.message ?: "Unknown"),
      product)
  }
}