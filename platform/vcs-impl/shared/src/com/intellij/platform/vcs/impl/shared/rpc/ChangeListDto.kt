// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusFactory
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@Serializable
@ApiStatus.Internal
data class ChangeListDto(
  private val name: @Nls String,
  private val comment: @NlsSafe String?,
  private val id: @NonNls String,
  private val isDefault: Boolean,
  private val changes: List<ChangeDto>,
  @Transient private var localValue: LocalChangeList? = null,
) {
  fun getChangeList(project: Project): LocalChangeList {
    return localValue ?: computeChangeList(project).also {
      localValue = it
    }
  }

  private fun computeChangeList(project: Project): LocalChangeList {
    return LocalChangeListImpl.Builder(project, name).apply {
      if (comment != null) {
        setComment(comment)
      }
      setDefault(isDefault)
      setId(id)
    }.setChanges(changes.map { it.toChange(name, id) }).build()
  }
}

/**
 * A serialized [Change] belonging to a [ChangeListDto].
 *
 * The concrete subtype encodes how the backend represents the change, which is what [toChange] reconstructs.
 * This drives [ChangeId.getId] to produce the same value on both frontend and backend so that commit inclusion
 * round-trips correctly (see IJPL-246371):
 * - [ChangelistChangeDto] for VCSes supporting partial changelists (e.g. Git), wrapped into a [ChangeListChange];
 * - [PlainChangeDto] for the others (e.g. Mercurial, SVN), kept as a plain [Change].
 */
@Serializable
@ApiStatus.Internal
sealed class ChangeDto {
  protected abstract val beforeRevision: ContentRevisionDto?
  protected abstract val afterRevision: ContentRevisionDto?
  protected abstract val fileStatusId: String
  protected abstract val localValue: Change?

  /** The plain [Change] carried by this DTO, without any change list wrapping. */
  val change: Change by lazy {
    localValue ?: Change(beforeRevision?.contentRevision, afterRevision?.contentRevision, PLATFORM_FILE_STATUSES[fileStatusId])
  }

  /** Reconstructs the change the way the backend represents it inside the change list [listName]/[listId]. */
  abstract fun toChange(listName: @Nls String, listId: @NonNls String): Change

  companion object {
    fun toDto(change: Change): ChangeDto {
      val beforeRevision = change.beforeRevision?.toDto()
      val afterRevision = change.afterRevision?.toDto()
      val fileStatusId = change.fileStatus.id
      return if (change is ChangeListChange) {
        ChangelistChangeDto(beforeRevision, afterRevision, fileStatusId, localValue = change)
      }
      else {
        PlainChangeDto(beforeRevision, afterRevision, fileStatusId, localValue = change)
      }
    }

    private fun ContentRevision.toDto() = ContentRevisionDto(
      revisionString = revisionNumber.asString(),
      filePath = FilePathDto.toDto(file),
      localValue = this,
    )
  }
}

/** A change from a VCS without partial changelist support; reconstructed as a plain [Change]. */
@Serializable
@ApiStatus.Internal
data class PlainChangeDto(
  override val beforeRevision: ContentRevisionDto?,
  override val afterRevision: ContentRevisionDto?,
  override val fileStatusId: String,
  @Transient override val localValue: Change? = null,
) : ChangeDto() {
  override fun toChange(listName: String, listId: String): Change = change
}

/** A change from a VCS with partial changelist support; reconstructed as a [ChangeListChange]. */
@Serializable
@ApiStatus.Internal
data class ChangelistChangeDto(
  override val beforeRevision: ContentRevisionDto?,
  override val afterRevision: ContentRevisionDto?,
  override val fileStatusId: String,
  @Transient override val localValue: ChangeListChange? = null,
) : ChangeDto() {
  override fun toChange(listName: String, listId: String): ChangeListChange = ChangeListChange(change, listName, listId)
}

private val PLATFORM_FILE_STATUSES: Map<String, FileStatus> by lazy {
  FileStatusFactory.getInstance().globalFileStatuses.associateBy { it.id }
}