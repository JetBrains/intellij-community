// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.SelectInContext
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
data class SelectInTargetDescriptor(
  val id: @NonNls String,
  val presentableName: @NlsSafe String,
  val weight: Float,
)

@ApiStatus.Internal
@Serializable
data class SelectInRequest(
  val targetId: @NonNls String,
  val contextDescriptor: SelectInContextDescriptor,
  @Transient val context: SelectInContext? = null,
)

@ApiStatus.Internal
@Serializable
data class SelectInContextDescriptor(
  val fileId: VirtualFileId,
)
