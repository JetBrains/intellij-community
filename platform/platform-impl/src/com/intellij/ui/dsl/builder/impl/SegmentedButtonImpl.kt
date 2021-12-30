// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.GraphProperty
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
import com.intellij.util.lockOrSkip
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultComboBoxModel

@ApiStatus.Internal
internal class SegmentedButtonImpl<T>(parent: RowImpl, private val renderer: (T) -> String) :
  PlaceholderBaseImpl<SegmentedButton<T>>(parent), SegmentedButton<T> {

  private var options: Collection<T> = emptyList()
  private var property: GraphProperty<T>? = null
  private var maxButtonsCount = SegmentedButton.DEFAULT_MAX_BUTTONS_COUNT

  private val comboBox = ComboBox<T>()
  private val segmentedButtonComponent = SegmentedButtonComponent(options, renderer)

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

  override fun options(options: Collection<T>): SegmentedButton<T> {
    this.options = options
    rebuild()
    return this
  }

  override fun bind(property: GraphProperty<T>): SegmentedButton<T> {
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
    if (ScreenReader.isActive() || options.size > maxButtonsCount) {
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
    model.addAll(options)
    comboBox.model = model
    if (selectedItem != null && options.contains(selectedItem)) {
      comboBox.selectedItem = selectedItem
    }
  }

  private fun fillSegmentedButtonComponent() {
    val selectedItem = getSelectedItem()
    segmentedButtonComponent.options = options
    if (selectedItem != null && options.contains(selectedItem)) {
      segmentedButtonComponent.selection = selectedItem
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
      segmentedButtonComponent -> segmentedButtonComponent.selection
      else -> null
    }
  }

  private fun bindSegmentedButtonComponent(property: GraphProperty<T>) {
    val mutex = AtomicBoolean()
    property.afterChange {
      mutex.lockOrSkip {
        segmentedButtonComponent.selection = it
      }
    }
    segmentedButtonComponent.changeListener = {
      segmentedButtonComponent.selection?.let {
        mutex.lockOrSkip {
          property.set(it)
        }
      }
    }
  }
}
