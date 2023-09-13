package com.intellij.remoteDev.downloader.exceptions

import java.io.IOException

class LobbyConnectionRefused(message: String, cause: Throwable) : IOException(message, cause)