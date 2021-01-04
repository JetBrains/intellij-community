// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel
import com.intellij.completion.ngram.slp.modeling.runners.ModelRunner

class FilePredictionNGramModelRunner(private val nGramOrder: Int,
                                     val lambda: Double,
                                     model: NGramModel, vocabulary: FilePredictionNGramVocabulary)
  : ModelRunner(model, vocabulary) {

  companion object {
    private const val DEFAULT_LAMBDA: Double = 0.5
    private const val LAST_STORED_FILES: Int = 200
    private const val LAST_STORED_FILES_SEQUENCE: Int = 10000

    fun createNewModelRunner(order: Int, lambda: Double = DEFAULT_LAMBDA): FilePredictionNGramModelRunner {
      return FilePredictionNGramModelRunner(
        nGramOrder = order,
        lambda = lambda,
        model = JMModel(counter = ArrayTrieCounter(), order = order),
        vocabulary = FilePredictionNGramVocabulary(LAST_STORED_FILES, FilePredictionRecentFileSequence(LAST_STORED_FILES_SEQUENCE, order))
      )
    }

    fun createModelRunner(order: Int,
                          lambda: Double,
                          counter: ArrayTrieCounter,
                          vocabulary: FilePredictionNGramVocabulary): FilePredictionNGramModelRunner {
      return FilePredictionNGramModelRunner(
        order,
        lambda,
        model = JMModel(counter = counter, order = order, lambda = lambda),
        vocabulary = vocabulary
      )
    }
  }

  init {
    assert(vocabulary.maxSize >= nGramOrder && vocabulary.fileSequence.maxSequenceLength >= nGramOrder)
  }

  internal var prevFiles: MutableList<String> = arrayListOf()

  fun learnNextFile(fileUrl: String) {
    updatePrevFiles(fileUrl)

    if (vocabulary is FilePredictionNGramVocabulary && model is NGramModel) {
      val indices = vocabulary.toIndicesWithLimit(prevFiles, model)
      if (indices.size > 1) {
        model.forget(indices.subList(0, indices.size - 1))
      }
      model.learn(indices)
    }
  }

  fun createScorer(): FilePredictionScorer {
    val prefix = if (prevFiles.size > 1) prevFiles.subList(1, prevFiles.size).toTypedArray() else emptyArray()
    return FilePredictionScorer({ scoreFiles(it) }, prefix)
  }

  private fun scoreFiles(files: List<String>): Double {
    if (vocabulary is FilePredictionNGramVocabulary) {
      val queryIndices = vocabulary.toExistingIndices(files)
      return model.modelToken(queryIndices, queryIndices.size - 1).first
    }
    return 0.0
  }

  private fun updatePrevFiles(fileUrl: String) {
    if (prevFiles.size < nGramOrder) {
      prevFiles.add(fileUrl)
      return
    }

    shiftFiles()
    prevFiles[prevFiles.size - 1] = fileUrl
  }

  private fun shiftFiles() {
    for (i in 0..prevFiles.size - 2) {
      prevFiles[i] = prevFiles[i + 1]
    }
  }
}

class FilePredictionScorer(private val scoringFunction: (List<String>) -> Double, prefix: Array<String>) {
  private val tokens: MutableList<String> = mutableListOf(*prefix, "!placeholder!")

  fun score(value: String): Double {
    tokens[tokens.lastIndex] = value
    return scoringFunction(tokens)
  }
}