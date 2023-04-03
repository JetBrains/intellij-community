// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

import com.intellij.ide.ui.search.PorterStemmerUtil
import com.intellij.util.text.NameUtilCore
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class WordsSplitter private constructor(private val skipLess: Int,
                                        private val ignoreStopWords: Boolean,
                                        private val stopWords: Set<String>,
                                        private val maxWords: Int,
                                        private val withStemming: Boolean,
                                        private val toLowerCase: Boolean) {

  fun split(name: String): List<String> = wordsFromName(name)
    .filter(::shouldInclude)
    .take(maxWords)
    .map(::normalize)
    .toList()

  private fun wordsFromName(name: String): Iterable<String> = object : Iterable<String> {
    override fun iterator(): Iterator<String> = object : Iterator<String> {
      var start = 0
      override fun hasNext(): Boolean = start < name.length
      override fun next(): String {
        val next = NameUtilCore.nextWord(name, start)
        val word = name.substring(start, next)
        start = next
        return word
      }
    }
  }

  private fun normalize(word: String): String {
    return word
      .let { if (toLowerCase) it.lowercase(Locale.getDefault()) else it }
      .let { if (withStemming) PorterStemmerUtil.stem(it) ?: it else it }
  }

  private fun shouldInclude(word: String): Boolean =
    word.isNotBlank() &&
    word.all { it.isLetter() } &&
    word.length >= skipLess &&
    (!ignoreStopWords || word !in stopWords)

  class Builder {
    companion object {
      private val DEFAULT_STOP_WORDS = setOf("set", "get", "is")
      private const val DEFAULT_MAX_WORDS_COUNT = 7
      private const val DEFAULT_MIN_WORD_LENGTH = 3

      fun identifiers(): Builder = Builder()
        .skipShort(DEFAULT_MIN_WORD_LENGTH)
        .ignoreStopWords(DEFAULT_STOP_WORDS)
        .maxWords(DEFAULT_MAX_WORDS_COUNT)
        .toLowerCase()
    }

    private var toLowerCase: Boolean = false
    private var withStemming: Boolean = false
    private var ignoreStopWords: Boolean = false
    private var stopWords: Set<String> = DEFAULT_STOP_WORDS
    private var skipLess: Int = 0
    private var maxWords: Int = Int.MAX_VALUE

    fun build(): WordsSplitter = WordsSplitter(skipLess, ignoreStopWords, stopWords, maxWords, withStemming, toLowerCase)

    fun ignoreStopWords(stopWords: Iterable<String>? = null): Builder = apply {
      ignoreStopWords = true
      if (stopWords != null) {
        this.stopWords = stopWords.toSet()
      }
    }

    fun skipShort(skipLess: Int = DEFAULT_MIN_WORD_LENGTH): Builder = apply {
      this.skipLess = skipLess
    }

    fun maxWords(count: Int = DEFAULT_MAX_WORDS_COUNT): Builder = apply {
      maxWords = count
    }

    fun withStemming(): Builder = apply {
      withStemming = true
    }

    fun toLowerCase(): Builder = apply {
      toLowerCase = true
    }
  }
}
