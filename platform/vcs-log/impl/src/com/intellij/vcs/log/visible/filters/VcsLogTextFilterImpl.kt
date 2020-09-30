// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters

import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogTextFilter
import org.jetbrains.annotations.NonNls

internal data class VcsLogTextFilterImpl(private val text: String,
                                         private val isMatchCase: Boolean) : VcsLogDetailsFilter, VcsLogTextFilter {

  override fun matches(message: String): Boolean = message.contains(text, !isMatchCase)

  override fun getText(): String = text

  override fun isRegex(): Boolean = false

  override fun matchesCase(): Boolean = isMatchCase

  @NonNls
  override fun toString(): String {
    return "containing '$text' ${caseSensitiveText()}"
  }
}