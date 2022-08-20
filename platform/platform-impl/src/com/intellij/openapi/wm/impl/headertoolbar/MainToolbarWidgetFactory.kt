// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import org.jetbrains.annotations.ApiStatus

/**
 * Factory for widgets placed in main toolbar.
 * This is root interface which is not supposed to be implemented. Please implement [MainToolbarProjectWidgetFactory] for
 * Application level widgets or [MainToolbarProjectWidgetFactory] for Project level widgets
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface MainToolbarWidgetFactory {

  /**
   * Defines part of toolbar (described in [Position]) when widget should be shown.
   */
  fun getPosition(): Position

  /**
   * List of allowed positions for toolbar widgets
   */
  enum class Position {
    Left, Right, Center
  }
}