// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.counting.trie.AbstractTrie
import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.exists
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path

object FilePredictionNGramSerializer {
  private val LOG: Logger = Logger.getInstance(FilePredictionNGramSerializer::class.java)

  fun saveNGrams(path: Path, runner: FilePredictionNGramModelRunner) {
    try {
      ObjectOutputStream(Files.newOutputStream(path)).use { oos ->
        val vocabulary = runner.vocabulary as FilePredictionNGramVocabulary
        vocabulary.writeExternal(oos)

        val counter = (runner.model as NGramModel).counter as AbstractTrie
        counter.writeExternal(oos)
      }
    }
    catch (e: IOException) {
      LOG.warn("Cannot serialize file sequence ngrams", e)
    }
  }

  fun loadNGrams(path: Path?, nGramLength: Int): FilePredictionNGramModelRunner {
    try {
      if (path != null && path.exists()) {
        return ObjectInputStream(Files.newInputStream(path)).use { ois ->
          val vocabulary = FilePredictionNGramVocabulary(100)
          vocabulary.readExternal(ois)

          val counter = ArrayTrieCounter()
          counter.readExternal(ois)
          return@use FilePredictionNGramModelRunner.createModelRunner(nGramLength, counter, vocabulary)
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Cannot deserialize file sequence ngrams", e)
    }
    return FilePredictionNGramModelRunner.createNewModelRunner(nGramLength)
  }
}