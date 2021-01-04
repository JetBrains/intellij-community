// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import kotlin.math.abs

class FilePredictionNGramSerializationTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  private fun doTest(openedFiles: List<String>, nGramLength: Int) {
    for (lambda in arrayOf(0.1, 0.3, 0.5, 0.8, 1.0)) {
      doTestInternal(openedFiles, nGramLength, lambda)
    }
  }

  private fun doTestInternal(openedFiles: List<String>, nGramLength: Int, lambda: Double) {
    val model = JMModel(counter = ArrayTrieCounter(), order = nGramLength, lambda = lambda)
    val vocabulary = FilePredictionNGramVocabulary(112, FilePredictionRecentFileSequence(12345, nGramLength))
    val runner = FilePredictionNGramModelRunner(nGramLength, lambda, model, vocabulary)
    for (file in openedFiles) {
      runner.learnNextFile(file)
    }

    val storage = FileUtil.createTempFile("testFileHistory", "-ngram").toPath()
    FilePredictionNGramSerializer.saveNGrams(storage, runner)

    val deserializedRunner = FilePredictionNGramSerializer.loadNGrams(storage, nGramLength)
    for (file in openedFiles) {
      val before = runner.createScorer().score(file)
      val after = deserializedRunner.createScorer().score(file)
      assertDoubleEquals(file, before, after)
    }

    // files learned after deserialization behave the same as before
    for (file in openedFiles) {
      runner.learnNextFile(file)
      deserializedRunner.learnNextFile(file)
    }

    for (file in openedFiles) {
      val before = runner.createScorer().score(file)
      val after = deserializedRunner.createScorer().score(file)
      assertDoubleEquals(file, before, after)
    }
  }

  private fun assertDoubleEquals(itemName: String, expected: Double, actual: Double) {
    assertTrue("$itemName isn't equal to expected. Expected: $expected, Actual: $actual", abs(expected - actual) < 0.0000000001)
  }

  fun `test unigrams with all unique files`() {
    doTest(
      listOf("a", "b", "c", "d", "e", "f"), 1
    )
  }

  fun `test unigrams with single files`() {
    doTest(
      listOf("a", "a", "a", "a"), 1
    )
  }

  fun `test unigrams with multiple files`() {
    doTest(
      listOf("a", "a", "c", "b", "a", "c"), 1
    )
  }

  fun `test bigrams with all unique files`() {
    doTest(
      listOf("a", "b", "c", "d", "e", "f"), 2
    )
  }

  fun `test bigrams with single files`() {
    doTest(
      listOf("a", "a", "a", "a"), 2
    )
  }

  fun `test bigrams with one existing combination`() {
    doTest(
      listOf("a", "b", "a", "b", "a"), 2
    )
  }

  fun `test bigrams with one multiple combinations`() {
    doTest(
      listOf("a", "b", "a", "c", "a"), 2
    )
  }

  fun `test trigrams with all unique files`() {
    doTest(
      listOf("a", "b", "c", "d", "e", "f"), 3
    )
  }

  fun `test trigrams with single files`() {
    doTest(
      listOf("a", "a", "a", "a"), 3
    )
  }

  fun `test trigrams with one existing combination`() {
    doTest(
      listOf("a", "b", "c", "a", "b", "c", "a"), 3
    )
  }

  fun `test trigrams with multiple combinations`() {
    doTest(
      listOf("a", "b", "c", "a", "c", "b", "a"), 3
    )
  }

  fun `test ngrams with multiple combinations`() {
    doTest(
      listOf("a", "b", "c", "a", "c", "b", "c", "c", "b", "b", "a", "d", "c", "b", "e", "a", "b", "e", "c", "b", "b", "e"), 6
    )
  }

  fun `test ngrams with repeated symbols combinations`() {
    doTest(
      listOf("a", "b", "a", "a", "a", "a", "a", "a", "b", "b", "b", "a", "c", "c", "c", "c"), 6
    )
  }
}