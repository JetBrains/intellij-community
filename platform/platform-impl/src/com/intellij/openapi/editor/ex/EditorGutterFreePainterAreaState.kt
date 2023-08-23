// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus

/**
 * Defines the condition for gutter free painter area painting
 */
@ApiStatus.Experimental
@ApiStatus.Internal
enum class EditorGutterFreePainterAreaState {
  SHOW, HIDE, ON_DEMAND
}