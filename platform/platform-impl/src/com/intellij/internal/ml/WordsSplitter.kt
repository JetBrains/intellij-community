// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

import com.intellij.util.text.NameUtilCore

class WordsSplitter private constructor(private val skipLess: Int,
                                        private val ignoreStopWords: Boolean,
                                        private val stopWords: Set<String>,
                                        private val maxWords: Int,
                                        private val toLowerCase: Boolean) {

  fun split(name: String): List<String> = wordsFromName(name)
    .filter(::filterWord)
    .take(maxWords)
    .map(::processWord)
    .toList()

  private fun wordsFromName(name: String) = sequence {
    var start = 0
    while (start < name.length) {
      val next = NameUtilCore.nextWord(name, start)
      yield(name.substring(start, next))
      start = next
    }
  }

  private fun processWord(word: String): String = if (toLowerCase) word.toLowerCase() else word

  private fun filterWord(word: String): Boolean =
    word.isNotBlank() &&
    word.all { it.isLetter() } &&
    word.length >= skipLess &&
    (!ignoreStopWords || word !in stopWords)

  class Builder {
    companion object {
      private val DEFAULT_STOP_WORDS = setOf("set", "get", "is")
      private const val DEFAULT_MAX_WORDS_COUNT = 7
      private const val DEFAULT_MIN_WORD_LENGTH = 3

      fun default(): WordsSplitter = WordsSplitter(DEFAULT_MIN_WORD_LENGTH, true, DEFAULT_STOP_WORDS, DEFAULT_MAX_WORDS_COUNT, true)
    }

    private var toLowerCase: Boolean = false
    private var ignoreStopWords: Boolean = false
    private var stopWords: Set<String> = DEFAULT_STOP_WORDS
    private var skipLess: Int = 0
    private var maxWords: Int = Int.MAX_VALUE

    fun build(): WordsSplitter = WordsSplitter(skipLess, ignoreStopWords, stopWords, maxWords, toLowerCase)

    fun ignoreStopWords(stopWords: Iterable<String>? = null): Builder = apply {
      ignoreStopWords = true
      if (stopWords != null) {
        this.stopWords = stopWords.toSet()
      }
    }

    fun skipShort(skipLess: Int = DEFAULT_MIN_WORD_LENGTH): Builder = apply {
      this.skipLess = skipLess
    }

    fun maxWords(count: Int): Builder = apply {
      maxWords = count
    }

    fun toLowerCase(): Builder = apply {
      toLowerCase = true
    }
  }
}
