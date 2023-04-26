// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.observable.util.whenItemSelectedFromUi
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.components.NO_TOOLTIP_RENDERER
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.bind
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.whenItemSelected
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.whenItemSelectedFromUi
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.toUnscaled
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.dsl.validation.impl.CompoundCellValidation
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.DefaultComboBoxModel

@ApiStatus.Internal
internal class SegmentedButtonImpl<T>(dialogPanelConfig: DialogPanelConfig,
                                      parent: RowImpl,
                                      private val renderer: (T) -> @Nls String,
                                      tooltipRenderer: (T) -> @Nls String? = NO_TOOLTIP_RENDERER
) : PlaceholderBaseImpl<SegmentedButton<T>>(parent), SegmentedButton<T> {

  private var items: Collection<T> = emptyList()
  private var property: ObservableProperty<T>? = null
  private var maxButtonsCount = SegmentedButton.DEFAULT_MAX_BUTTONS_COUNT

  private val comboBox = ComboBox<T>()
  private val segmentedButtonComponent = SegmentedButtonComponent(items, renderer, tooltipRenderer)

  private val cellValidation = CompoundCellValidation(
    CellValidationImpl(dialogPanelConfig, this, comboBox),
    CellValidationImpl(dialogPanelConfig, this, segmentedButtonComponent))

  override var selectedItem: T?
    get() {
      val result = property?.get()
      if (result != null) {
        return result
      }

      @Suppress("UNCHECKED_CAST")
      return when (component) {
        comboBox -> comboBox.selectedItem as? T
        segmentedButtonComponent -> segmentedButtonComponent.selectedItem
        else -> null
      }
    }

    set(value) {
      when (component) {
        comboBox -> comboBox.selectedItem = value
        segmentedButtonComponent -> segmentedButtonComponent.selectedItem = value
      }
    }

  init {
    comboBox.renderer = listCellRenderer { text = renderer(it) }
    segmentedButtonComponent.isOpaque = false
    rebuild()
  }

  override fun align(align: Align): SegmentedButton<T> {
    super.align(align)
    return this
  }

  override fun resizableColumn(): SegmentedButton<T> {
    super.resizableColumn()
    return this
  }

  override fun gap(rightGap: RightGap): SegmentedButton<T> {
    super.gap(rightGap)
    return this
  }

  override fun enabled(isEnabled: Boolean): SegmentedButton<T> {
    super.enabled(isEnabled)
    return this
  }

  override fun visible(isVisible: Boolean): SegmentedButton<T> {
    super.visible(isVisible)
    return this
  }

  @Deprecated("Use customize(UnscaledGaps) instead")
  override fun customize(customGaps: Gaps): SegmentedButton<T> {
    return customize(customGaps.toUnscaled())
  }

  override fun customize(customGaps: UnscaledGaps): SegmentedButton<T> {
    super.customize(customGaps)
    return this
  }

  override fun items(items: Collection<T>): SegmentedButton<T> {
    this.items = items
    rebuild()
    return this
  }

  override fun bind(property: ObservableMutableProperty<T>): SegmentedButton<T> {
    this.property = property
    comboBox.bind(property)
    segmentedButtonComponent.bind(property)
    rebuild()
    return this
  }

  override fun whenItemSelected(parentDisposable: Disposable?, listener: (T) -> Unit): SegmentedButton<T> {
    comboBox.whenItemSelected(parentDisposable, listener)
    segmentedButtonComponent.whenItemSelected(parentDisposable, listener)
    return this
  }

  override fun whenItemSelectedFromUi(parentDisposable: Disposable?, listener: (T) -> Unit): SegmentedButton<T> {
    comboBox.whenItemSelectedFromUi(parentDisposable, listener)
    segmentedButtonComponent.whenItemSelectedFromUi(parentDisposable, listener)
    return this
  }

  override fun maxButtonsCount(value: Int): SegmentedButton<T> {
    maxButtonsCount = value
    rebuild()
    return this
  }

  override fun validation(init: CellValidation<SegmentedButton<T>>.(SegmentedButton<T>) -> Unit): SegmentedButton<T> {
    cellValidation.init(this)
    return this
  }

  override fun init(panel: DialogPanel, constraints: Constraints, spacing: SpacingConfiguration) {
    super.init(panel, constraints, spacing)
    segmentedButtonComponent.spacing = spacing
  }

  private fun rebuild() {
    if (ScreenReader.isActive() || items.size > maxButtonsCount) {
      fillComboBox()
      component = comboBox
    }
    else {
      fillSegmentedButtonComponent()
      component = segmentedButtonComponent
    }
  }

  private fun fillComboBox() {
    val oldSelectedItem = selectedItem
    val model = DefaultComboBoxModel<T>()
    model.addAll(items)
    comboBox.model = model
    if (oldSelectedItem != null && items.contains(oldSelectedItem)) {
      comboBox.selectedItem = oldSelectedItem
    }
  }

  private fun fillSegmentedButtonComponent() {
    val oldSelectedItem = selectedItem
    segmentedButtonComponent.items = items
    if (oldSelectedItem != null && items.contains(oldSelectedItem)) {
      segmentedButtonComponent.selectedItem = oldSelectedItem
    }
  }
}
