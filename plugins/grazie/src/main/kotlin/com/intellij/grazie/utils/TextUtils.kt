// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import org.apache.commons.text.similarity.LevenshteinDistance

internal object Text {
  fun isNewline(char: Char) = char == '\n'

  private val PUNCTUATIONS: Set<Byte> = setOf(Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                                              Character.OTHER_PUNCTUATION, Character.CONNECTOR_PUNCTUATION,
                                              Character.DASH_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
                                              Character.FINAL_QUOTE_PUNCTUATION)

  fun isPunctuation(char: Char) = when (Character.getType(char).toByte()) {
    in PUNCTUATIONS -> true
    else -> false
  }

  fun isQuote(char: Char) = char == '\'' || char == '\"'

  object Levenshtein {
    private val levenshtein = LevenshteinDistance()

    fun distance(str1: CharSequence?, str2: CharSequence?): Int = levenshtein.apply(str1, str2)
  }
}
