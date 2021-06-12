// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.internal.ml.WordsSplitter
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import kotlin.math.max

object ContextSimilarityUtil {
  private val WORDS_SPLITTER = WordsSplitter.Builder.identifiers().build()
  private val STEMMING_WORDS_SPLITTER = WordsSplitter.Builder.identifiers().withStemming().build()
  val LINE_SIMILARITY_SCORER_KEY: Key<ContextSimilarityScoringFunction> = Key.create("LINE_SIMILARITY_SCORER")
  val PARENT_SIMILARITY_SCORER_KEY: Key<ContextSimilarityScoringFunction> = Key.create("PARENT_SIMILARITY_SCORER")

  fun createLineSimilarityScoringFunction(line: String): ContextSimilarityScoringFunction =
    ContextSimilarityScoringFunction(StringUtil.getWordsIn(line))

  fun createParentSimilarityScoringFunction(element: PsiElement?): ContextSimilarityScoringFunction {
    var curElement = element
    val parents = mutableListOf<String>()
    while (curElement != null && curElement !is PsiFile) {
      if (curElement is PsiNamedElement) {
        val name = curElement.name
        if (name != null) parents.add(name)
      }
      curElement = curElement.parent
    }
    return ContextSimilarityScoringFunction(parents)
  }

  class ContextSimilarityScoringFunction(words: List<String>) {
    private val tokens: List<List<String>> = words.map { WORDS_SPLITTER.split(it) }
    private val stemmedTokens: List<List<String>> = words.map { STEMMING_WORDS_SPLITTER.split(it) }

    fun scorePrefixSimilarity(value: String): Similarity = calculatePrefixSimilarity(tokens, value)
    fun scoreStemmedSimilarity(value: String): Similarity = calculateStemmedSimilarity(stemmedTokens, value)
  }

  class Similarity {
    private var sumSimilarity: Double = 0.0
    private var count: Int = 0
    private var maxSimilarity: Double = 0.0
    private var fullSimilarity: Double = 0.0

    fun addSimilarityValue(value: Double) {
      sumSimilarity += value
      maxSimilarity = max(maxSimilarity, value)
      count++
    }

    fun addFullSimilarityValue(value: Double) {
      fullSimilarity = max(fullSimilarity, value)
    }

    fun meanSimilarity(): Double = if (count == 0) 0.0 else sumSimilarity / count
    fun maxSimilarity(): Double = maxSimilarity
    fun fullSimilarity(): Double = fullSimilarity
  }

  fun calculatePrefixSimilarity(tokens: List<List<String>>, lookupString: String): Similarity {
    val result = Similarity()
    val lookupWords = WORDS_SPLITTER.split(lookupString)
    if (lookupWords.isEmpty()) return result
    for (tokenWords in tokens) {
      var tokenSimilarity = 0.0
      var tokenFullSimilarity = 0.0
      for (word in lookupWords) {
        tokenSimilarity += word.length.downTo(1).find { prefixLength ->
          tokenWords.any {
            it.startsWith(word.substring(0, prefixLength), true)
          }
        } ?: 0
        if (tokenWords.any { it.equals(word, true) }) tokenFullSimilarity++
      }
      result.addSimilarityValue(tokenSimilarity / lookupString.length)
      result.addFullSimilarityValue(tokenFullSimilarity / lookupWords.size)
    }
    return result
  }

  fun calculateStemmedSimilarity(tokens: List<List<String>>, lookupString: String): Similarity {
    val result = Similarity()
    val lookupWords = STEMMING_WORDS_SPLITTER.split(lookupString)
    if (lookupWords.isEmpty()) return result
    for (tokenWords in tokens) {
      var matchedWords = 0.0
      for (word in lookupWords) {
        if (tokenWords.any { it.equals(word, true) }) matchedWords++
      }
      result.addSimilarityValue(matchedWords / lookupWords.size)
    }
    return result
  }
}