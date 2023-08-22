// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@LcrDslMarker
interface LcrInitParams {

  /**
   * True if the cell fills available extra horizontal space. If several cells use [grow] then extra space is distributed equally
   */
  var grow: Boolean
}