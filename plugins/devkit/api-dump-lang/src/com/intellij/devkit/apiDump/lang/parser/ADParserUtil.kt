// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ADParserUtil")

package com.intellij.devkit.apiDump.lang.parser

import com.intellij.lang.PsiBuilder
import com.intellij.psi.TokenType

internal fun consumeUntilNextLineImpl(builder: PsiBuilder, level: Int) {
  while (!builder.eof()) {
    if (isLineFeed(builder, level)) break
    builder.advanceLexer()
  }
}

/** Check if there is a line feed before the current token  */
private fun isLineFeed(builder: PsiBuilder, level: Int): Boolean {
  if (builder.eof()) return true

  var steps = 0
  while (builder.rawLookup(steps - 1) === TokenType.WHITE_SPACE) {
    steps--
  }

  if (builder.rawLookup(steps - 1) == null) return true
  val start = builder.rawTokenTypeStart(steps)
  val end = builder.getCurrentOffset()
  val originalText = builder.getOriginalText()
  return originalText.subSequence(start, end).contains('\n')
}