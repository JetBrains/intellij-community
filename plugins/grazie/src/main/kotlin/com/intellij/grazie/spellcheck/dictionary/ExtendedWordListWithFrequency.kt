// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.spellcheck.dictionary

import ai.grazie.spell.lists.WordListWithFrequency
import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.grazie.spellcheck.engine.MAX_WORD_LENGTH
import com.intellij.grazie.utils.runWithCheckCanceledOrKnownSlow

internal class ExtendedWordListWithFrequency(private val base: WordListWithFrequency,
                                             private val extension: WordListAdapter) : WordListWithFrequency {
  override val defaultFrequency: Int
    get() = base.defaultFrequency

  override val maxFrequency: Int
    get() = base.maxFrequency

  override fun getFrequency(word: String) = base.getFrequency(word)

  override fun contains(word: String, caseSensitive: Boolean): Boolean {
    if (word.length > MAX_WORD_LENGTH) return false
    return base.contains(word, caseSensitive) || extension.contains(word, caseSensitive)
  }

  override fun suggest(word: String) = (if (base is HunspellWordList) {
    runWithCheckCanceledOrKnownSlow("IJPL-256787") { base.suggest(word) }
  } else {
    base.suggest(word)
  }).apply { this += extension.suggest(word) }
}
