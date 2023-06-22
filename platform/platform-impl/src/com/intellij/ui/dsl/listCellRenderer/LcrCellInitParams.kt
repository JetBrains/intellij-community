// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.ui.SimpleColoredComponent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface LcrCellInitParams : LcrInitParams {

  /**
   * True if horizontal insets should be removed from the component to avoid unexpected spacing
   * (for example [SimpleColoredComponent] contains some paddings around by default)
   *
   * Default value is true
   */
  var stripHorizontalInsets: Boolean

  /**
   * Default value is false
   */
  var opaque: Boolean
}
