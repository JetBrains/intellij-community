// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed class ProjectViewPaneRequest

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneLoadChildrenRequest(
  val nodeId: Long,
) : ProjectViewPaneRequest()
