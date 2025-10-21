// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.fs.UnwatchOptionsBuilder
import com.intellij.platform.eel.path.EelPath

internal object FileWatcherUtil {
  @Throws(UnsupportedOperationException::class)
  internal suspend fun reset(eel: EelApi) {
    eel.fs.unwatch(UnwatchOptionsBuilder(EelPath.parse("/", eel.descriptor)).build())
  }
}