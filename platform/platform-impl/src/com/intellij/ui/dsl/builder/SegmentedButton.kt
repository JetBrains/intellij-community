// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.validation.CellValidation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Represents segmented button or combobox depending on number of buttons and screen reader mode. Screen reader mode always uses combobox
 *
 * @see Row.segmentedButton
 */
@ApiStatus.NonExtendable
@ApiStatus.Experimental
interface SegmentedButton<T> : CellBase<SegmentedButton<T>> {

  companion object {
    const val DEFAULT_MAX_BUTTONS_COUNT: Int = 6
  }

  @LayoutDslMarker
  interface ItemPresentation {

    var text: @Nls String?

    var toolTipText: @Nls String?

    var icon: Icon?

    var enabled: Boolean
  }

  override fun visible(isVisible: Boolean): SegmentedButton<T>

  override fun enabled(isEnabled: Boolean): SegmentedButton<T>

  override fun align(align: Align): SegmentedButton<T>

  override fun resizableColumn(): SegmentedButton<T>

  override fun gap(rightGap: RightGap): SegmentedButton<T>

  @Deprecated("Use customize(UnscaledGaps) instead")
  @ApiStatus.ScheduledForRemoval
  override fun customize(customGaps: Gaps): SegmentedButton<T>

  override fun customize(customGaps: UnscaledGaps): SegmentedButton<T>

  var items: Collection<T>

  var selectedItem: T?

  /**
   * Updates presentations of provided [items]
   */
  fun update(vararg items: T)

  fun bind(property: ObservableMutableProperty<T>): SegmentedButton<T>

  @ApiStatus.Experimental
  // todo union whenItemSelected methods into onChange
  fun whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit): SegmentedButton<T>

  @ApiStatus.Experimental
  fun whenItemSelectedFromUi(parentDisposable: Disposable? = null, listener: (T) -> Unit): SegmentedButton<T>

  /**
   * Maximum number of buttons in segmented button. The component automatically turned into ComboBox if exceeded.
   * Default value is [DEFAULT_MAX_BUTTONS_COUNT]
   */
  fun maxButtonsCount(value: Int): SegmentedButton<T>

  fun validation(init: CellValidation<SegmentedButton<T>>.(SegmentedButton<T>) -> Unit): SegmentedButton<T>
}
