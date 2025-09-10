// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.changes.ChangeListManagerState
import com.intellij.platform.vcs.changes.ChangeListManagerState.FileHoldersState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
interface ChangesListManagerStateProvider {
  val state: StateFlow<ChangeListManagerState>

  companion object {
    fun getInstance(project: Project): ChangesListManagerStateProvider = project.service()
  }
}

internal class ChangesListManagerStateProviderImpl() : ChangesListManagerStateProvider {
  override val state: MutableStateFlow<ChangeListManagerState> =
    MutableStateFlow(ChangeListManagerState.Default(createEmptyFileHoldersState()))

  fun setFreezeReason(@Nls reason: String?) = state.update {
    calculateState(reason, it is ChangeListManagerState.Updating, it.fileHoldersState)
  }

  fun setInUpdateMode(value: Boolean) = state.update {
    calculateState((it as? ChangeListManagerState.Frozen)?.reason, value, it.fileHoldersState)
  }

  fun setFileHolderState(value: FileHoldersState) = state.update {
    calculateState((it as? ChangeListManagerState.Frozen)?.reason, it is ChangeListManagerState.Updating, value)
  }

  private fun calculateState(freezeReason: @Nls String?, updateMode: Boolean, fileHoldersState: FileHoldersState): ChangeListManagerState =
    when {
      freezeReason != null -> ChangeListManagerState.Frozen(freezeReason, fileHoldersState)
      updateMode -> ChangeListManagerState.Updating(fileHoldersState)
      else -> ChangeListManagerState.Default(fileHoldersState)
    }

  private fun createEmptyFileHoldersState(): FileHoldersState = FileHoldersState(false, false)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangesListManagerStateProviderImpl = ChangesListManagerStateProvider.getInstance(project) as ChangesListManagerStateProviderImpl
  }
}