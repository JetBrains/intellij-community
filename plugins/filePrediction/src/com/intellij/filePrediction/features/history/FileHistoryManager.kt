// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.filePrediction.features.history.ngram.FilePredictionNGramFeatures
import com.intellij.internal.ml.ngram.NGramIncrementalModelRunner
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly

data class FileHistoryFeatures(val position: Int?, val uniGram: NextFileProbability, val biGram: NextFileProbability)

data class NextFileProbability(
  val mle: Double, val minMle: Double, val maxMle: Double, val mleToMin: Double, val mleToMax: Double
)

class FileHistoryManager(private val model: NGramIncrementalModelRunner,
                         private var state: FilePredictionHistoryState,
                         private val recentFilesLimit: Int) {
  val helper: FileSequenceModelHelper = FileSequenceModelHelper()

  @TestOnly
  @Synchronized
  fun getState() = state

  @Synchronized
  fun saveFileHistory(project: Project) {
    FileHistoryPersistence.saveNGrams(project, model)
    FileHistoryPersistence.saveFileHistory(project, state)
  }

  @Synchronized
  fun onFileOpened(fileUrl: String) {
    model.learnNextToken(fileUrl)

    val entry = findOrAddEntry(fileUrl, recentFilesLimit)
    val code = entry.code

    helper.onFileOpened(state.root, code, state.prevFile)
    state.prevFile = code
  }

  private fun findOrAddEntry(fileUrl: String, limit: Int): RecentFileEntry {
    val index = findEntry(fileUrl)
    val size = state.recentFiles.size
    if (index < 0 || index >= size) {
      val removedCode = trimRecentFilesSize(limit - 1)

      val newEntry = RecentFileEntry()
      newEntry.fileUrl = fileUrl
      newEntry.code = nextCode(removedCode)

      state.recentFiles.add(newEntry)
      return newEntry
    }

    val entry = state.recentFiles[index]
    state.recentFiles.removeAt(index)
    state.recentFiles.add(entry)
    return entry
  }

  private fun nextCode(removed: Int?): Int {
    if (removed != null) {
      return removed
    }
    val nextFileCode = state.nextFileCode
    state.nextFileCode++
    return nextFileCode
  }

  private fun trimRecentFilesSize(limit: Int): Int? {
    var removedCode: Int? = null
    while (state.recentFiles.size > limit) {
      val code = state.recentFiles.removeAt(0).code
      helper.remove(state.root, code)
      removedCode = code
    }
    return removedCode
  }

  @Synchronized
  fun calcHistoryFeatures(fileUrl: String): FileHistoryFeatures {
    val index = findEntry(fileUrl)
    val size = state.recentFiles.size

    val fileWasPreviouslyOpened = index in 0 until size
    val entry = if (fileWasPreviouslyOpened) state.recentFiles[index] else null
    val position = if (fileWasPreviouslyOpened) size - index - 1 else null
    val uniGram = helper.calculateUniGramProb(state.root, entry?.code)
    val biGram = helper.calculateBiGramProb(state.root, entry?.code, state.prevFile)
    return FileHistoryFeatures(position, uniGram, biGram)
  }

  fun calcNGramFeatures(candidates: List<String>): FilePredictionNGramFeatures {
    val result: MutableMap<String, Double> = hashMapOf()
    val scorer = model.createScorer()
    for (candidate in candidates) {
      result[candidate] = scorer.score(candidate)
    }
    return FilePredictionNGramFeatures(result)
  }

  private fun findEntry(fileUrl: String): Int {
    var i = state.recentFiles.size - 1
    while (i >= 0 && state.recentFiles[i].fileUrl != fileUrl) {
      i--
    }
    return i
  }

  @Synchronized
  fun size(): Int = state.recentFiles.size

  @Synchronized
  fun cleanup() {
    state.recentFiles.clear()
    state.root.clear()
  }
}