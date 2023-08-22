// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.openapi.diff.impl.patch.TextFilePatch

class MutableLinearGitFileHistory(private val commitHashes: List<String>) : GitFileHistory {

  private val history: MutableMap<String, Entry> = LinkedHashMap()

  private val firstKnownFilePath: String?
    get() = (history.values.find { it.patch != null }?.patch ?: error("Empty history")).beforeName

  val lastKnownFilePath: String?
    get() {
      val lastFilePath = (history.values.findLast { it.patch != null }?.patch ?: error("Empty history")).afterName
      return lastFilePath ?: firstKnownFilePath
    }

  init {
    for (sha in commitHashes) {
      history[sha] = Entry(null)
    }
  }

  override fun findStartCommit(): String? = commitHashes.firstOrNull()

  override fun findFirstParent(commitSha: String): String? {
    var parentSha: String? = null
    for (commitHash in commitHashes) {
      if (commitSha == commitHash) {
        break
      }
      parentSha = commitHash
    }
    return parentSha
  }

  fun append(commitSha: String, patch: TextFilePatch) {
    val entry = history[commitSha]
    check(entry != null) { "Adding entry for an unknown commit $commitSha. Known commits - $commitHashes" }
    check(entry.patch == null) { "Entry patch was already recorded for commit $commitSha. " +
                                 "Existing patch - ${entry.patch?.headerString}. " +
                                 "New patch - ${patch.headerString}" }
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

  override fun getPatchesBetween(parent: String, child: String): List<TextFilePatch> {
    val patches = mutableListOf<TextFilePatch>()

    var foundParent = false
    for ((sha, entry) in history) {
      if (!foundParent) {
        if (sha == parent) {
          foundParent = true
        }
      }
      else {
        entry.patch?.let { patches.add(it) }
        if (sha == child) break
      }
    }
    return patches
  }

  private class Entry(val patch: TextFilePatch?) {
    val filePath: String? = patch?.filePath
  }

  companion object {
    private val TextFilePatch.filePath
      get() = (afterName ?: beforeName)!!

    private val TextFilePatch.headerString
      get() = """$beforeVersionId $beforeName -> $afterVersionId $afterName"""
  }
}