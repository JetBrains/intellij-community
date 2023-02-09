package com.intellij.remoteDev.downloader.exceptions

import java.io.IOException

open class ErrorHttpResponseException(message: String?, val responseCode: Int, cause: Throwable?) : IOException(message, cause)

class CodeWithMeUnavailableException(message: String, val learnMoreLink: String?, val reason: String?, cause: Throwable?, responseCode: Int): ErrorHttpResponseException(message, responseCode, cause)

class LobbyConnectionRefused(message: String, cause: Throwable) : IOException(message, cause)