/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.layout

// http://www.migcalendar.com/miglayout/mavensite/docs/cheatsheet.pdf

enum class LCFlags {
  /**
   * Puts the layout in a flow-only mode.
   * All components in the flow direction will be put in the same cell and will thus not be aligned with component in other rows/columns.
   * For normal horizontal flow this is the same as to say that all component will be put in the first and only column.
   */
  noGrid,

  /**
   * Puts the layout in vertical flow mode. This means that the next cell is normally below and the next component will be put there instead of to the right. Default is horizontal flow.
   */
  flowY,

  /**
   * Claims all available space in the container for the columns and/or rows.
   * At least one component need to have a "grow" constraint for it to fill the container.
   * The space will be divided equal, though honoring "growPriority".
   * If no columns/rows has "grow" set the grow weight of the components in the rows/columns will migrate to that row/column.
   */
  fill, fillX, fillY,

  lcWrap,

  debug
}

enum class CCFlags {
  /**
   * Wrap to the next line/column **after** the component that this constraint belongs to.
   */
  // use row instead
  //wrap,

  /**
   * Span cells in both x and y.
   */
  // use row instead
  //span, spanX, spanY,

  /**
   * Splits the cell in a number of sub cells. Basically this means that the next count number of components will be put in the same cell, next to each other with default gaps.
   * Only the first component in a cell can set the split, any subsequent "split" keywords in the cell will be ignored.
   * "count" defaults to infinite if not specified, which means that "split" alone will put all subsequent components in the same cell.
   * "skip", "wrap" and "newline" will break out of the split cell. The latter two will move to a new row/column as usual.
   * "skip" will skip out if the splitting and continue in the next cell.
   */
  // use row instead
  //split,

  /**
   * Sets how keen the component should be to grow in relation to other component **in the same cell**. Use `push` in addition if need.
   * The weight (defaults to 100 if not specified) is purely a relative value to other components' weight. Twice the weight will get double the extra space.
   * If this constraint is not set the grow weight is set to 0 and the component will not grow (unless fill is set in the row/column in which case "grow 0" can be used to explicitly make it not grow).
   * Grow weight will only be compared against the weights in the same grow priority group and for the same cell.
   */
  grow, growX, growY,

  /**
   * Makes the row and/or column that the component is residing in grow with `weight`.
   */
  push, pushY, pushX,

  // use right { } instead
  //right,

  /**
   * Skips a number of cells in the flow. This is used to jump over a number of cells before the next free cell is looked for.
   * The skipping is done before this component is put in a cell and thus this cells is affected by it. "count" defaults to 1 if not specified.
   */
  // use row instead
  //skip
}