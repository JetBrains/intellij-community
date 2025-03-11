// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import fleet.util.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
@Serializable
sealed interface XExecutionStacksEvent {
  @Serializable
  data class NewExecutionStacks(val stacks: List<XExecutionStackDto>, val last: Boolean) : XExecutionStacksEvent

  @Serializable
  data class ErrorOccurred(val errorMessage: @NlsContexts.DialogMessage String) : XExecutionStacksEvent
}

@ApiStatus.Internal
@Serializable
data class XExecutionStackId(val id: UID)

@ApiStatus.Internal
@Serializable
data class XExecutionStackDto(
  val executionStackId: XExecutionStackId,
  val displayName: @Nls String,
  val icon: IconId?
)

@ApiStatus.Internal
@Serializable
data class XNewStackFrames(
  val stacks: List<XStackFrameDto>,
  val last: Boolean // TODO[IJPL-177087] maybe not needed, as we send all via Flow
)

@ApiStatus.Internal
@Serializable
data class XStackFrameId(val id: UID)

@ApiStatus.Internal
@Serializable
data class XStackFrameDto(
  val stackFrameId: XStackFrameId,
)
