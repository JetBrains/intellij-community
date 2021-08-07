// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SpacingConfiguration {

  /**
   * Horizontal gap between unrelated settings in one row
   */
  val horizontalUnrelatedGap: Int

  /**
   * The horizontal left indent of one level
   */
  val horizontalIndent: Int

  /**
   * Top and bottom gaps for components like CheckBox, JTextField etc
   */
  val verticalComponentGap: Int

  /**
   * Vertical gap after comment
   */
  val verticalCommentBottomGap: Int

}
