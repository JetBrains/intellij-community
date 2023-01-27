// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.openapi.vcs.changes.Change

/**
 * Represents a set of commits with their changes
 */
interface GitParsedChangesBundle {
  val changes: List<Change>
  val changesByCommits: Map<String, Collection<Change>>
  val linearHistory: Boolean

  fun findChangeDiffData(change: Change): GitChangeDiffData?

  fun findCumulativeChange(commitSha: String, filePath: String): Change?
}