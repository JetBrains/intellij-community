// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.util.text.matching.KeyboardLayoutConverter
import com.intellij.util.text.matching.KeyboardLayoutUtil

/**
 * todo: move to platform module together with NameUtil class
 */
object PlatformKeyboardLayoutConverter : KeyboardLayoutConverter {
  override fun convert(c: Char): Char? = KeyboardLayoutUtil.getAsciiForChar(c)
}