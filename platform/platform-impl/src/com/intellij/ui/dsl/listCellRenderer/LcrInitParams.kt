// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
@LcrDslMarker
sealed class LcrInitParams {

  enum class Align {
    LEFT,
    CENTER,
    RIGHT
  }

  /**
   * If specified then the cell occupies all available free space (so next cells will be near right edge) and the content of the cell
   * is placed according to the [align] value
   */
  var align: Align? = null

  var accessibleName: @Nls String? = null
}
