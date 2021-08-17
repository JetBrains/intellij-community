// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SpacingConfiguration {

  /**
   * Small horizontal gap, used between label and related component for example
   */
  val horizontalSmallGap: Int

  /**
   * Default horizontal gap between components in one row
   */
  val horizontalDefaultGap: Int

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
   * Vertical gap after comment
   */
  val verticalCommentBottomGap: Int

  /**
   * Vertical gap before group
   */
  val verticalGroupTopGap: Int

  /**
   * Vertical small gap between unrelated settings
   */
  val verticalSmallTopGap: Int

}
