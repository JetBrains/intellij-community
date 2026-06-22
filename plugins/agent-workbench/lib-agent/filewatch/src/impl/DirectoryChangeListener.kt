// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.filewatch.impl

internal fun interface DirectoryChangeListener {
  suspend fun onEvent(event: DirectoryChangeEvent)

  suspend fun onException(e: Exception) {
  }
}
