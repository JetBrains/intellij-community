package com.intellij.remoteDev.util

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.util.application
import com.intellij.util.withPath
import org.jetbrains.annotations.ApiStatus
import java.net.URI

fun URI.addPathSuffix(suffix: String): URI {
  val currentPath = path
  if (currentPath.isNullOrEmpty()) {
    error("Expected URI path to be present: $this")
  }
  return withPath(currentPath + suffix)
}

fun URI.trimPath(): String {
  return this.resolve("/").toString()
}

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
          RemoteDevUtilBundle.message(
            "error.message.invalid.address.format",
            ex.message?.let { StringUtil.escapeXmlEntities(it) } ?: ""
          ),
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
