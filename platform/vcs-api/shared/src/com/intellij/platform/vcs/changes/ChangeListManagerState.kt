// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.changes

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
@Serializable
sealed class ChangeListManagerState {
  abstract val fileHoldersState: FileHoldersState

  @Serializable
  data class Default(override val fileHoldersState: FileHoldersState) : ChangeListManagerState()

  @Serializable
  data class Updating(override val fileHoldersState: FileHoldersState) : ChangeListManagerState()

  @Serializable
  data class Frozen(
    val reason: @Nls(capitalization = Nls.Capitalization.Sentence) String,
    override val fileHoldersState: FileHoldersState,
  ) : ChangeListManagerState()

  @Serializable
  data class FileHoldersState(
    val unversionedInUpdateMode: Boolean,
    val ignoredInUpdateMode: Boolean,
  )
}