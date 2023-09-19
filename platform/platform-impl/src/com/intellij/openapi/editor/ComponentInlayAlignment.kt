// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class ComponentInlayAlignment {
  STRETCH_TO_CONTENT_WIDTH, // use component preferred width, but stretch to editor content width if preferred width less than content width
  FIT_CONTENT_WIDTH,        // fit content width if content width more than minimum component width
  FIT_VIEWPORT_WIDTH,       // fit viewport width if viewport width more than minimum component width
  FIT_VIEWPORT_X_SPAN       // fit viewport X span, ensure inlay component shifted and resized to viewport, if viewport width less than minimum component width then component's minimum width used
}
