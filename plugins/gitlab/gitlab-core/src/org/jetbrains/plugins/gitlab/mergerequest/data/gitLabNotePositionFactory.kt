// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteRestDTO

private val LOG = logger<GitLabNotePosition>()

/**
 * Reads a [GitLabNotePosition] from the REST payload of a note.
 *
 * The conversion stays in the backend module, because it reads a REST DTO. The position itself is shared, because the
 * review UI needs it on the frontend.
 *
 * @return `null` when the payload has no merge base, which makes the position unusable.
 */
@Suppress("DuplicatedCode")
fun GitLabNoteRestDTO.Position.toNotePosition(): GitLabNotePosition? {
  val parentSha = baseSha
  if (parentSha == null) {
    LOG.debug("Missing merge base in the note position: $this")
    return null
  }

  val sha = headSha

  return when (positionType) {
    "text" -> GitLabNotePosition.Text(parentSha, sha, oldPath, newPath,
                                      oldLine?.dec(), newLine?.dec(),
                                      lineRange?.start?.oldLine?.dec(), lineRange?.start?.newLine?.dec(),
                                      lineRange?.end?.oldLine?.dec(), lineRange?.end?.newLine?.dec()
    )
    else -> GitLabNotePosition.Image(parentSha, sha, oldPath, newPath)
  }
}

/**
 * Reads a [GitLabNotePosition] from the REST payload of a draft note.
 *
 * @return `null` when the payload has no merge base or no head, which makes the position unusable.
 */
@Suppress("DuplicatedCode")
fun GitLabMergeRequestDraftNoteRestDTO.Position.toNotePosition(): GitLabNotePosition? {
  val parentSha = baseSha
  if (parentSha == null) {
    LOG.debug("Missing merge base in the draft note position: $this")
    return null
  }

  val sha = headSha ?: return null

  return when (positionType) {
    "text" -> GitLabNotePosition.Text(parentSha, sha, oldPath, newPath,
                                      oldLine?.dec(), newLine?.dec(),
                                      lineRange?.start?.oldLine?.dec(), lineRange?.start?.newLine?.dec(),
                                      lineRange?.end?.oldLine?.dec(), lineRange?.end?.newLine?.dec()
    )
    else -> GitLabNotePosition.Image(parentSha, sha, oldPath, newPath)
  }
}
