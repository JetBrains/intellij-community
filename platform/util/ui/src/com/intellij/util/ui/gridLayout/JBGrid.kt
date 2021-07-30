// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.gridLayout

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface JBGrid {

  /**
   * Set of columns that fill available extra space in container
   */
  var resizableColumns: Set<Int>

  /**
   * Set of rows that fill available extra space in container
   */
  var resizableRows: Set<Int>

  /**
   * Distances after columns with correspondent index
   */
  var columnsDistance: List<Int>

  /**
   * Distances after rows with correspondent index
   */
  var rowsDistance: List<Int>
}