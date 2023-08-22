package com.intellij.remoteDev.downloader.exceptions

class CodeWithMeUnavailableException(
  message: String,
  val learnMoreLink: String?,
  val reason: String?,
  cause: Throwable?,
  responseCode: Int
): ErrorHttpResponseException(message, responseCode, cause)