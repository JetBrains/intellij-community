// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Events produced in [com.intellij.vcs.changes.viewModel.BackendRemoteCommitChangesViewModel] on the backend
 * to update the actual Changes View state on the frontend.
 *
 * @see [com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi.getBackendChangesViewEvents]
 */
@ApiStatus.Internal
@Serializable
sealed class BackendChangesViewEvent {
  @Serializable
  data class InclusionChanged(val inclusionState: List<InclusionDto>) : BackendChangesViewEvent() {
    override fun toString(): String = "InclusionChanged(items=${inclusionState.size})"
  }

  @Serializable
  data class RefreshRequested(val withDelay: Boolean, val refreshCounter: Int) : BackendChangesViewEvent()

  @Serializable
  data class ToggleCheckboxes(val showCheckboxes: Boolean) : BackendChangesViewEvent()
}