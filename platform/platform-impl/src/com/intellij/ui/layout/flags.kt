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

/**
 * Use [right] to set `align: right`.
 */
enum class CCFlags {
  /**
   * Wrap to the next line/column **after** the component that this constraint belongs to.
   */
  wrap,

  /**
   * Span cells in both x and y.
   */
  span, spanX, spanY,

  split,

  grow, push, pushY, pushX, right, skip
}