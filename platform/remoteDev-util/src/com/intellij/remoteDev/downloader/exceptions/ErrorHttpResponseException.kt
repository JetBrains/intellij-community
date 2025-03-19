package com.intellij.remoteDev.downloader.exceptions

import java.io.IOException

open class ErrorHttpResponseException(
  message: String,
  val responseCode: Int,
  cause: Throwable?,
  presentableMessage: String? = null,
) : IOException(message, cause) {

  private val _message = message

  private val _presentableMessage = presentableMessage

  val presentableMessage: String
    get() = _presentableMessage ?: _message
}
