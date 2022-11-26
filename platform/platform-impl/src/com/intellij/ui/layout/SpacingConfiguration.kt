// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.util.ui.JBUI

@Deprecated("Use Kotlin UI DSL Version 2")
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
  val verticalGap: Int get() = componentVerticalGap * 2
  val componentVerticalGap: Int

  /**
   * Horizontal gap after label column.
   */
  val labelColumnHorizontalGap: Int

  val largeHorizontalGap: Int
  val largeVerticalGap: Int
  val radioGroupTitleVerticalGap: Int

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
@Deprecated("Use Kotlin UI DSL Version 2")
fun createIntelliJSpacingConfiguration(): SpacingConfiguration {
  return object : SpacingConfiguration {
    override val horizontalGap = JBUI.scale(6)
    override val componentVerticalGap = JBUI.scale(6)
    override val labelColumnHorizontalGap = JBUI.scale(6)
    override val largeHorizontalGap = JBUI.scale(16)
    override val largeVerticalGap = JBUI.scale(20)
    override val radioGroupTitleVerticalGap = JBUI.scale(6 + 2)

    override val shortTextWidth = JBUI.scale(250)
    override val maxShortTextWidth = JBUI.scale(350)

    override val unitSize = JBUI.scale(4)

    override val dialogTopBottom = JBUI.scale(10)
    override val dialogLeftRight = JBUI.scale(12)

    override val commentVerticalTopGap = JBUI.scale(6)

    override val indentLevel: Int
      get() = JBUI.scale(20)
  }
}
