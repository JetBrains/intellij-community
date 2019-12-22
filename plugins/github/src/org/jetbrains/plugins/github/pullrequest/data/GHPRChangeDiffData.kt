// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.util.Range
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import org.jetbrains.plugins.github.util.GHPatchHunkUtil

sealed class GHPRChangeDiffData(val commitSha: String, val filePath: String,
                                private val patch: TextFilePatch, private val cumulativePatch: TextFilePatch,
                                protected val fileHistory: FileHistory) {

  val diffRanges: List<Range> by lazy(LazyThreadSafetyMode.NONE) {
    patch.hunks.map(GHPatchHunkUtil::getRange)
  }
  val diffRangesWithoutContext: List<Range> by lazy(LazyThreadSafetyMode.NONE) {
    patch.hunks.map(GHPatchHunkUtil::getChangeOnlyRanges).flatten()
  }
  val linesMapper: GHPRChangedFileLinesMapper by lazy(LazyThreadSafetyMode.NONE) {
    GHPRChangedFileLinesMapperImpl(cumulativePatch)
  }

  fun contains(commitSha: String, filePath: String): Boolean {
    return fileHistory.contains(commitSha, filePath)
  }

  class FileHistory(commitHashes: List<String>) {
    private val history: MutableMap<String, Entry>

    val initialFilePath: String?
      get() = (history.values.find { it.patch != null }?.patch ?: error("Empty history")).beforeName

    val filePath: String?
      get() {
        val lastFilePath = (history.values.findLast { it.patch != null }?.patch ?: error("Empty history")).afterName
        return lastFilePath ?: initialFilePath
      }

    init {
      history = LinkedHashMap()
      for (sha in commitHashes) {
        history[sha] = Entry(null)
      }
    }

    fun append(commitSha: String, patch: TextFilePatch) {
      val entry = history[commitSha]
      assert(entry != null && entry.patch == null)
      history[commitSha] = Entry(patch)
    }

    fun contains(commitSha: String, filePath: String): Boolean {
      var lastPath: String? = null
      for ((sha, entry) in history) {
        val entryPath = entry.filePath
        if (entryPath != null) lastPath = entryPath

        if (sha == commitSha && lastPath == filePath) return true
      }
      return false
    }

    private class Entry(val patch: TextFilePatch?) {
      val filePath = patch?.filePath
    }
  }

  class Commit(commitSha: String, filePath: String,
               patch: TextFilePatch, cumulativePatch: TextFilePatch,
               fileHistory: FileHistory)
    : GHPRChangeDiffData(commitSha, filePath,
                         patch, cumulativePatch,
                         fileHistory) {

  }

  class Cumulative(commitSha: String, filePath: String,
                   patch: TextFilePatch,
                   fileHistory: FileHistory)
    : GHPRChangeDiffData(commitSha, filePath,
                         patch, patch,
                         fileHistory)

  companion object {
    private val TextFilePatch.filePath
      get() = (afterName ?: beforeName)!!
  }
}