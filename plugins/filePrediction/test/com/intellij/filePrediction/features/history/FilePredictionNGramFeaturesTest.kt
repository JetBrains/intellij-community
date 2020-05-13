// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

@Suppress("SameParameterValue")
class FilePredictionNGramFeaturesTest : FilePredictionHistoryBaseTest() {

  private fun doTestUniGram(openedFiles: List<String>, size: Int, vararg expected: Pair<String, NextFileProbability>) {
    doTestInternal(openedFiles, size, 5) { manager ->
      var total = 0.0
      expected.forEach {
        val uniGram = manager.calcHistoryFeatures(it.first).uniGram
        assertNextFileProbabilityEquals(it.first, it.second, uniGram)
        total += uniGram.mle
      }
      assertEquals(1.0, total)
    }
  }

  private fun doTestBiGram(openedFiles: List<String>, size: Int, withTotal: Boolean, vararg expected: Pair<String, NextFileProbability>) {
    doTestBiGramInternal(openedFiles, size, withTotal, 5, *expected)
  }

  private fun doTestBiGramWithCustomLimit(openedFiles: List<String>, size: Int, withTotal: Boolean, limit: Int, vararg expected: Pair<String, NextFileProbability>) {
    doTestBiGramInternal(openedFiles, size, withTotal, limit, *expected)
  }

  private fun doTestBiGramInternal(openedFiles: List<String>, size: Int, withTotal: Boolean, limit: Int, vararg expected: Pair<String, NextFileProbability>) {
    doTestInternal(openedFiles, size, limit) { manager ->
      var total = 0.0
      expected.forEach {
        val biGram = manager.calcHistoryFeatures(it.first).biGram
        assertNextFileProbabilityEquals(it.first, it.second, biGram)
        total += biGram.mle
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
      "file://b" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0),
      "file://a" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0)
    )
  }

  fun `test unigram with repeated file`() {
    doTestUniGram(
      listOf("file://a", "file://b", "file://c", "file://a", "file://a", "file://b", "file://a", "file://b"), 3,
      "file://a" to NextFileProbability(0.5, 0.125, 0.5, 4.0, 1.0),
      "file://b" to NextFileProbability(0.375, 0.125, 0.5, 3.0, 0.75),
      "file://c" to NextFileProbability(0.125, 0.125, 0.5, 1.0, 0.25),
      "file://d" to NextFileProbability(0.0, 0.125, 0.5, 0.0, 0.0)
    )
  }

  fun `test bigram with the single file`() {
    doTestBiGram(
      listOf("file://a", "file://a", "file://a"), 1, true,
      "file://a" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0)
    )
  }

  fun `test bigram with all unique files`() {
    doTestBiGram(
      listOf("file://a", "file://b", "file://c", "file://d"), 4, false,
      "file://a" to NextFileProbability(0.0, 0.0, 0.0, 0.0, 0.0),
      "file://b" to NextFileProbability(0.0, 0.0, 0.0, 0.0, 0.0),
      "file://c" to NextFileProbability(0.0, 0.0, 0.0, 0.0, 0.0),
      "file://d" to NextFileProbability(0.0, 0.0, 0.0, 0.0, 0.0)
    )
  }

  fun `test bigram with a new file`() {
    doTestBiGram(
      listOf("file://a", "file://a", "file://a"), 1, true,
      "file://b" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0),
      "file://a" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0)
    )
  }

  fun `test bigram with one successor`() {
    doTestBiGram(
      listOf("file://a", "file://b", "file://a", "file://c", "file://a", "file://b", "file://a", "file://b"), 3, true,
      "file://a" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0),
      "file://b" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0),
      "file://c" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0),
      "file://d" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0)
    )
  }

  fun `test bigram with two successors`() {
    doTestBiGram(
      listOf("file://a", "file://b", "file://a", "file://c", "file://a", "file://b", "file://a", "file://b", "file://a"), 3, true,
      "file://a" to NextFileProbability(0.0, 0.25, 0.75, 0.0, 0.0),
      "file://b" to NextFileProbability(0.75, 0.25, 0.75, 3.0, 1.0),
      "file://c" to NextFileProbability(0.25, 0.25, 0.75, 1.0, 0.33333333333),
      "file://d" to NextFileProbability(0.0, 0.25, 0.75, 0.0, 0.0)
    )
  }

  fun `test bigram with all possible successors`() {
    doTestBiGram(
      listOf("file://c", "file://c", "file://a", "file://c", "file://a", "file://b", "file://c", "file://b", "file://c"), 3, true,
      "file://a" to NextFileProbability(0.5, 0.25, 0.5, 2.0, 1.0),
      "file://b" to NextFileProbability(0.25, 0.25, 0.5, 1.0, 0.5),
      "file://c" to NextFileProbability(0.25, 0.25, 0.5, 1.0, 0.5),
      "file://d" to NextFileProbability(0.0, 0.25, 0.5, 0.0, 0.0)
    )
  }

  fun `test bigram with removed oldest file`() {
    doTestBiGramWithCustomLimit(
      listOf("file://a", "file://b", "file://c", "file://d", "file://b"), 3, true, 3,
      "file://a" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0),
      "file://b" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0),
      "file://c" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0),
      "file://d" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0)
    )
  }

  fun `test bigram with removed file with usages`() {
    doTestBiGramWithCustomLimit(
      listOf("file://b", "file://a", "file://b", "file://c", "file://d", "file://b"), 3, true, 3,
      "file://a" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0),
      "file://b" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0),
      "file://c" to NextFileProbability(1.0, 1.0, 1.0, 1.0, 1.0),
      "file://d" to NextFileProbability(0.0, 1.0, 1.0, 0.0, 0.0)
    )
  }
}