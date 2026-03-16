// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteRestDTO

sealed interface GitLabNotePosition {
  val parentSha: String
  val sha: String
  val filePathBefore: String?
  val filePathAfter: String?

  interface WithLine : GitLabNotePosition {
    val lineIndexLeft: Int?
    val lineIndexRight: Int?
    val startLineIndexLeft: Int?
    val startLineIndexRight: Int?
    val endLineIndexLeft: Int?
    val endLineIndexRight: Int?
  }

  data class Text(
    override val parentSha: String,
    override val sha: String,
    override val filePathBefore: String?,
    override val filePathAfter: String?,
    override val lineIndexLeft: Int?,
    override val lineIndexRight: Int?,
    override val startLineIndexLeft: Int?,
    override val startLineIndexRight: Int?,
    override val endLineIndexLeft: Int?,
    override val endLineIndexRight: Int?,
  ) : WithLine

  data class Image(
    override val parentSha: String,
    override val sha: String,
    override val filePathBefore: String?,
    override val filePathAfter: String?,
  ) : GitLabNotePosition

  companion object {
    private val LOG = logger<GitLabNotePosition>()

    @Suppress("DuplicatedCode")
    fun from(position: GitLabNoteRestDTO.Position): GitLabNotePosition? {
      if (position.baseSha == null) {
        LOG.debug("Missing merge base in note position: $position")
        return null
      }

      val parentSha = position.baseSha
      val sha = position.headSha

      return when (position.positionType) {
        "text" -> Text(parentSha, sha, position.oldPath, position.newPath,
                       position.oldLine?.dec(), position.newLine?.dec(),
                       position.lineRange?.start?.oldLine?.dec(), position.lineRange?.start?.newLine?.dec(),
                       position.lineRange?.end?.oldLine?.dec(), position.lineRange?.end?.newLine?.dec()
        )
        else -> Image(parentSha, sha, position.oldPath, position.newPath)
      }
    }

    @Suppress("DuplicatedCode")
    fun from(position: GitLabMergeRequestDraftNoteRestDTO.Position): GitLabNotePosition? {
      if (position.baseSha == null) {
        LOG.debug("Missing merge base in note position: $position")
        return null
      }

      val parentSha = position.baseSha
      val sha = position.headSha ?: return null

      return when (position.positionType) {
        "text" -> Text(parentSha, sha, position.oldPath, position.newPath,
                       position.oldLine?.dec(), position.newLine?.dec(),
                       position.lineRange?.start?.oldLine?.dec(), position.lineRange?.start?.newLine?.dec(),
                       position.lineRange?.end?.oldLine?.dec(), position.lineRange?.end?.newLine?.dec()
        )
        else -> Image(parentSha, sha, position.oldPath, position.newPath)
      }
    }
  }
}

val GitLabNotePosition.filePath: String
  get() = (filePathAfter ?: filePathBefore)!!

fun GitLabNotePosition.getLocation(contextSide: Side = Side.LEFT): DiffLineRange? {
  val position = this
  if (position !is GitLabNotePosition.WithLine) return null
  return GitLabNotePositionUtil.getLocation(position, contextSide)
}