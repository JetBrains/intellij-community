// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

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
        oos.writeDouble(runner.lambda)

        val vocabulary = runner.vocabulary as FilePredictionNGramVocabulary
        oos.writeInt(vocabulary.maxSize)
        oos.writeInt(vocabulary.fileSequence.maxSequenceLength)
        vocabulary.writeExternal(oos)

        val counter = (runner.model as NGramModel).counter as ArrayTrieCounter
        counter.writeExternal(oos)

        oos.writeInt(runner.prevFiles.size)
        for (file in runner.prevFiles) {
          oos.writeObject(file)
        }
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
          val lambda = ois.readDouble()

          val maxVocabularySize = ois.readInt()
          val maxSequenceSize = ois.readInt()
          val fileSequence = FilePredictionRecentFileSequence(maxSequenceSize, nGramLength)
          val vocabulary = FilePredictionNGramVocabulary(maxVocabularySize, fileSequence)
          vocabulary.readExternal(ois)

          val counter = ArrayTrieCounter()
          counter.readExternal(ois)
          val runner = FilePredictionNGramModelRunner.createModelRunner(nGramLength, lambda, counter, vocabulary)
          val prevFilesSize = ois.readInt()
          for (i in 0 until prevFilesSize) {
            runner.prevFiles.add(ois.readObject() as String)
          }
          return@use runner
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Cannot deserialize file sequence ngrams", e)
    }
    return FilePredictionNGramModelRunner.createNewModelRunner(nGramLength)
  }
}