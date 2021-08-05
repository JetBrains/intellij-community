// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.util.ui.JBUI
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
}

// https://jetbrains.github.io/ui/controls/input_field/#spacing
@ApiStatus.Experimental
fun createIntelliJSpacingConfiguration(): SpacingConfiguration {
  return object : SpacingConfiguration {

    override val horizontalUnrelatedGap = JBUI.scale(16)
    override val horizontalIndent = JBUI.scale(20)
  }
}
