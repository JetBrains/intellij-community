// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
