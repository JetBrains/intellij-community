// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.platform.vcs.changes.ChangesUtil
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent

/**
 * @see [com.intellij.vcs.changes.ChangesViewChangeIdProvider]
 */
@Serializable
@ApiStatus.Internal
sealed class ChangeId {
  /**
   * Path returned by [ChangesUtil.getFilePath]
   */
  abstract val filePath: @SystemIndependent String

  companion object {
    fun getId(change: Change): ChangeId {
      val filePath = ChangesUtil.getFilePath(change).path
      if (change is ChangeListChange) {
        return ChangeListChangeId(change.changeListId, filePath)
      }

      return NonChangeListChangeId(
        filePath = filePath,
        beforeRevision = change.beforeRevision?.revisionNumber?.asString(),
        afterRevision = change.afterRevision?.revisionNumber?.asString(),
      )
    }
  }
}

@Serializable
private data class NonChangeListChangeId(
  override val filePath: @SystemIndependent String,
  val beforeRevision: String?,
  val afterRevision: String?,
) : ChangeId()

@Serializable
private data class ChangeListChangeId(
  val changeListId: @NonNls String,
  override val filePath: @SystemIndependent String,
) : ChangeId()