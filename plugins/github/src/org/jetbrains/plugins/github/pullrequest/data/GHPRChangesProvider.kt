// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.util.Range
import com.intellij.openapi.vcs.changes.Change

interface GHPRChangesProvider {
  val lastCommitSha: String
  val changes: List<Change>

  fun getFilePath(change: Change): String
  fun findDiffRanges(change: Change): List<Range>?
  fun findDiffRangesWithoutContext(change: Change): List<Range>?
  fun findChange(commitSha: String, filePath: String): Change?
  fun findFileLinesMapper(change: Change): GHPRChangedFileLinesMapperImpl?
}