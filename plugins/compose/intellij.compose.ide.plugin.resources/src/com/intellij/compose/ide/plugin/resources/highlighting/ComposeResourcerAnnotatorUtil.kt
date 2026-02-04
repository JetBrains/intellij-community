// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange


private val ESCAPE_REGEX = Regex("""\\n|\\t|\\u[0-9a-fA-F]{4}""")
private val PLACEHOLDER_REGEX = Regex("""%\d+\$[sd]""")

internal fun findHighlightedRanges(text: String): Sequence<Pair<TextRange, TextAttributesKey>> = sequence {
  for (match in ESCAPE_REGEX.findAll(text)) {
    yield(TextRange.from(match.range.first, match.value.length) to DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
  }
  for (match in PLACEHOLDER_REGEX.findAll(text)) {
    yield(TextRange.from(match.range.first, match.value.length) to DefaultLanguageHighlighterColors.CONSTANT)
  }
}