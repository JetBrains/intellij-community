// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
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

  class FileHistory(commitHashes: List<String>) : Comparator<String> {
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

    override fun compare(commitSha1: String, commitSha2: String): Int {
      if (commitSha1 == commitSha2) return 0

      for ((sha, _) in history) {
        if (sha == commitSha1) return -1
        if (sha == commitSha2) return 1
      }
      error("Unknown commit sha")
    }

    fun getPatches(fromCommit: String, toCommit: String, dropHead: Boolean, dropTail: Boolean): List<TextFilePatch> {
      val patches = mutableListOf<TextFilePatch>()

      var foundFrom = false
      var lastFound: TextFilePatch? = null

      for ((sha, entry) in history) {

        if (!foundFrom) {
          if (entry.patch != null) lastFound = entry.patch

          if (sha == fromCommit) {
            foundFrom = true
            if (!dropHead) {
              val patchToAdd = entry.patch ?: lastFound
                               ?: error("Original patch was not found")
              patches.add(patchToAdd)
            }
          }
        }
        else {
          if (dropTail) {
            if (sha == toCommit) break
            entry.patch?.let { patches.add(it) }
          }
          else {
            entry.patch?.let { patches.add(it) }
            if (sha == toCommit) break
          }
        }
      }
      return patches
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

    fun mapPosition(fromCommitSha: String,
                    side: Side, line: Int): Pair<Side, Int>? {

      val comparison = fileHistory.compare(fromCommitSha, commitSha)
      if (comparison == 0) return side to line
      if (comparison < 0) {
        val patches = fileHistory.getPatches(fromCommitSha, commitSha, false, true)
        return transferLine(patches, side, line, false)
      }
      else {
        val patches = fileHistory.getPatches(commitSha, fromCommitSha, true, false)
        return transferLine(patches, side, line, true)
      }
    }

    private fun transferLine(patchChain: List<TextFilePatch>, side: Side, line: Int, rightToLeft: Boolean): Pair<Side, Int>? {
      // points to the same patch
      if (patchChain.isEmpty()) return side to line

      val patches = if (rightToLeft) patchChain.asReversed() else patchChain
      val transferFrom = if (rightToLeft) Side.RIGHT else Side.LEFT

      var currentSide: Side = side
      var currentLine: Int = line

      for (patch in patches) {
        if (currentSide == transferFrom) {
          val changeOnlyRanges = patch.hunks.map { hunk ->
            val ranges = GHPatchHunkUtil.getChangeOnlyRanges(hunk)
            if (rightToLeft) ranges.map { reverseRange(it) } else ranges
          }.flatten()

          var offset = 0
          loop@ for (range in changeOnlyRanges) {
            when {
              currentLine < range.start1 ->
                break@loop
              currentLine in range.start1 until range.end1 ->
                return null
              currentLine >= range.end1 ->
                offset += (range.end2 - range.start2) - (range.end1 - range.start1)
            }
          }
          currentLine += offset
        }
        else {
          currentSide = transferFrom
        }
      }
      return currentSide to currentLine
    }

    private fun reverseRange(range: Range) = Range(range.start2, range.end2, range.start1, range.end1)
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