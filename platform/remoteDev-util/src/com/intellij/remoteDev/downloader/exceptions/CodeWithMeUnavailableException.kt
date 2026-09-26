// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.downloader.exceptions

class CodeWithMeUnavailableException(
  message: String,
  val learnMoreLink: String?,
  val reason: String?,
  cause: Throwable?,
  responseCode: Int
): ErrorHttpResponseException(message, responseCode, cause)