// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel
import com.intellij.filePrediction.features.history.FileHistoryManager
import com.intellij.filePrediction.features.history.FilePredictionHistoryBaseTest
import com.intellij.filePrediction.features.history.FilePredictionHistoryState
import com.intellij.filePrediction.features.history.NextFileProbability
import com.intellij.internal.ml.ngram.NGramIncrementalModelRunner
import com.intellij.internal.ml.ngram.VocabularyWithLimit

class FilePredictionNGramTest : FilePredictionHistoryBaseTest() {

  private fun doTestNGramBase(openedFiles: List<String>, nGramLength: Int,
                              maxSequenceLength: Int, vocabularyLimit: Int, maxIdx: Int? = null,
                              expectedInternalState: FilePredictionRunnerAssertion,
                              assertion: (FileHistoryManager) -> Unit) {
    val state = FilePredictionHistoryState()
    val model = JMModel(counter = ArrayTrieCounter(), order = nGramLength, lambda = 1.0)
    val vocabulary = VocabularyWithLimit(vocabularyLimit, nGramLength, maxSequenceLength, 2)
    maxIdx?.let { vocabulary.recent.setMaxTokenIndex(it) }
    val runner = NGramIncrementalModelRunner(nGramLength, 1.0, model, vocabulary)
    val manager = FileHistoryManager(runner, state, vocabularyLimit)
    try {
      for (file in openedFiles) {
        manager.onFileOpened(file)
      }

      expectedInternalState.assert(runner)
      assertion.invoke(manager)
    }
    finally {
      manager.cleanup()
    }
  }

  private fun doTestNGram(openedFiles: List<String>,
                          nGramOrder: Int,
                          vocabularyLimit: Int = 3,
                          maxSequenceLength: Int = 10000,
                          maxIdx: Int? = null,
                          expectedInternalState: FilePredictionRunnerAssertion,
                          expected: List<Pair<String, NextFileProbability>> = emptyList()) {
    doTestNGramBase(openedFiles, nGramOrder, maxSequenceLength, vocabularyLimit, maxIdx, expectedInternalState) { manager ->
      var total = 0.0
      val actual = manager.calcNGramFeatures(expected.map { it.first })
      for (expectedEntry in expected) {
        val probability = actual.calculateFileFeatures(expectedEntry.first)!!
        assertNextFileProbabilityEquals(expectedEntry.first, expectedEntry.second, probability)
        total += probability.mle
      }

      assertEquals(1.0, total)
    }
  }

  private fun doTestNGramMle(openedFiles: List<String>,
                             nGramOrder: Int,
                             vocabularyLimit: Int = 3,
                             maxSequenceLength: Int = 10000,
                             maxIdx: Int? = null,
                             expectedInternalState: FilePredictionRunnerAssertion,
                             expected: List<Pair<String, Double>> = emptyList()) {
    doTestNGramBase(openedFiles, nGramOrder, maxSequenceLength, vocabularyLimit, maxIdx, expectedInternalState) { manager ->
      var total = 0.0
      val actual = manager.calcNGramFeatures(expected.map { it.first })
      for (expectedEntry in expected) {
        val probability = actual.calculateFileFeatures(expectedEntry.first)!!
        assertDoubleEquals("MLE for ${expectedEntry.first}", expectedEntry.second, probability.mle)
        total += probability.mle
      }

      assertDoubleEquals("Probability sum", 1.0, total)
    }
  }

  private fun doTestBiGramMle(openedFiles: List<String>,
                              vocabularyLimit: Int = 3,
                              maxSequenceLength: Int = 10000,
                              maxIdx: Int? = null,
                              expectedInternalState: FilePredictionRunnerAssertion,
                              expected: List<Pair<String, Double>> = emptyList()) {
    doTestNGramMle(openedFiles, 2, vocabularyLimit, maxSequenceLength, maxIdx, expectedInternalState, expected)
  }

