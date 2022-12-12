package com.intellij.remoteDev.downloader.exceptions

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.util.application

object CodeWithMeDownloaderExceptionHandler {
  fun handle(@NlsContexts.DialogTitle product: String, t: Throwable) {
    application.invokeLater({
      when (t) {
        is CodeWithMeUnavailableException -> handle(product, t)
      }
    }, ModalityState.any())
  }

  private fun handle(@NlsContexts.DialogTitle product: String, t: CodeWithMeUnavailableException) {
    Messages.showErrorDialog(
      t.message ?: RemoteDevUtilBundle.message("error.not.available", product),
      RemoteDevUtilBundle.message("error.not.available", product)
    )
  }
}