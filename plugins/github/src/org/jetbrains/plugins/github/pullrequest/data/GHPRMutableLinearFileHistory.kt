// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.diff.impl.patch.TextFilePatch

class GHPRMutableLinearFileHistory(commitHashes: List<String>) : GHPRFileHistory {

  private val history: MutableMap<String, Entry>

  val firstKnownFilePath: String?
    get() = (history.values.find { it.patch != null }?.patch ?: error("Empty history")).beforeName

  val lastKnownFilePath: String?
    get() {
      val lastFilePath = (history.values.findLast { it.patch != null }?.patch ?: error("Empty history")).afterName
      return lastFilePath ?: firstKnownFilePath
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

  override fun contains(commitSha: String, filePath: String): Boolean {
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

  override fun getPatches(parent: String, child: String, includeFirstKnownPatch: Boolean, includeLastPatch: Boolean): List<TextFilePatch> {
    val patches = mutableListOf<TextFilePatch>()

    var foundParent = false
    var lastFound: TextFilePatch? = null

    for ((sha, entry) in history) {

      if (!foundParent) {
        if (entry.patch != null) lastFound = entry.patch

        if (sha == parent) {
          foundParent = true
          if (!includeFirstKnownPatch) {
            val patchToAdd = entry.patch ?: lastFound
                             ?: error("Original patch was not found")
            patches.add(patchToAdd)
          }
        }
      }
      else {
        if (includeLastPatch) {
          if (sha == child) break
          entry.patch?.let { patches.add(it) }
        }
        else {
          entry.patch?.let { patches.add(it) }
          if (sha == child) break
        }
      }
    }
    return patches
  }

  private class Entry(val patch: TextFilePatch?) {
    val filePath = patch?.filePath
  }

  companion object {
    private val TextFilePatch.filePath
      get() = (afterName ?: beforeName)!!
  }
}