  private fun doTestTriGramMle(openedFiles: List<String>,
                               vocabularyLimit: Int = 3,
                               maxSequenceLength: Int = 10000,
                               maxIdx: Int? = null,
                               expectedInternalState: FilePredictionRunnerAssertion,
                               expected: List<Pair<String, Double>> = emptyList()) {
    doTestNGramMle(openedFiles, 3, vocabularyLimit, maxSequenceLength, maxIdx, expectedInternalState, expected)
  }

  private fun doTestUniGram(openedFiles: List<String>,
                            vocabularyLimit: Int = 3,
                            maxSequenceLength: Int = 10000,
                            maxIdx: Int? = null,
                            expectedInternalState: FilePredictionRunnerAssertion,
                            expected: List<Pair<String, NextFileProbability>> = emptyList()) {
    doTestNGram(openedFiles, 1, vocabularyLimit, maxSequenceLength, maxIdx, expectedInternalState, expected)
  }

  private fun doTestBiGram(openedFiles: List<String>,
                           vocabularyLimit: Int = 3,
                           maxSequenceLength: Int = 10000,
                           maxIdx: Int? = null,
                           expectedInternalState: FilePredictionRunnerAssertion,
                           expected: List<Pair<String, NextFileProbability>> = emptyList()) {
    doTestNGram(openedFiles, 2, vocabularyLimit, maxSequenceLength, maxIdx, expectedInternalState, expected)
  }

