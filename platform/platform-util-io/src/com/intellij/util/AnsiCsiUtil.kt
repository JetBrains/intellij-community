// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.jetbrains.annotations.ApiStatus

// https://en.wikipedia.org/wiki/ANSI_escape_code#CSIsection
// \u001B - END symbol
@ApiStatus.Internal
val AnsiCsiRegexCapturing: Regex = Regex("\u001B\\[([;\\d]*)m")

@ApiStatus.Internal
object AnsiCsiUtil {

  /**
   * see [CSI](https://en.wikipedia.org/wiki/ANSI_escape_code#CSIsection)
   */
  @JvmStatic
  fun stripAnsi(text: CharSequence): String = AnsiCsiRegexCapturing.replace(text, "").trim()

  /**
   * see [CSI](https://en.wikipedia.org/wiki/ANSI_escape_code#CSIsection)
   */
  @JvmStatic
  fun containsAnsi(text: CharSequence): Boolean = AnsiCsiRegexCapturing.containsMatchIn(text)
}