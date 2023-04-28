// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO

sealed interface GitLabNotePosition {
  val parentSha: String
  val sha: String
  val filePathBefore: String?
  val filePathAfter: String?

  data class Text(
    override val parentSha: String,
    override val sha: String,
    override val filePathBefore: String?,
    override val filePathAfter: String?,
    val location: DiffLineLocation
  ) : GitLabNotePosition

  data class Image(
    override val parentSha: String,
    override val sha: String,
    override val filePathBefore: String?,
    override val filePathAfter: String?,
  ) : GitLabNotePosition

  companion object {
    private val LOG = logger<GitLabNotePosition>()

    @Suppress("DuplicatedCode")
    fun from(position: GitLabNoteDTO.Position): GitLabNotePosition? {
      if (position.diffRefs.baseSha == null) {
        LOG.warn("Missing merge base in note position: $position")
        return null
      }

      val parentSha = position.diffRefs.baseSha
      val sha = position.diffRefs.headSha

      return when (position.positionType) {
        "text" -> {
          val location = if (position.oldLine != null) {
            DiffLineLocation(Side.LEFT, position.oldLine - 1)
          }
          else {
            DiffLineLocation(Side.RIGHT, position.newLine!! - 1)
          }
          Text(parentSha, sha, position.oldPath, position.newPath, location)
        }
        else -> Image(parentSha, sha, position.oldPath, position.newPath)
      }
    }

    @Suppress("DuplicatedCode")
    fun from(position: GitLabMergeRequestDraftNoteRestDTO.Position): GitLabNotePosition? {
      if (position.baseSha == null) {
        LOG.warn("Missing merge base in note position: $position")
        return null
      }

      val parentSha = position.baseSha
      val sha = position.headSha ?: return null

      return when (position.positionType) {
        "text" -> {
          val location = if (position.oldLine != null) {
            DiffLineLocation(Side.LEFT, position.oldLine - 1)
          }
          else {
            DiffLineLocation(Side.RIGHT, position.newLine!! - 1)
          }
          Text(parentSha, sha, position.oldPath, position.newPath, location)
        }
        else -> Image(parentSha, sha, position.oldPath, position.newPath)
      }
    }
  }
}

val GitLabNotePosition.filePath: String
  get() = (filePathAfter ?: filePathBefore)!!