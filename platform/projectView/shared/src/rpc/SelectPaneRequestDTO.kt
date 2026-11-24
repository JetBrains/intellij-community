// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.rpc

import com.intellij.platform.projectView.pane.ProjectViewPaneId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * A request of the backend to select the pane [paneId] on the frontend.
 */
@ApiStatus.Internal
@Serializable
data class SelectPaneRequestDTO(
  val requestId: Long,
  val paneId: ProjectViewPaneId,
)
