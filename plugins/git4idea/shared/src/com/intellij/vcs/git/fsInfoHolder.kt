// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus

/**
 * Historically, case-sensitivity is handled as per-OS and not per-FS property in [git4idea.GitReference].
 * However, it should be reconsidered in the future.
 */
@ApiStatus.Internal
object CaseSensitivityInfoHolder {
  private val LOG = Logger.getInstance(CaseSensitivityInfoHolder::class.java)

  var caseSensitive: Boolean = SystemInfoRt.isFileSystemCaseSensitive
    private set

  fun setCaseSensitivity(caseSensitive: Boolean) {
    LOG.debug("Case sensitivity set: $caseSensitive")
    this.caseSensitive = caseSensitive
  }
}