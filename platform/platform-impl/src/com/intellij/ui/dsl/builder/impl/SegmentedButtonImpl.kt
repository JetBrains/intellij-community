// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.openapi.observable.util.lockOrSkip
import com.intellij.openapi.observable.util.bind
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultComboBoxModel

@ApiStatus.Internal
internal class SegmentedButtonImpl<T>(parent: RowImpl, private val renderer: (T) -> String) :
  PlaceholderBaseImpl<SegmentedButton<T>>(parent), SegmentedButton<T> {

  private var items: Collection<T> = emptyList()
  private var property: ObservableProperty<T>? = null
  private var maxButtonsCount = SegmentedButton.DEFAULT_MAX_BUTTONS_COUNT

  private val comboBox = ComboBox<T>()
  private val segmentedButtonComponent = SegmentedButtonComponent(items, renderer)

  init {
    comboBox.renderer = listCellRenderer { value, _, _ -> text = renderer(value) }
    rebuild()
  }

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): SegmentedButton<T> {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  override fun verticalAlign(verticalAlign: VerticalAlign): SegmentedButton<T> {
    super.verticalAlign(verticalAlign)
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

  override fun customize(customGaps: Gaps): SegmentedButton<T> {
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
    bindSegmentedButtonComponent(property)
    rebuild()
    return this
  }

  override fun maxButtonsCount(value: Int): SegmentedButton<T> {
    maxButtonsCount = value
    rebuild()
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
    val selectedItem = getSelectedItem()
    val model = DefaultComboBoxModel<T>()
    model.addAll(items)
    comboBox.model = model
    if (selectedItem != null && items.contains(selectedItem)) {
      comboBox.selectedItem = selectedItem
    }
  }

  private fun fillSegmentedButtonComponent() {
    val selectedItem = getSelectedItem()
    segmentedButtonComponent.items = items
    if (selectedItem != null && items.contains(selectedItem)) {
      segmentedButtonComponent.selectedItem = selectedItem
    }
  }

  private fun getSelectedItem(): T? {
    val result = property?.get()
    if (result != null) {
      return result
    }

    val c = component
    @Suppress("UNCHECKED_CAST")
    return when (c) {
      comboBox -> comboBox.selectedItem as? T
      segmentedButtonComponent -> segmentedButtonComponent.selectedItem
      else -> null
    }
  }

  private fun bindSegmentedButtonComponent(property: ObservableMutableProperty<T>) {
    val mutex = AtomicBoolean()
    property.afterChange {
      mutex.lockOrSkip {
        segmentedButtonComponent.selectedItem = it
      }
    }
    segmentedButtonComponent.addSelectedItemListener {
      segmentedButtonComponent.selectedItem?.let {
        mutex.lockOrSkip {
          property.set(it)
        }
      }
    }
  }
}
