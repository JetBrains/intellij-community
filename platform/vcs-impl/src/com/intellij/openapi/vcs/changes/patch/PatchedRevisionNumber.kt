// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.util.text.DateFormatUtil
import java.util.Date

internal class PatchedRevisionNumber(private val version: String?): VcsRevisionNumber {
  private val versionAsDate: Date? = version?.let(PatchDateParser::parseVersionAsDate)

  override fun asString(): @NlsSafe String = VcsBundle.message("patch.apply.conflict.patched.version") + when {
    versionAsDate != null -> " (" + DateFormatUtil.formatDateTime(versionAsDate) + ")"
    version != null -> " $version"
    else -> ""
  }

  override fun compareTo(other: VcsRevisionNumber?): Int = 0
}