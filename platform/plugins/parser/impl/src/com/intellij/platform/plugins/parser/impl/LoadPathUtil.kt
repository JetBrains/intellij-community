// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun isV2ModulePath(path: String): Boolean = path.startsWith("intellij.") || path.startsWith("fleet.")

object LoadPathUtil {
  /**
   * By default, plugin.xml resides in the `/META-INF/` directory, and it serves as a default for `baseDir`
   *
   * `intellij.*`, `fleet.*` and `kotlin.*` relative paths are treated as references to module XMLs which reside in resource root rather than in `META-INF`.
   *
   * Returned path does _not_ have leading '/' to use it in classloader's `getResource`
   */
  fun toLoadPath(relativePath: String, baseDir: String? = null): String {
    return when {
      relativePath[0] == '/' -> relativePath.substring(1)
      isV2ModulePath(relativePath) ||
      // TODO to be removed after KTIJ-29799
      relativePath.startsWith("kotlin.") -> relativePath
      else -> (baseDir ?: "META-INF") + '/' + relativePath
    }
  }

  // FIXME this thing is bugged when relative path is an absolute path outside /META-INF, probably it has to trim first /
  fun getChildBaseDir(base: String?, relativePath: String): String? {
    val end = relativePath.lastIndexOf('/')
    if (end <= 0 || relativePath.startsWith("/META-INF/")) {
      return base
    }

    val childBase = relativePath.substring(0, end)
    return if (base == null) childBase else "$base/$childBase"
  }
}