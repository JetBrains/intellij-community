// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime.impl

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.runtime.ErrorState
import com.intellij.platform.syntax.util.runtime.Frame
import com.intellij.platform.syntax.util.runtime.Modifiers
import org.jetbrains.annotations.NonNls

internal class FrameImpl : Frame {
  override var parentFrame: FrameImpl? = null
    private set

  override var elementType: SyntaxElementType? = null // Left public because this field is modified extensively in SqlParser

  override var offset: Int = 0
    private set

  override var position: Int = 0
    private set

  override var level: Int = 0
    private set

  override var modifiers: Modifiers = Modifiers._NONE_ // Left public because this field can be modified in many different ways

  override var name: @NonNls String? = null
    private set

  override var variantCount: Int = 0
    internal set

  override var errorReportedAt: Int = 0
    internal set

  override var lastVariantAt: Int = 0
    internal set

  override var leftMarker: SyntaxTreeBuilder.Marker? = null
    internal set

  fun init(
    builder: SyntaxTreeBuilder,
    state: ErrorState,
    level_: Int,
    modifiers_: Modifiers,
    elementType_: SyntaxElementType?,
    name_: String?,
  ): FrameImpl {
    parentFrame = (state as ErrorStateImpl).currentFrame
    elementType = elementType_

    offset = builder.currentOffset
    position = builder.rawTokenIndex()
    level = level_
    modifiers = modifiers_
    name = name_
    variantCount = state.variants.size
    errorReportedAt = -1
    lastVariantAt = -1

    leftMarker = null
    return this
  }

  override fun resetErrorReportedAt() {
    errorReportedAt = -1
  }

  override fun toString(): @NonNls String {
    val mod = if (modifiers == Modifiers._NONE_) "_NONE_, "
    else ((if ((modifiers and Modifiers._COLLAPSE_) != Modifiers._NONE_) "_CAN_COLLAPSE_, " else "") +
          (if ((modifiers and Modifiers._LEFT_) != Modifiers._NONE_) "_LEFT_, " else "") +
          (if ((modifiers and Modifiers._LEFT_INNER_) != Modifiers._NONE_) "_LEFT_INNER_, " else "") +
          (if ((modifiers and Modifiers._AND_) != Modifiers._NONE_) "_AND_, " else "") +
          (if ((modifiers and Modifiers._NOT_) != Modifiers._NONE_) "_NOT_, " else "") +
          (if ((modifiers and Modifiers._UPPER_) != Modifiers._NONE_) "_UPPER_, " else ""))
    return "{$offset:$position:$level, $errorReportedAt, $mod$elementType, $name}"
  }
}