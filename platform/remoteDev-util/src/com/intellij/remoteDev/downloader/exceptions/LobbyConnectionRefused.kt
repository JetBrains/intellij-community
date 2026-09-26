// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.downloader.exceptions

import java.io.IOException

class LobbyConnectionRefused(message: String, cause: Throwable) : IOException(message, cause)