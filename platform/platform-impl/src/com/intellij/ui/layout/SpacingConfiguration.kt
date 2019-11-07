// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil

interface SpacingConfiguration {
  /**
   * Horizontal space between two components (in terms of layout grid - cells).
   *
   * It is space between associated components (somehow relates to each other) - for example, combobox and button to delete items from combobox.
   * Since in most cases components in cells will be associated, it is not a space between two independent components.
   * Horizontal subgroups of components is not supported yet, that's why there is no property to define such space.
   */
  val horizontalGap: Int

  /**
   * Vertical space between two components (in terms of layout grid - rows).
   */
  val verticalGap: Int

  /**
   * Horizontal gap after label column.
   */
  val labelColumnHorizontalGap: Int

  val largeVerticalGap: Int

  val shortTextWidth: Int
  val maxShortTextWidth: Int

  // row comment top gap or gear icon left gap
  val unitSize: Int

  val commentVerticalTopGap: Int

  val dialogTopBottom: Int
  val dialogLeftRight: Int

  /**
   * The size of one indent level (when not overridden by specific control type, e.g. indent of checkbox comment row
   * is defined by checkbox icon size)
   */
  val indentLevel: Int
}

// https://jetbrains.github.io/ui/controls/input_field/#spacing
fun createIntelliJSpacingConfiguration(): SpacingConfiguration {
  return object : SpacingConfiguration {
    override val horizontalGap = JBUI.scale(8)
    override val verticalGap = JBUI.scale(5 * 2)
    override val labelColumnHorizontalGap = JBUI.scale(6)
    override val largeVerticalGap = JBUI.scale(UIUtil.LARGE_VGAP)

    override val shortTextWidth = JBUI.scale(250)
    override val maxShortTextWidth = JBUI.scale(350)

    override val unitSize = JBUI.scale(4)

    override val dialogTopBottom = JBUI.scale(10)
    override val dialogLeftRight = JBUI.scale(12)

    override val commentVerticalTopGap = JBUI.scale(6)
    override val indentLevel: Int get() = horizontalGap * 3
  }
}

