// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

/**
 * Grid representation of [GridLayout] root container or cells sub-grids
 */
interface Grid {

  /**
   * Set of columns that fill available extra space in container
   */
  val resizableColumns: MutableSet<Int>

  /**
   * Set of rows that fill available extra space in container
   */
  val resizableRows: MutableSet<Int>

  /**
   * Gaps around columns. Used only when column is visible
   */
  val columnsGaps: MutableList<HorizontalGaps>

  /**
   * Gaps around rows. Used only when row is visible
   */
  val rowsGaps: MutableList<VerticalGaps>
}
