// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmName

interface LineOffsets {
  fun getLineStart(line: Int): Int

  /**
   * includeNewline = false
   */
  fun getLineEnd(line: Int): Int

  fun getLineEnd(line: Int, includeNewline: Boolean): Int

  fun getLineNumber(offset: Int): Int

  val lineCount: Int

  val textLength: Int

  // for preservation of source compatibility with Kotlin code after j2k
  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getLineCountDoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #lineCount instead", ReplaceWith("lineCount"))
  fun getLineCount(): Int = lineCount

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getTextLengthDoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #textLength instead", ReplaceWith("textLength"))
  fun getTextLength(): Int = textLength
}
