// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter

import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.visible.filters.caseSensitiveText

class VcsLogTextFilterImpl internal constructor(private val text: String,
                                                private val isMatchCase: Boolean) : VcsLogDetailsFilter, VcsLogTextFilter {
  @Deprecated("Use VcsLogFilterObject.fromPattern instead")
  @Suppress("unused")
  // used in upsource
  constructor(text: String) : this(text, false)

  override fun matches(message: String): Boolean = message.contains(text, !isMatchCase)

  override fun getText(): String = text

  override fun isRegex(): Boolean = false

  override fun matchesCase(): Boolean = isMatchCase

  override fun toString(): String {
    return "containing '$text' ${caseSensitiveText()}"
  }
}