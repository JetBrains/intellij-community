// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter
import com.intellij.completion.ngram.slp.modeling.Model
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel
import com.intellij.completion.ngram.slp.modeling.runners.ModelRunner
import org.jetbrains.annotations.TestOnly

class FilePredictionNGramModelRunner(private var nGramLength: Int, model: Model, vocabulary: FilePredictionNGramVocabulary)
  : ModelRunner(model, vocabulary) {

  companion object {
    private const val LAST_STORED_FILES: Int = 100

    fun createNewModelRunner(order: Int): FilePredictionNGramModelRunner {
      return FilePredictionNGramModelRunner(
        order,
        model = JMModel(counter = ArrayTrieCounter(), order = order),
        vocabulary = FilePredictionNGramVocabulary(LAST_STORED_FILES)
      )
    }

    fun createModelRunner(order: Int,
                          counter: ArrayTrieCounter,
                          vocabulary: FilePredictionNGramVocabulary): FilePredictionNGramModelRunner {
      return FilePredictionNGramModelRunner(
        order,
        model = JMModel(counter = counter, order = order),
        vocabulary = vocabulary
      )
    }
  }

  private var prevFiles: MutableList<String> = arrayListOf()

  fun learnNextFile(fileUrl: String) {
    updatePrevFiles(fileUrl)

    if (vocabulary is FilePredictionNGramVocabulary) {
      val indices = vocabulary.toIndicesWithLimit(prevFiles, model)
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
    if (prevFiles.size < nGramLength) {
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

  @TestOnly
  fun cleanPreviousFilesSequence() {
    prevFiles.clear()
  }
}

class FilePredictionScorer(private val scoringFunction: (List<String>) -> Double, prefix: Array<String>) {
  private val tokens: MutableList<String> = mutableListOf(*prefix, "!placeholder!")

  fun score(value: String): Double {
    tokens[tokens.lastIndex] = value
    return scoringFunction(tokens)
  }
}