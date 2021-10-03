package com.intellij.remoteDev.util

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Experimental
object UrlUtil {
  fun parseOrShowError(url: String, @NlsContexts.DialogTitle product: String): URI? {
    try {
      if (url.contains("://")) {
        return URI.create(url)
      }

      val split = url.split(':')
      if (split.size == 2) {
        return URI.create("tcp://${split[0]}:${split[1]}")
      }
    }
    catch (ex: Throwable) {
      application.invokeLater {
        Messages.showErrorDialog(
          RemoteDevUtilBundle.message("error.message.invalid.address.format", ex.message ?: ""),
          product)
      }
      return null
    }

    application.invokeLater {
      Messages.showErrorDialog(
        RemoteDevUtilBundle.message("error.message.unsupported.address.format"),
        product)
    }

    return null
  }
}