// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusFactory
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ChangeListChange
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
  @Transient private val localValue: ChangeList? = null,
) {
  private var cachedChangeList: ChangeList? = null

  fun getChangeList(project: Project): ChangeList {
    return cachedChangeList ?: computeChangeList(project).also {
      cachedChangeList = it
    }
  }

  private fun computeChangeList(project: Project): ChangeList {
    return localValue ?: LocalChangeListImpl.Builder(project, name).apply {
      if (comment != null) {
        setComment(comment)
      }
      setDefault(isDefault)
      setId(id)
    }.setChanges(changes.map { ChangeListChange(it.change, name, id) }).build()
  }
}

@Serializable
@ApiStatus.Internal
data class ChangeDto(
  private val beforeRevision: ContentRevisionDto?,
  private val afterRevision: ContentRevisionDto?,
  private val fileStatusId: String,
  @Transient private val localValue: Change? = null,
) {
  val change: Change by lazy {
    if (localValue != null) return@lazy localValue

    val fileStatus = PLATFORM_FILE_STATUSES[fileStatusId]
    Change(beforeRevision?.contentRevision, afterRevision?.contentRevision, fileStatus)
  }
}

private val PLATFORM_FILE_STATUSES: Map<String, FileStatus> by lazy {
  FileStatusFactory.getInstance().globalFileStatuses.associateBy { it.id }
}