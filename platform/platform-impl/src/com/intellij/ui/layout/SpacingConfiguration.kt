// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil

// https://jetbrains.github.io/ui/controls/input_field/#spacing
fun createIntelliJSpacingConfiguration(): SpacingConfiguration {
  return object : SpacingConfiguration {
    override val isCompensateVisualPaddings = !SystemInfoRt.isLinux

    override val horizontalGap = JBUI.scale(8)
    override val verticalGap = JBUI.scale(5 * 2)
    override val labelColumnHorizontalGap = JBUI.scale(6)
    override val largeVerticalGap = JBUI.scale(UIUtil.LARGE_VGAP)

    override val shortTextWidth = JBUI.scale(250)
    override val maxShortTextWidth = JBUI.scale(350)

    override val unitSize = JBUI.scale(4)

    override val dialogTopBottom = JBUI.scale(8)
    override val dialogLeftRight = JBUI.scale(12)

    override val commentVerticalTopGap = JBUI.scale(6)
  }
}

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

  /**
   * On macOS input fields (text fields, checkboxes, buttons and so on) have focus ring that drawn outside of component border.
   * If reported component dimensions will be equals to visible (when unfocused) component dimensions, focus ring will be clipped.
   *
   * Since LaF cannot control component environment (host component), default safe strategy is to report component dimensions including focus ring.
   * But it leads to an issue - spacing specified for visible component borders, not to compensated. For example, if horizontal space must be 8px,
   * this 8px must be between one visible border of component to another visible border (in the case of macOS Light theme, gray 1px borders).
   * Exactly 8px.
   *
   * So, advanced layout engine, e.g. MigLayout, offers a way to compensate visual padding on the layout container level, not on component level, as a solution.
   */
  val isCompensateVisualPaddings: Boolean

  val shortTextWidth: Int
  val maxShortTextWidth: Int

  // row comment top gap or gear icon left gap
  val unitSize: Int

  val commentVerticalTopGap: Int

  val dialogTopBottom: Int
  val dialogLeftRight: Int
}