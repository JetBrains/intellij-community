// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.util.Range
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.api.data.GHCommit

interface GHPRChangesProvider {
  val changes: List<Change>
  val changesByCommits: Map<GHCommit, List<Change>>

  fun findChangeDiffData(change: Change): DiffData?

  interface DiffData {
    val commitSha: String
    val filePath: String
    val diffRanges: List<Range>
    val diffRangesWithoutContext: List<Range>
    val linesMapper: GHPRChangedFileLinesMapper

    fun contains(commitSha: String, filePath: String): Boolean
  }
}