// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
internal interface SpacingConfiguration {
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
