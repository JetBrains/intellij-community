// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
@LcrDslMarker
interface LcrInitParams {

  enum class Align {
    LEFT,

    /**
     * The cell occupies all available free space, so next cells will be near right edge
     */
    FILL,

    /**
     * Similar to [FILL] but additionally aligns the cell to the right
     */
    RIGHT
  }

  /**
   * Default value is [Align.LEFT]
   */
  var align: Align

  var accessibleName: @Nls String?
}