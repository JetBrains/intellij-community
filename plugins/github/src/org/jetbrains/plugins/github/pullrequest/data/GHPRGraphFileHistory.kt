// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.util.asSafely
import org.jetbrains.plugins.github.api.data.GHCommit
import java.util.concurrent.ConcurrentHashMap

class GHPRGraphFileHistory(private val commitsMap: Map<String, GHCommitWithPatches>,
                           private val lastCommit: GHCommit,
                           private val finalFilePath: String) : GHPRFileHistory {

  private val containsCache = ConcurrentHashMap<Pair<String, String>, Boolean>()

  override fun contains(commitSha: String, filePath: String): Boolean = containsCache.getOrPut(commitSha to filePath) {
    val lastCommitWithPatches = commitsMap[lastCommit.oid] ?: return@getOrPut false
    isSameFile(lastCommitWithPatches, finalFilePath, commitSha, filePath)
  }

  private fun isSameFile(knownCommit: GHCommitWithPatches, knownFilePath: String, commitSha: String, filePath: String): Boolean {
    if (knownCommit.cumulativePatches.none { it.asSafely<TextFilePatch>()?.filePath == knownFilePath }) return false

    val newKnownFilePath = knownCommit.commitPatches.find { it.asSafely<TextFilePatch>()?.filePath == knownFilePath }?.beforeFileName
    if (knownCommit.sha == commitSha && (knownFilePath == filePath || newKnownFilePath == filePath)) return true

    val knownParents = knownCommit.commit.parents.mapNotNull { commitsMap[it.oid] }
    return knownParents.any { isSameFile(it, newKnownFilePath ?: knownFilePath, commitSha, filePath) }
  }

  override fun compare(commitSha1: String, commitSha2: String): Int {
    TODO("Not supported for now")
    /*if (commitSha1 == commitSha2) return 0
    val commit1 = commitsMap[commitSha1] ?: error("Invalid commit sha: $commitSha1")
    val commit2 = commitsMap[commitSha2] ?: error("Invalid commit sha: $commitSha2")

    // check if commitSha2 is a parent of commitSha1
    for (commit in Traverser.forGraph(commitsGraph).depthFirstPreOrder(commit1.commit)) {
      if (commit == commit2.commit) return 1
    }

    // check if commitSha1 is a parent of commitSha2
    for (commit in Traverser.forGraph(commitsGraph).depthFirstPreOrder(commit2.commit)) {
      if (commit == commit1.commit) return -1
    }

    // We break contract here by returning -2 for unconnected commits
    return -2*/
  }

  override fun getPatches(parent: String, child: String, includeFirstKnownPatch: Boolean, includeLastPatch: Boolean): List<TextFilePatch> {
    TODO("Not supported for now")
  }

  companion object {
    private val TextFilePatch.filePath
      get() = (afterName ?: beforeName)!!
  }
}
