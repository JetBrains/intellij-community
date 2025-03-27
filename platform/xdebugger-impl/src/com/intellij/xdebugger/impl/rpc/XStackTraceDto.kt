// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.Id
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

/**
 * @see XExecutionStackModel
 */
@ApiStatus.Internal
@Serializable
data class XExecutionStackId(override val uid: com.intellij.platform.rpc.UID): Id

@ApiStatus.Internal
@Serializable
data class XExecutionStackDto(
  val executionStackId: XExecutionStackId,
  val displayName: @Nls String,
  val icon: IconId?,
)

@ApiStatus.Internal
@Serializable
sealed interface XStackFramesEvent {
  @ApiStatus.Internal
  @Serializable
  data class XNewStackFrames(val frames: List<XStackFrameDto>, val last: Boolean) : XStackFramesEvent

  @Serializable
  data class ErrorOccurred(val errorMessage: @NlsContexts.DialogMessage String) : XStackFramesEvent
}

/**
 * @see XStackFrameValueIdType
 * @see XStackFrameModel
 */
@ApiStatus.Internal
@Serializable
data class XStackFrameId(override val uid: com.intellij.platform.rpc.UID) : Id

@ApiStatus.Internal
@Serializable
data class XStackFrameDto(
  val stackFrameId: XStackFrameId,
  val sourcePosition: XSourcePositionDto?,
  val equalityObject: XStackFrameEqualityObject?,
  val evaluator: XDebuggerEvaluatorDto,
)

@ApiStatus.Internal
@Serializable
sealed interface XStackFrameEqualityObject

@ApiStatus.Internal
@Serializable
data class XStackFrameStringEqualityObject(val string: String) : XStackFrameEqualityObject
