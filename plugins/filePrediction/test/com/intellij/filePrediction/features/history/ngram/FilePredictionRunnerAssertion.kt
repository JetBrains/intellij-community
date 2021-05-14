// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.internal.ml.ngram.NGramIncrementalModelRunner
import com.intellij.internal.ml.ngram.VocabularyWithLimit
import junit.framework.TestCase

internal class FilePredictionRunnerAssertion {
  private var withVocabulary: Boolean = false
  private var vocabularySize: Int = 3

  private var withFileSequence: Boolean = false
  private var fileSequence: List<Int> = emptyList()

  private var withRecentFiles: Boolean = false
  private var nextFileSequenceIdx: Int = -1
  private var recentFiles: List<String> = emptyList()
  private var recentFilesIdx: List<Int> = emptyList()

  fun withVocabulary(size: Int): FilePredictionRunnerAssertion {
    withVocabulary = true
    vocabularySize = size
    return this
  }

  fun withFileSequence(files: List<Int>): FilePredictionRunnerAssertion {
    withFileSequence = true
    fileSequence = files
    return this
  }

  fun withRecentFiles(nextIdx: Int, recent: List<String>, idx: List<Int>): FilePredictionRunnerAssertion {
    withRecentFiles = true
    nextFileSequenceIdx = nextIdx
    recentFiles = recent
    recentFilesIdx = idx
    return this
  }

  fun assert(runner: NGramIncrementalModelRunner) {
    TestCase.assertTrue(runner.vocabulary is VocabularyWithLimit)
    val vocabulary = runner.vocabulary as VocabularyWithLimit

    if (withVocabulary) {
      // unknown token is always added to wordIndices, therefore, actual size will be always size + 1
      TestCase.assertEquals(vocabularySize, vocabulary.wordIndices.size - 1)
    }

    if (withFileSequence) {
      val actualFileSequence = vocabulary.recentSequence.subListFromStart(vocabulary.recentSequence.size())
      TestCase.assertEquals("File sequence is different from expected", fileSequence, actualFileSequence)
    }

    if (withRecentFiles) {
      val recent = vocabulary.recent
      TestCase.assertEquals("Next file sequence index is different from expected", nextFileSequenceIdx, recent.lastIndex() + 1)

      val tokens = recent.getRecentTokens()
      TestCase.assertEquals("Recent files are different from expected", recentFiles, tokens.map { it.first })
      TestCase.assertEquals("Recent files indices are different from expected", recentFilesIdx, tokens.map { it.second })
    }
  }
}