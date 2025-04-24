// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text

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
}
