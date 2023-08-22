// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.openapi.diff.impl.patch.TextFilePatch

/**
 * Represents a history of changes in a single file
 */
interface GitFileHistory : Comparator<String> {

  /**
   * Find first commit in history
   */
  fun findStartCommit(): String?

  /**
   * Find parent commit of [commitSha]
   */
  fun findFirstParent(commitSha: String): String?

  /**
   * Check if file history contains a record about [filePath] at or prior to commit [commitSha]
   */
  fun contains(commitSha: String, filePath: String): Boolean

  /**
   * Compare two commits in history to find the parent-chile relation
   *
   * @return less than 0 if [commitSha1] is a parent of [commitSha2]
   *         0 if it's the same commit
   *         more than 0 if [commitSha2] is a parent of [commitSha1]
   */
  override fun compare(commitSha1: String, commitSha2: String): Int

  /**
   * Retrieve a chain of patches between commits [parent] and [child]
   */
  fun getPatchesBetween(parent: String, child: String): List<TextFilePatch>
}