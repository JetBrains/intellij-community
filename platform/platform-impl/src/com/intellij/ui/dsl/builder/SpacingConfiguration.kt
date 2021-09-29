// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SpacingConfiguration {

  companion object {
    val EMPTY = object : SpacingConfiguration {
      override val horizontalSmallGap = 0
      override val horizontalDefaultGap = 0
      override val horizontalColumnsGap = 0
      override val horizontalIndent = 0
      override val horizontalToggleButtonIndent = 0
      override val verticalComponentGap = 0
      override val verticalSmallGap = 0
      override val verticalMediumGap = 0
      override val buttonGroupHeaderBottomGap = 0
      override val segmentedButtonVerticalGap = 0
      override val segmentedButtonHorizontalGap = 0
    }
  }

  /**
   * Small horizontal gap, used between label and related component for example
   */
  val horizontalSmallGap: Int

  /**
   * Default horizontal gap between components in one row
   */
  val horizontalDefaultGap: Int

  /**
   * Horizontal gap between two columns of components
   */
  val horizontalColumnsGap: Int

  /**
   * The horizontal left indent of one level
   */
  val horizontalIndent: Int

  /**
   * The horizontal left indent for toggle button comment
   */
  val horizontalToggleButtonIndent: Int

  /**
   * Top and bottom gaps for components like CheckBox, JTextField etc
   */
  val verticalComponentGap: Int

  /**
   * Vertical small gap between unrelated settings
   */
  val verticalSmallGap: Int

  /**
   * Vertical medium gap between unrelated settings, before and after groups
   */
  val verticalMediumGap: Int

  /**
   * Vertical gap after button group header
   */
  val buttonGroupHeaderBottomGap: Int

  /**
   * Vertical gaps between text and button border for segmented buttons
   */
  val segmentedButtonVerticalGap: Int

  /**
   * Horizontal gaps between text and button border for segmented buttons
   */
  val segmentedButtonHorizontalGap: Int

}
