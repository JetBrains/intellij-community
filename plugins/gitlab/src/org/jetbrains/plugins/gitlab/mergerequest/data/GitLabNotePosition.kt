// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO

sealed class GitLabNotePosition(
  val parentSha: String,
  val sha: String,
  val filePathBefore: String?,
  val filePathAfter: String?
) {

  class Text(
    parentSha: String,
    sha: String,
    filePathBefore: String?,
    filePathAfter: String?,
    val lineIndexLeft: Int?,
    val lineIndexRight: Int?
  ) : GitLabNotePosition(parentSha, sha, filePathBefore, filePathAfter) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Text) return false
      if (!super.equals(other)) return false

      if (lineIndexLeft != other.lineIndexLeft) return false
      if (lineIndexRight != other.lineIndexRight) return false

      return true
    }

    override fun hashCode(): Int {
      var result = super.hashCode()
      result = 31 * result + (lineIndexLeft ?: 0)
      result = 31 * result + (lineIndexRight ?: 0)
      return result
    }
  }

  class Image(
    parentSha: String,
    sha: String,
    filePathBefore: String?,
    filePathAfter: String?,
  ) : GitLabNotePosition(parentSha, sha, filePathBefore, filePathAfter) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      return super.equals(other)
    }
  }

  companion object {
    private val LOG = logger<GitLabNotePosition>()

    @Suppress("DuplicatedCode")
    fun from(position: GitLabNoteDTO.Position): GitLabNotePosition? {
      if (position.diffRefs.baseSha == null) {
        LOG.debug("Missing merge base in note position: $position")
        return null
      }

      val parentSha = position.diffRefs.baseSha
      val sha = position.diffRefs.headSha

      return when (position.positionType) {
        "text" -> Text(parentSha, sha, position.oldPath, position.newPath, position.oldLine?.dec(), position.newLine?.dec())
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
        "text" -> Text(parentSha, sha, position.oldPath, position.newPath, position.oldLine?.dec(), position.newLine?.dec())
        else -> Image(parentSha, sha, position.oldPath, position.newPath)
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GitLabNotePosition

    if (parentSha != other.parentSha) return false
    if (sha != other.sha) return false
    if (filePathBefore != other.filePathBefore) return false
    return filePathAfter == other.filePathAfter
  }

  override fun hashCode(): Int {
    var result = parentSha.hashCode()
    result = 31 * result + sha.hashCode()
    result = 31 * result + (filePathBefore?.hashCode() ?: 0)
    result = 31 * result + (filePathAfter?.hashCode() ?: 0)
    return result
  }
}

val GitLabNotePosition.filePath: String
  get() = (filePathAfter ?: filePathBefore)!!

fun GitLabNotePosition.getLocation(contextSide: Side = Side.LEFT): DiffLineLocation? {
  if (this !is GitLabNotePosition.Text) return null
  return GitLabNotePositionUtil.getLocation(lineIndexLeft, lineIndexRight, contextSide)
}