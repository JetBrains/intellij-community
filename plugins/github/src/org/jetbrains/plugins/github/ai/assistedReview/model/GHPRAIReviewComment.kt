// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview.model

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.flow.MutableStateFlow

data class GHPRAIComment(
  val id: AIComment,
  val position: GHPRAICommentPosition,
  val textHtml: String,
  val reasoningHtml: String,
  val text: String,
  val reasoning: String
) {
  val accepted: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val rejected: MutableStateFlow<Boolean> = MutableStateFlow(false)
}

fun GHPRAIComment.discard() {
  accepted.value = false
  rejected.value = true
}

fun GHPRAIComment.accept() {
  accepted.value = true
  rejected.value = false
}

fun GHPRAICommentPosition.mapToLocation(commitSha: String, diffData: GitTextFilePatchWithHistory, sideBias: Side? = null): DiffLineLocation? {
  val commentData = this

  if (!diffData.contains(commitSha, commentData.path)) return null
  return diffData.mapLine(commitSha, commentData.lineIndex, sideBias ?: Side.RIGHT)
}

data class GHPRAICommentPosition(val path: String, val lineIndex: Int)