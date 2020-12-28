// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel
import com.intellij.filePrediction.features.history.FileHistoryManager
import com.intellij.filePrediction.features.history.FilePredictionHistoryBaseTest
import com.intellij.filePrediction.features.history.FilePredictionHistoryState
import com.intellij.filePrediction.features.history.NextFileProbability

class FilePredictionNGramTest : FilePredictionHistoryBaseTest() {

  private fun doTestNGram(openedFiles: List<String>, nGramLength: Int, size: Int, limit: Int, assertion: (FileHistoryManager) -> Unit) {
    val state = FilePredictionHistoryState()
    val model = JMModel(counter = ArrayTrieCounter(), order = nGramLength, lambda = 1.0)
    val vocabulary = FilePredictionNGramVocabulary(limit)
    val runner = FilePredictionNGramModelRunner(nGramLength, model, vocabulary)
    val manager = FileHistoryManager(runner, state, limit)
    try {
      for (file in openedFiles) {
        manager.onFileOpened(file)
      }

      // unknown token is always added to wordIndices, therefore, actual size will be always size + 1
      assertEquals(size, vocabulary.wordIndices.size - 1)
      assertion.invoke(manager)
    }
    finally {
      manager.cleanup()
    }
  }

  private fun doTestUniGram(openedFiles: List<String>, size: Int, vararg expected: Pair<String, NextFileProbability>) {
    doTestNGram(openedFiles, 1, size, 5) { manager ->
      var total = 0.0
      val actual = manager.calcNGramFeatures(expected.map { it.first })
      for (entry in expected) {
        val probability= actual.calculateFileFeatures(entry.first)!!
        assertNextFileProbabilityEquals(entry.first, entry.second, probability)
        total += probability.mle
      }

      assertEquals(1.0, total)
    }
  }

  private fun doTestBiGram(openedFiles: List<String>, size: Int, limit: Int, withTotal: Boolean, vararg expected: Pair<String, NextFileProbability>) {
    doTestNGram(openedFiles, 2, size, limit) { manager ->
      var total = 0.0
      val actual = manager.calcNGramFeatures(expected.map { it.first })
      for (expectedEntry in expected) {
        val probability = actual.calculateFileFeatures(expectedEntry.first)!!
        assertNextFileProbabilityEquals(expectedEntry.first, expectedEntry.second, probability)
        total += probability.mle
      }

      if (withTotal) {
        assertEquals(1.0, total)
      }
    }
  }

