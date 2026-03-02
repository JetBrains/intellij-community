// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.SerializableSimpleTextAttributes
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ThreeState
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface XStackFramesEvent {
  @ApiStatus.Internal
  @Serializable
  data class XNewStackFrames(val frames: List<XStackFrameDto>, val frameToSelectId: XStackFrameId?, val last: Boolean) : XStackFramesEvent

  @Serializable
  data class ErrorOccurred(val errorMessage: @NlsContexts.DialogMessage String) : XStackFramesEvent

  @ApiStatus.Internal
  @Serializable
  data class NewPresentation(val stackFrameId: XStackFrameId, val presentation: XStackFramePresentation) : XStackFramesEvent
}

@ApiStatus.Internal
@Serializable
data class XStackFrameDto(
  val stackFrameId: XStackFrameId,
  val sourcePosition: XSourcePositionDto?,
  val alternativeSourcePosition: XSourcePositionDto?,
  val equalityObject: XStackFrameEqualityObject?,
  val evaluator: XDebuggerEvaluatorDto,
  val textPresentation: XStackFramePresentation,
  val captionInfo: XStackFrameCaptionInfo = XStackFrameCaptionInfo.noInfo,
  val backgroundColor: XStackFrameBackgroundColor? = null,
  val canDrop: ThreeState,
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
data class XStackFrameBackgroundColor(
  val colorId: ColorId? = null,
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
sealed interface XStackFrameEqualityObject

@ApiStatus.Internal
@Serializable
data class XStackFrameStringEqualityObject(val string: String) : XStackFrameEqualityObject
