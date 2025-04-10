// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import com.intellij.ui.SimpleTextAttributes
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
 * @see com.intellij.xdebugger.impl.rpc.models.XExecutionStackModel
 */
@ApiStatus.Internal
@Serializable
data class XExecutionStackId(override val uid: UID) : Id

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
 * @see com.intellij.xdebugger.impl.rpc.models.XStackFrameValueIdType
 * @see com.intellij.xdebugger.impl.rpc.models.XStackFrameModel
 */
@ApiStatus.Internal
@Serializable
data class XStackFrameId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XStackFrameDto(
  val stackFrameId: XStackFrameId,
  val sourcePosition: XSourcePositionDto?,
  val equalityObject: XStackFrameEqualityObject?,
  val evaluator: XDebuggerEvaluatorDto,
  val initialPresentation: XStackFramePresentation,
  val captionInfo: XStackFrameCaptionInfo = XStackFrameCaptionInfo.noInfo,
  val customBackgroundInfo: XStackFrameCustomBackgroundInfo? = null,
)

@ApiStatus.Internal
@Serializable
data class XStackFrameCaptionInfo(
  val hasSeparatorAbove: Boolean,
  val caption: @NlsContexts.Separator String?,
) {
  companion object {
    val noInfo: XStackFrameCaptionInfo = XStackFrameCaptionInfo(hasSeparatorAbove = false, caption = null)
  }
}

@ApiStatus.Internal
@Serializable
data class XStackFrameCustomBackgroundInfo(
  val backgroundColor: ColorId? = null,
)

@ApiStatus.Internal
@Serializable
data class XStackFramePresentation(
  val fragments: List<XStackFramePresentationFragment>,
  val iconId: IconId?,
  val tooltipText: @NlsContexts.Label String?,
)

@ApiStatus.Internal
@Serializable
data class XStackFramePresentationFragment(
  val text: String,
  val textAttributes: SerializableSimpleTextAttributes,
)

@ApiStatus.Internal
@Serializable
data class SerializableSimpleTextAttributes(
  val bgColor: ColorId?,
  val fgColor: ColorId?,
  val waveColor: ColorId?,
  val style: Int,
)

@ApiStatus.Internal
fun SimpleTextAttributes.toRpc(): SerializableSimpleTextAttributes =
  SerializableSimpleTextAttributes(bgColor?.rpcId(),
                                   fgColor?.rpcId(),
                                   waveColor?.rpcId(),
                                   style)

@ApiStatus.Internal
fun SerializableSimpleTextAttributes.toSimpleTextAttributes(): SimpleTextAttributes {
  val (bgColor, fgColor, waveColor, style) = this
  return SimpleTextAttributes(bgColor?.color(),
                              fgColor?.color(),
                              waveColor?.color(),
                              style)
}

@ApiStatus.Internal
@Serializable
sealed interface XStackFrameEqualityObject

@ApiStatus.Internal
@Serializable
data class XStackFrameStringEqualityObject(val string: String) : XStackFrameEqualityObject
