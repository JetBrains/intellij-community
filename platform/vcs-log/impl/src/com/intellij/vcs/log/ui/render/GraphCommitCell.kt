// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.ui.VcsBookmarkRef
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GraphCommitCell(
  val commitId: CommitId?,
  @NlsSafe val text: String,
  val refsToThisCommit: Collection<VcsRef>,
  val bookmarksToThisCommit: Collection<VcsBookmarkRef>,
  val printElements: Collection<PrintElement>,
  val isLoading: Boolean,
) {
  override fun toString(): String = text
}
