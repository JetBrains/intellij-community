// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch

import java.util.*

internal object PatchDateParser {
  /**
   * @see com.intellij.openapi.diff.impl.patch.TextPatchBuilder
   */
  private val dateRegex = "\\(date ([0-9]+)\\)".toRegex() // NON-NLS

  @JvmStatic
  fun parseVersionAsDate(versionId: String): Date? = try {
    val tsMatcher = dateRegex.matchEntire(versionId)
    if (tsMatcher != null) {
      val fromTsPattern = tsMatcher.groupValues[1].toLong()
      Date(fromTsPattern)
    }
    else {
      Date(versionId)
    }
  }
  catch (_: IllegalArgumentException) {
    null
  }
}