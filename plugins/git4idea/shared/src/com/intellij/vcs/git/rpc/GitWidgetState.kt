// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
@Serializable
sealed interface GitWidgetState {
  @Serializable
  data object DoNotShow : GitWidgetState

  @Serializable
  data class NoVcs(val trustedProject: Boolean) : GitWidgetState

  @Serializable
  data object GitRepositoriesNotLoaded : GitWidgetState

  @Serializable
  data class OnRepository(val repository: RepositoryId, val presentationData: RepositoryPresentation) : GitWidgetState

  @Serializable
  data class RepositoryPresentation(
    val icon: IconId?,
    val text: @Nls String,
    val description: @Nls String?,
    val syncStatus: BranchSyncStatus,
  )

  @Serializable
  data class BranchSyncStatus(val incoming: Boolean, val outgoing: Boolean)
}