  fun `test unigram with all unique files`() {
    doTestUniGram(
      listOf("file://a", "file://b", "file://c", "file://d"), 4,
      "file://a" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
      "file://b" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
      "file://c" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0),
      "file://d" to NextFileProbability(0.25, 0.25, 0.25, 1.0, 1.0)
    )
  }

  fun `test unigram with the single file`() {
    doTestUniGram(
      listOf("file://a", "file://a", "file://a"), 1,
      "file://a" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0)
    )
  }

  fun `test unigram with a new file`() {
    doTestUniGram(
      listOf("file://a", "file://a", "file://a"), 1,
      "file://b" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
      "file://a" to NextFileProbability(1.0, 0.0001, 1.0, 10000.0, 1.0)
    )
  }

  fun `test unigram with repeated file`() {
    doTestUniGram(
      listOf("file://a", "file://b", "file://c", "file://a", "file://a", "file://b", "file://a", "file://b"), 3,
      "file://a" to NextFileProbability(0.5, 0.0001, 0.5, 5000.0, 1.0),
      "file://b" to NextFileProbability(0.375, 0.0001, 0.5, 3750.0, 0.75),
      "file://c" to NextFileProbability(0.125, 0.0001, 0.5, 1250.0, 0.25),
      "file://d" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0)
    )
  }

  fun `test bigram with the single file`() {
    doTestBiGram(
      listOf("file://a", "file://a", "file://a"), 1, 5, true,
      "file://a" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0)
    )
  }

  /**
   * If all combinations of two tokens are unique -> uni-grams are used
   */
  fun `test bigram with all unique files`() {
    doTestBiGram(
      listOf("file://a", "file://b", "file://c", "file://d"), 4, 5, false,
      "file://a" to NextFileProbability(0.2857142857, 0.14285714285, 0.2857142857, 2.0, 1.0),
      "file://b" to NextFileProbability(0.2857142857, 0.14285714285, 0.2857142857, 2.0, 1.0),
      "file://c" to NextFileProbability(0.2857142857, 0.14285714285, 0.2857142857, 2.0, 1.0),
      "file://d" to NextFileProbability(0.14285714285, 0.14285714285, 0.2857142857, 1.0, 0.5)
    )
  }

  fun `test bigram with a new file`() {
    doTestBiGram(
      listOf("file://a", "file://a", "file://a"), 1, 5, true,
      "file://b" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
      "file://a" to NextFileProbability(1.0, 0.0001, 1.0, 10000.0, 1.0)
    )
  }

  fun `test bigram with one successor`() {
    doTestBiGram(
      listOf("file://a", "file://b", "file://a", "file://c", "file://a", "file://b", "file://a", "file://b"),
      3, 5, true,
      "file://a" to NextFileProbability(1.0, 0.0001, 1.0, 10000.0, 1.0),
      "file://b" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
      "file://c" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0),
      "file://d" to NextFileProbability(0.0, 0.0001, 1.0, 0.0, 0.0)
    )
  }

  fun `test bigram with two successors`() {
    doTestBiGram(
      listOf("file://a", "file://b", "file://a", "file://c", "file://a", "file://b", "file://a", "file://b", "file://a"),
      3, 5, true,
      "file://a" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
      "file://b" to NextFileProbability(0.75, 0.0001, 0.75, 7500.0, 1.0),
      "file://c" to NextFileProbability(0.25, 0.0001, 0.75, 2500.0, 0.33333333333),
      "file://d" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0)
    )
  }

  fun `test bigram with all possible successors`() {
    doTestBiGram(
      listOf("file://c", "file://c", "file://a", "file://c", "file://a", "file://b", "file://c", "file://b", "file://c"),
      3, 5, true,
      "file://a" to NextFileProbability(0.5, 0.0001, 0.5, 5000.0, 1.0),
      "file://b" to NextFileProbability(0.25, 0.0001, 0.5, 2500.0, 0.5),
      "file://c" to NextFileProbability(0.25, 0.0001, 0.5, 2500.0, 0.5),
      "file://d" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0)
    )
  }

  fun `test bigram with forgotten tokens`() {
    doTestBiGram(
      listOf("file://x", "file://a", "file://b", "file://a", "file://c", "file://a", "file://b", "file://a", "file://b", "file://a"),
      3, 3, true,
      "file://a" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
      "file://b" to NextFileProbability(0.75, 0.0001, 0.75, 7500.0, 1.0),
      "file://c" to NextFileProbability(0.25, 0.0001, 0.75, 2500.0, 0.33333333333),
      "file://d" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
      "file://x" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0)
    )
  }

  fun `test bigram with multiple forgotten prefix tokens`() {
    doTestBiGram(
      listOf("file://x", "file://y", "file://x", "file://y", "file://z",
             "file://a", "file://b", "file://a", "file://c", "file://a", "file://b", "file://a", "file://b", "file://a"),
      3, 3, true,
      "file://a" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
      "file://b" to NextFileProbability(0.75, 0.0001, 0.75, 7500.0, 1.0),
      "file://c" to NextFileProbability(0.25, 0.0001, 0.75, 2500.0, 0.33333333333),
      "file://d" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
      "file://x" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
      "file://y" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0),
      "file://z" to NextFileProbability(0.0, 0.0001, 0.75, 0.0, 0.0)
    )
  }

  fun `test bigram with multiple forgotten tokens`() {
    doTestBiGram(
      listOf("file://x", "file://a", "file://y",
             "file://b", "file://a", "file://c", "file://a", "file://b", "file://a", "file://b", "file://a"),
      3, 3, false,
      "file://a" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0),
      "file://b" to NextFileProbability(0.5, 0.0001, 0.5, 5000.0, 1.0),
      "file://c" to NextFileProbability(0.25, 0.0001, 0.5, 2500.0, 0.5),
      "file://d" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0),
      "file://x" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0),
      "file://y" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0),
      "file://z" to NextFileProbability(0.0, 0.0001, 0.5, 0.0, 0.0)
    )
  }
}