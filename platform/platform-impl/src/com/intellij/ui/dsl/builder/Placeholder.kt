// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Used as a reserved cell in layout. Can be populated by content later via [component] property or reset to null
 *
 * @see Row.placeholder
 */
@ApiStatus.NonExtendable
@ApiStatus.Experimental
interface Placeholder : CellBase<Placeholder> {

  override fun visible(isVisible: Boolean): Placeholder

  override fun enabled(isEnabled: Boolean): Placeholder

  @Deprecated("Use align method instead")
  @ApiStatus.ScheduledForRemoval
  override fun verticalAlign(verticalAlign: VerticalAlign): Placeholder

  override fun align(align: Align): Placeholder

  override fun resizableColumn(): Placeholder

  override fun gap(rightGap: RightGap): Placeholder

  @Deprecated("Use customize(UnscaledGaps) instead")
  override fun customize(customGaps: Gaps): Placeholder

  override fun customize(customGaps: UnscaledGaps): Placeholder

  /**
   * Component placed in the cell. If the component is [DialogPanel] then all functionality related to
   * [DialogPanel.apply]/[DialogPanel.reset]/[DialogPanel.isModified] and validation mechanism is delegated from [component]
   * to parent [DialogPanel] that contains this placeholder.
   *
   * The property is not reset automatically when the component is removed from the panel by other methods like [java.awt.Container.remove]
   * or by adding the component to another parent
   */
  var component: JComponent?
}
