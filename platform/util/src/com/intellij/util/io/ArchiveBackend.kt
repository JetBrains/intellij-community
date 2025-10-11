// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*

@ApiStatus.Internal
/**
 * Bridge used by [Compressor] and [Decompressor] to access eel.
 * It is impossible to access eel directly due to dependency hell.
 *
 * We now only check for OS to fix IJPL-211207 but both files should delegate to Eel Archive API.
 */
interface ArchiveBackend {
  fun isWindows(path: Path): Boolean

  companion object {
    internal fun isWindows(path: Path) =
      ServiceLoader.load(ArchiveBackend::class.java).firstOrNull()?.isWindows(path) ?: SystemInfo.isWindows
  }
}