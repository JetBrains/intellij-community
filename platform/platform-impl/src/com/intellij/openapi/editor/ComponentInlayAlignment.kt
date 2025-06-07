// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import org.jetbrains.annotations.ApiStatus

/**
 * Alignment for component inlays which let adjust component width to content or viewport size.
 */
@ApiStatus.Experimental
enum class ComponentInlayAlignment {
  /**
   * Use component preferred width, but stretch to editor content width if preferred width less than content width.
   */
  STRETCH_TO_CONTENT_WIDTH,
  /**
   * Fit content width if content width more than minimum component width.
   */
  FIT_CONTENT_WIDTH,
  /**
   * Fit viewport width if viewport width more than minimum component width.
   */
  FIT_VIEWPORT_WIDTH,
  /**
   * Fit viewport X span, ensure inlay component shifted and resized to viewport, if viewport width less than minimum component width then component's minimum width used.
   */
  FIT_VIEWPORT_X_SPAN,
  /**
   * Use component size and align inlay to the offset
   * Please note that this is experimental and can be deleted in the future
   */
  @ApiStatus.Internal
  INLINE_COMPONENT
}
