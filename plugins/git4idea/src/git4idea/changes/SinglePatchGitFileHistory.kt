// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.openapi.diff.impl.patch.TextFilePatch

class SinglePatchGitFileHistory(private val patch: TextFilePatch) : GitFileHistory {

  override fun findStartCommit(): String? = patch.beforeVersionId

  override fun findFirstParent(commitSha: String): String? = null

  override fun contains(commitSha: String, filePath: String): Boolean {
    return patch.filePath == filePath
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

  override fun getPatchesBetween(parent: String, child: String): List<TextFilePatch> = emptyList()

  companion object {
    private val TextFilePatch.filePath
      get() = (afterName ?: beforeName)!!
  }
}