  fun `test unigram with all unique files`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(1, 2, 3, 4))
      .withRecentFiles(5, listOf("a", "b", "c", "d"), listOf(1, 2, 3, 4))

    doTestUniGram(
      listOf("a", "b", "c", "d"),
      vocabularyLimit = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
        "b" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
        "c" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
        "d" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0)
      )
    )
  }

  fun `test unigram with the single file`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(1)
      .withFileSequence(listOf(1, 1, 1))
      .withRecentFiles(4, listOf("a"), listOf(3))

    doTestUniGram(
      listOf("a", "a", "a"),
      expectedInternalState = state,
      expected = listOf("a" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0))
    )
  }

  fun `test unigram with a new file`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(1)
      .withFileSequence(listOf(1, 1, 1))
      .withRecentFiles(4, listOf("a"), listOf(3))

    doTestUniGram(
      listOf("a", "a", "a"),
      expectedInternalState = state,
      expected = listOf(
        "b" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
        "a" to NextFileProbability(1.0, 0.0001, 1.0, 10000.0, 1.0)
      )
    )
  }

  fun `test unigram with repeated file`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 3, 1, 1, 2, 1, 2))
      .withRecentFiles(9, listOf("c", "a", "b"), listOf(3, 7, 8))

    doTestUniGram(
      listOf("a", "b", "c", "a", "a", "b", "a", "b"),
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.5, 0.0001, 0.5, 5000.0, 1.0),
        "b" to NextFileProbability(0.375, 0.0001, 0.5, 3750.0, 0.75),
        "c" to NextFileProbability(0.125, 0.0001, 0.5, 1250.0, 0.25),
        "d" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0)
      )
    )
  }

  fun `test bigram with the single file`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(1)
      .withFileSequence(listOf(1, 1, 1))
      .withRecentFiles(4, listOf("a"), listOf(3))

    doTestBiGram(
      listOf("a", "a", "a"),
      vocabularyLimit = 5,
      expectedInternalState = state,
      expected = listOf("a" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0))
    )
  }

  fun `test bigram with all unique files`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(1, 2, 3, 4))
      .withRecentFiles(5, listOf("a", "b", "c", "d"), listOf(1, 2, 3, 4))

    doTestBiGram(
      listOf("a", "b", "c", "d"),
      vocabularyLimit = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
        "b" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
        "c" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
        "d" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0)
      )
    )
  }

  fun `test bigram with a new file`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(1)
      .withFileSequence(listOf(1, 1, 1))
      .withRecentFiles(4, listOf("a"), listOf(3))

    doTestBiGram(
      listOf("a", "a", "a"),
      vocabularyLimit = 5,
      expectedInternalState = state,
      expected = listOf(
        "b" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
        "a" to NextFileProbability(1.0, 0.0001, 1.0, 10000.0, 1.0)
      )
    )
  }

  fun `test bigram with one successor`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 1, 3, 1, 2, 1, 2))
      .withRecentFiles(9, listOf("c", "a", "b"), listOf(4, 7, 8))

    doTestBiGram(
      listOf("a", "b", "a", "c", "a", "b", "a", "b"),
      vocabularyLimit = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(1.0, 0.0001, 1.0, 10000.0, 1.0),
        "b" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
        "c" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
        "d" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0)
      )
    )
  }

  fun `test bigram with two successors`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 1, 3, 1, 2, 1, 2, 1))
      .withRecentFiles(10, listOf("c", "b", "a"), listOf(4, 8, 9))

    doTestBiGram(
      listOf("a", "b", "a", "c", "a", "b", "a", "b", "a"),
      vocabularyLimit = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
        "b" to NextFileProbability(0.75, 0.0001, 0.75, 7500.0, 1.0),
        "c" to NextFileProbability(0.25, 0.0001, 0.75, 2500.0, 0.33333333333),
        "d" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0)
      )
    )
  }

  fun `test bigram with all possible successors`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 1, 2, 1, 2, 3, 1, 3, 1))
      .withRecentFiles(10, listOf("a", "b", "c"), listOf(5, 8, 9))

    doTestBiGram(
      listOf("c", "c", "a", "c", "a", "b", "c", "b", "c"),
      5,
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.5, 0.0001, 0.5, 5000.0, 1.0),
        "b" to NextFileProbability(0.25, 0.0001, 0.5, 2500.0, 0.5),
        "c" to NextFileProbability(0.25, 0.0001, 0.5, 2500.0, 0.5),
        "d" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0)
      )
    )
  }

  fun `test bigram with forgotten tokens short sequence`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(2, 3, 4))
      .withRecentFiles(5, listOf("a", "b", "c"), listOf(2, 3, 4))

    doTestBiGram(
      listOf("x", "a", "b", "c"),
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.33333333333, 0.0001, 0.33333333333, 3333.33333333333, 1.0),
        "b" to NextFileProbability(0.33333333333, 0.0001, 0.33333333333, 3333.33333333333, 1.0),
        "c" to NextFileProbability(0.33333333333, 0.0001, 0.33333333333, 3333.33333333333, 1.0),
        "x" to NextFileProbability(0.0, 0.0001, 0.33333333333, 0.0, 0.0),
        "y" to NextFileProbability(0.0, 0.0001, 0.33333333333, 0.0, 0.0)
      )
    )
  }

  fun `test bigram short sequence`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 3))
      .withRecentFiles(4, listOf("a", "b", "c"), listOf(1, 2, 3))

    doTestBiGram(
      listOf("a", "b", "c"),
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.33333333333, 0.0001, 0.33333333333, 3333.33333333333, 1.0),
        "b" to NextFileProbability(0.33333333333, 0.0001, 0.33333333333, 3333.33333333333, 1.0),
        "c" to NextFileProbability(0.33333333333, 0.0001, 0.33333333333, 3333.33333333333, 1.0),
        "x" to NextFileProbability(0.0, 0.0001, 0.33333333333, 0.0, 0.0)
      )
    )
  }

  fun `test bigram forget popular token`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(2, 3, 4, 2))
      .withRecentFiles(10, listOf("c", "d", "b"), listOf(7, 8, 9))

    doTestBiGram(
      listOf("a", "b", "a", "b", "a", "b", "c", "d", "b"),
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
        "b" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
        "c" to NextFileProbability(1.0, 0.0001, 1.0, 10000.0, 1.0),
        "x" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0)
      )
    )
  }

  fun `test bigram with forgotten tokens`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(2, 3, 2, 4, 2, 3, 2, 3, 2))
      .withRecentFiles(11, listOf("c", "b", "a"), listOf(5, 9, 10))

    doTestBiGram(
      listOf("x", "a", "b", "a", "c", "a", "b", "a", "b", "a"),
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
        "b" to NextFileProbability(0.75, 0.0001, 0.75, 7500.0, 1.0),
        "c" to NextFileProbability(0.25, 0.0001, 0.75, 2500.0, 0.33333333333),
        "d" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
        "x" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0)
      )
    )
  }

  fun `test bigram with multiple forgotten prefix tokens`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(4, 5, 4, 6, 4, 5, 4, 5, 4))
      .withRecentFiles(15, listOf("c", "b", "a"), listOf(9, 13, 14))

    doTestBiGram(
      listOf("x", "y", "x", "y", "z",
             "a", "b", "a", "c", "a", "b", "a", "b", "a"),
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
        "b" to NextFileProbability(0.75, 0.0001, 0.75, 7500.0, 1.0),
        "c" to NextFileProbability(0.25, 0.0001, 0.75, 2500.0, 0.33333333333),
        "d" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
        "x" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
        "y" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
        "z" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0)
      )
    )
  }

  fun `test bigram with multiple forgotten tokens`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(4, 2, 5, 2, 4, 2, 4, 2))
      .withRecentFiles(12, listOf("c", "b", "a"), listOf(6, 10, 11))

    doTestBiGram(
      listOf(
        "x", "a", "y",
        "b", "a", "c", "a", "b", "a", "b", "a"
      ),
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0),
        "b" to NextFileProbability(0.66666666666, 0.0001, 0.66666666666, 6666.66666666666, 1.0),
        "c" to NextFileProbability(0.33333333333, 0.0001, 0.66666666666, 3333.33333333333, 0.5),
        "d" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0),
        "x" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0),
        "y" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0),
        "z" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0)
      )
    )
  }

  fun `test bigram with multiple forgotten tokens 2`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 3, 2, 1, 2, 1, 2))
      .withRecentFiles(9, listOf("c", "b", "a"), listOf(3, 7, 8))

    doTestBiGram(
      listOf(
        "b", "a", "c", "a", "b", "a", "b", "a"
      ),
      expectedInternalState = state,
      expected = listOf(
        "a" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0),
        "b" to NextFileProbability(0.66666666666, 0.0001, 0.66666666666, 6666.66666666666, 1.0),
        "c" to NextFileProbability(0.33333333333, 0.0001, 0.66666666666, 3333.33333333333, 0.5),
        "d" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0),
        "x" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0),
        "y" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0),
        "z" to NextFileProbability(0.0, 0.0001, 0.66666666666, 0.0, 0.0)
      )
    )
  }

  fun `test bigram mle short sequence`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 3, 1, 2, 1, 2))
      .withRecentFiles(8, listOf("c", "a", "b"), listOf(3, 6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "a", "b", "a", "b"),
      vocabularyLimit = 10, maxSequenceLength = 10,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.5,
        "b" to 0.0,
        "c" to 0.5,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with forgotten tokens`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(2, 3, 4, 2))
      .withRecentFiles(10, listOf("a", "c", "d", "b"), listOf(5, 7, 8, 9))


    doTestBiGramMle(
      openedFiles = listOf("a", "b", "a", "b", "a", "b", "c", "d", "b"),
      vocabularyLimit = 10, maxSequenceLength = 4,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 1.0,
        "d" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with forgotten all unique tokens`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(8)
      .withFileSequence(listOf(5, 6, 7, 8))
      .withRecentFiles(9, listOf("a", "b", "c", "d", "e", "f", "g", "h"), listOf(1, 2, 3, 4, 5, 6, 7, 8))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "e", "f", "g", "h"),
      vocabularyLimit = 10, maxSequenceLength = 4,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 0.0,
        "e" to 0.25,
        "f" to 0.25,
        "g" to 0.25,
        "h" to 0.25,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with forgotten all unique tokens and index reset`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(8)
      .withFileSequence(listOf(7, 8, 9, 10))
      .withRecentFiles(7, listOf("c", "d", "e", "f", "g", "h", "i", "j"), listOf(0, 0, 1, 2, 3, 4, 5, 6))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
      vocabularyLimit = 8, maxSequenceLength = 4, maxIdx = 8,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 0.0,
        "e" to 0.0,
        "f" to 0.0,
        "g" to 0.25,
        "h" to 0.25,
        "i" to 0.25,
        "j" to 0.25,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle short sequence with all unique tokens`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 3, 1))
      .withRecentFiles(5, listOf("c", "d", "b"), listOf(2, 3, 4))

    doTestBiGramMle(
      openedFiles = listOf("b", "c", "d", "b"),
      vocabularyLimit = 10, maxSequenceLength = 4,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 1.0,
        "d" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with multiple forgotten tokens`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(3, 4, 1, 4))
      .withRecentFiles(12, listOf("b", "c", "a", "d"), listOf(7, 8, 10, 11))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "a", "b", "a", "b", "c", "d", "a", "d"),
      vocabularyLimit = 10, maxSequenceLength = 4,
      expectedInternalState = state,
      expected = listOf(
        "a" to 1.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with the first file opened several times`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(2)
      .withFileSequence(listOf(1, 1, 1, 1, 2, 1))
      .withRecentFiles(7, listOf("b", "a"), listOf(5, 6))

    doTestBiGramMle(
      openedFiles = listOf("a", "a", "a", "a", "b", "a"),
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.75,
        "b" to 0.25,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with the second file opened several times`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(2)
      .withFileSequence(listOf(1, 1, 2, 2, 1, 2))
      .withRecentFiles(7, listOf("a", "b"), listOf(5, 6))

    doTestBiGramMle(
      openedFiles = listOf("a", "a", "b", "b", "a", "b"),
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.5,
        "b" to 0.5,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with only sequence limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(5)
      .withFileSequence(listOf(4, 2, 3, 5))
      .withRecentFiles(8, listOf("a", "d", "b", "c", "e"), listOf(1, 4, 5, 6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "b", "c", "e"),
      vocabularyLimit = 5, maxSequenceLength = 4,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.25,
        "c" to 0.25,
        "d" to 0.25,
        "e" to 0.25,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with only vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(2, 3, 4, 2, 3, 5))
      .withRecentFiles(8, listOf("d", "b", "c", "e"), listOf(4, 5, 6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "b", "c", "e"),
      vocabularyLimit = 4, maxSequenceLength = 20,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.33333333333,
        "c" to 0.33333333333,
        "d" to 0.16666666666,
        "e" to 0.16666666666,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with sequence and then vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(4, 2, 3, 5))
      .withRecentFiles(8, listOf("d", "b", "c", "e"), listOf(4, 5, 6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "b", "c", "e"),
      vocabularyLimit = 4, maxSequenceLength = 4,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.25,
        "c" to 0.25,
        "d" to 0.25,
        "e" to 0.25,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle with vocabulary and then sequence limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(3, 4, 2, 3))
      .withRecentFiles(7, listOf("d", "b", "c"), listOf(4, 5, 6))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "b", "c"),
      vocabularyLimit = 3, maxSequenceLength = 4,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 1.0,
        "e" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle all unique with only sequence limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(7)
      .withFileSequence(listOf(3, 4, 5, 6, 7))
      .withRecentFiles(8, listOf("a", "b", "c", "d", "e", "f", "g"), listOf(1, 2, 3, 4, 5, 6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "e", "f", "g"),
      vocabularyLimit = 10, maxSequenceLength = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.2,
        "d" to 0.2,
        "e" to 0.2,
        "f" to 0.2,
        "g" to 0.2,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle all unique with only vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(2)
      .withFileSequence(listOf(6, 7))
      .withRecentFiles(8, listOf("f", "g"), listOf(6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "e", "f", "g"),
      vocabularyLimit = 2, maxSequenceLength = 10,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 0.0,
        "e" to 0.0,
        "f" to 0.5,
        "g" to 0.5,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle all unique with sequence and vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(5)
      .withFileSequence(listOf(3, 4, 5, 6, 7))
      .withRecentFiles(8, listOf("c", "d", "e", "f", "g"), listOf(3, 4, 5, 6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "e", "f", "g"),
      vocabularyLimit = 5, maxSequenceLength = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.2,
        "d" to 0.2,
        "e" to 0.2,
        "f" to 0.2,
        "g" to 0.2,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle all unique with short sequence and vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(5)
      .withFileSequence(listOf(5, 6, 7))
      .withRecentFiles(8, listOf("c", "d", "e", "f", "g"), listOf(3, 4, 5, 6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "e", "f", "g"),
      vocabularyLimit = 5, maxSequenceLength = 3,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 0.0,
        "e" to 0.33333333333,
        "f" to 0.33333333333,
        "g" to 0.33333333333,
        "x" to 0.0
      )
    )
  }

  fun `test bigram mle all unique with sequence and small vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(5, 6, 7))
      .withRecentFiles(8, listOf("e", "f", "g"), listOf(5, 6, 7))

    doTestBiGramMle(
      openedFiles = listOf("a", "b", "c", "d", "e", "f", "g"),
      vocabularyLimit = 3, maxSequenceLength = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 0.0,
        "e" to 0.33333333333,
        "f" to 0.33333333333,
        "g" to 0.33333333333,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle short sequence`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 3))
      .withRecentFiles(4, listOf("a", "b", "c"), listOf(1, 2, 3))

    doTestTriGramMle(
      openedFiles = listOf("a", "b", "c"),
      vocabularyLimit = 10, maxSequenceLength = 10,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.33333333333,
        "b" to 0.33333333333,
        "c" to 0.33333333333,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle without forget`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(1, 2, 3, 1, 3, 1, 3))
      .withRecentFiles(8, listOf("b", "a", "c"), listOf(2, 6, 7))

    doTestTriGramMle(
      openedFiles = listOf("a", "b", "c", "a", "c", "a", "c"),
      vocabularyLimit = 3, maxSequenceLength = 10,
      expectedInternalState = state,
      expected = listOf(
        "a" to 1.0,
        "b" to 0.0,
        "c" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle with vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(5, 3, 5, 6))
      .withRecentFiles(9, listOf("c", "a", "b"), listOf(6, 7, 8))

    doTestTriGramMle(
      openedFiles = listOf("a", "b", "c", "d", "a", "c", "a", "b"),
      vocabularyLimit = 3, maxSequenceLength = 10,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.5,
        "b" to 0.25,
        "c" to 0.25,
        "d" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle with small vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(5, 2, 6))
      .withRecentFiles(12, listOf("a", "b", "e"), listOf(9, 10, 11))

    doTestTriGramMle(
      openedFiles = listOf("a", "b", "d", "c", "b", "c", "a", "c", "a", "b", "e"),
      vocabularyLimit = 3, maxSequenceLength = 8,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.33333333333,
        "b" to 0.33333333333,
        "c" to 0.0,
        "d" to 0.0,
        "e" to 0.33333333333,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle with sequence limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(4, 1, 3, 1, 2))
      .withRecentFiles(9, listOf("d", "c", "a", "b"), listOf(4, 6, 7, 8))

    doTestTriGramMle(
      openedFiles = listOf("a", "b", "c", "d", "a", "c", "a", "b"),
      vocabularyLimit = 10, maxSequenceLength = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.4,
        "b" to 0.2,
        "c" to 0.2,
        "d" to 0.2,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle with sequence and then vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(1, 4, 1, 2, 5))
      .withRecentFiles(12, listOf("c", "a", "b", "e"), listOf(8, 9, 10, 11))

    doTestTriGramMle(
      openedFiles = listOf("a", "b", "d", "c", "b", "c", "a", "c", "a", "b", "e"),
      vocabularyLimit = 4, maxSequenceLength = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.4,
        "b" to 0.2,
        "c" to 0.2,
        "d" to 0.0,
        "e" to 0.2,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle with vocabulary and then sequence limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(4, 5, 4, 5, 2))
      .withRecentFiles(11, listOf("c", "a", "b"), listOf(8, 9, 10))

    doTestTriGramMle(
      openedFiles = listOf("a", "b", "d", "c", "b", "c", "a", "c", "a", "b"),
      vocabularyLimit = 3, maxSequenceLength = 5,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.4,
        "b" to 0.2,
        "c" to 0.4,
        "d" to 0.0,
        "e" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle for repeated symbol`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(
        1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 4
      ))
      .withRecentFiles(
        21,
        listOf("a", "b", "c", "d"),
        listOf(10, 12, 15, 20)
      )

    doTestTriGramMle(
      openedFiles = listOf(
        "a", "b", "b", "b", "b", "b", "b", "a", "a", "a",
        "b", "b", "c", "c", "c", "d", "d", "d", "d", "d"
      ),
      vocabularyLimit = 5, maxSequenceLength = 50,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 1.0,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle for repeated symbol with vocabulary`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(3)
      .withFileSequence(listOf(
        2, 2, 3, 3, 3, 4, 4, 4, 4, 4
      ))
      .withRecentFiles(
        21,
        listOf("b", "c", "d"),
        listOf(12, 15, 20)
      )

    doTestTriGramMle(
      openedFiles = listOf(
        "a", "b", "b", "b", "b", "b", "b", "a", "a", "a",
        "b", "b", "c", "c", "c", "d", "d", "d", "d", "d"
      ),
      vocabularyLimit = 3, maxSequenceLength = 50,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 1.0,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle for repeated symbol with sequence limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(
        3, 3, 4, 4, 4, 4, 4
      ))
      .withRecentFiles(
        21,
        listOf("a", "b", "c", "d"),
        listOf(10, 12, 15, 20)
      )

    doTestTriGramMle(
      openedFiles = listOf(
        "a", "b", "b", "b", "b", "b", "b", "a", "a", "a",
        "b", "b", "c", "c", "c", "d", "d", "d", "d", "d"
      ),
      vocabularyLimit = 4, maxSequenceLength = 7,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 1.0,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle for repeated symbol with sequence limit and index reset`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(
        3, 3, 4, 4, 4, 4, 4
      ))
      .withRecentFiles(
        14,
        listOf("a", "b", "c", "d"),
        listOf(0, 5, 8, 13)
      )

    doTestTriGramMle(
      openedFiles = listOf(
        "a", "b", "b", "b", "a", "a", "a", "b", "b", "b",
        "b", "b", "c", "c", "c", "d", "d", "d", "d", "d"
      ),
      vocabularyLimit = 4, maxSequenceLength = 7, maxIdx = 14,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 1.0,
        "x" to 0.0
      )
    )
  }

  fun `test trigram mle for repeated symbol with sequence and vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(4)
      .withFileSequence(listOf(
        3, 4, 4, 4, 4, 4, 5
      ))
      .withRecentFiles(
        22,
        listOf("b", "c", "d", "e"),
        listOf(12, 15, 20, 21)
      )

    doTestTriGramMle(
      openedFiles = listOf(
        "a", "b", "b", "b", "b", "b", "b", "a", "a", "a",
        "b", "b", "c", "c", "c", "d", "d", "d", "d", "d", "e"
      ),
      vocabularyLimit = 4, maxSequenceLength = 7,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.14285714285,
        "d" to 0.71428571428,
        "e" to 0.14285714285,
        "x" to 0.0
      )
    )
  }

  fun `test ngram mle without forget`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(8)
      .withFileSequence(listOf(
        1, 2, 3, 4, 5, 6, 7, 1, 5, 2, 3, 7, 5, 4,
        6, 1, 7, 2, 6, 3, 4, 1, 2, 7, 6, 3, 4, 8
      ))
      .withRecentFiles(
        29,
        listOf("e", "a", "b", "g", "f", "c", "d", "h"),
        listOf(13, 22, 23, 24, 25, 26, 27, 28)
      )

    doTestNGramMle(
      openedFiles = listOf(
        "a", "b", "c", "d", "e", "f", "g", "a", "e", "b", "c", "g", "e", "d",
        "f", "a", "g", "b", "f", "c", "d", "a", "b", "g", "f", "c", "d", "h"
      ),
      nGramOrder = 6, vocabularyLimit = 20, maxSequenceLength = 50,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.14285714285,
        "b" to 0.14285714285,
        "c" to 0.14285714285,
        "d" to 0.14285714285,
        "e" to 0.10714285714,
        "f" to 0.14285714285,
        "g" to 0.14285714285,
        "h" to 0.0357142857,
        "x" to 0.0
      )
    )
  }

  fun `test ngram mle with vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(8)
      .withFileSequence(listOf(9, 3, 10, 13, 14, 15, 16, 17, 9, 10))
      .withRecentFiles(
        27,
        listOf("c", "d", "e", "f", "g", "h", "i", "j"),
        listOf(18, 20, 21, 22, 23, 24, 25, 26)
      )

    doTestNGramMle(
      openedFiles = listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "i", "c", "j",
        "a", "j", "b", "i", "c", "j", "d", "e", "f", "g", "h", "i", "j"
      ),
      nGramOrder = 6, vocabularyLimit = 8, maxSequenceLength = 50,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 1.0,
        "e" to 0.0,
        "f" to 0.0,
        "g" to 0.0,
        "h" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test ngram mle with sequence limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(10)
      .withFileSequence(listOf(10, 9, 3, 10, 1, 10, 2, 9, 3, 10, 4, 5, 6, 7, 8, 9, 10))
      .withRecentFiles(
        27,
        listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
        listOf(14, 16, 18, 20, 21, 22, 23, 24, 25, 26)
      )

    doTestNGramMle(
      openedFiles = listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "i", "c", "j",
        "a", "j", "b", "i", "c", "j", "d", "e", "f", "g", "h", "i", "j"
      ),
      nGramOrder = 6, vocabularyLimit = 20, maxSequenceLength = 17,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.25,
        "b" to 0.25,
        "c" to 0.0,
        "d" to 0.25,
        "e" to 0.0,
        "f" to 0.0,
        "g" to 0.0,
        "h" to 0.0,
        "i" to 0.25,
        "x" to 0.0
      )
    )
  }

  fun `test ngram mle with vocabulary and then sequence limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(8)
      .withFileSequence(listOf(3, 10, 13, 14, 15, 16, 17, 9, 10))
      .withRecentFiles(
        27,
        listOf("c", "d", "e", "f", "g", "h", "i", "j"),
        listOf(18, 20, 21, 22, 23, 24, 25, 26)
      )

    doTestNGramMle(
      openedFiles = listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "i", "c", "j",
        "a", "j", "b", "i", "c", "j", "d", "e", "f", "g", "h", "i", "j"
      ),
      nGramOrder = 6, vocabularyLimit = 8, maxSequenceLength = 9,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 1.0,
        "e" to 0.0,
        "f" to 0.0,
        "g" to 0.0,
        "h" to 0.0,
        "x" to 0.0
      )
    )
  }

  fun `test ngram mle with sequence and then vocabulary limit`() {
    val state = FilePredictionRunnerAssertion()
      .withVocabulary(9)
      .withFileSequence(listOf(9, 3, 10, 13, 14, 15, 16, 17, 9, 10))
      .withRecentFiles(
        27,
        listOf("b", "c", "d", "e", "f", "g", "h", "i", "j"),
        listOf(16, 18, 20, 21, 22, 23, 24, 25, 26)
      )

    doTestNGramMle(
      openedFiles = listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "i", "c", "j",
        "a", "j", "b", "i", "c", "j", "d", "e", "f", "g", "h", "i", "j"
      ),
      nGramOrder = 6, vocabularyLimit = 9, maxSequenceLength = 10,
      expectedInternalState = state,
      expected = listOf(
        "a" to 0.0,
        "b" to 0.0,
        "c" to 0.0,
        "d" to 1.0,
        "e" to 0.0,
        "f" to 0.0,
        "g" to 0.0,
        "h" to 0.0,
        "i" to 0.0,
        "x" to 0.0
      )
    )
  }
}