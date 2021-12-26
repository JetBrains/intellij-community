// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.components.SegmentedButtonAction
import com.intellij.ui.dsl.builder.components.SegmentedButtonToolbar
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultComboBoxModel

@ApiStatus.Internal
internal class SegmentedButtonImpl<T>(parent: RowImpl, private val renderer: (T) -> String) :
  PlaceholderBaseImpl<SegmentedButton<T>>(parent), SegmentedButton<T> {

  private var options: Collection<T> = emptyList()
  private var property: GraphProperty<T>? = null
  private var maxButtonsCount = SegmentedButton.DEFAULT_MAX_BUTTONS_COUNT

  /**
   * Keep instance to avoid [property] listeners creations after every [buildComboBox]
   */
  private var comboBox: ComboBox<T>? = null

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

  override fun options(options: Collection<T>): SegmentedButton<T> {
    this.options = options
    rebuild()
    return this
  }

  override fun bind(property: GraphProperty<T>): SegmentedButton<T> {
    this.property = property
    comboBox?.bind(property)
    rebuild()
    return this
  }

  override fun maxButtonsCount(value: Int): SegmentedButton<T> {
    maxButtonsCount = value
    rebuild()
    return this
  }

  fun rebuild(forceCreation: Boolean = false) {
    if (component == null && !forceCreation) {
      return
    }

    if (ScreenReader.isActive() || options.size > maxButtonsCount) {
      buildComboBox()
    }
    else {
      buildSegmentedButtonToolbar()
    }
  }

  private fun buildComboBox() {
    val selectedItem = getSelectedItem()
    var result = comboBox
    if (result == null) {
      result = ComboBox<T>()
      result.renderer = listCellRenderer { value, _, _ -> text = renderer(value) }
      property?.let { result.bind(it) }
      comboBox = result
    }

    val model = DefaultComboBoxModel<T>()
    model.addAll(options)
    result.model = model
    if (selectedItem != null && options.contains(selectedItem)) {
      result.selectedItem = selectedItem
    }
    component = result
  }

  private fun buildSegmentedButtonToolbar() {
    val actionGroup: DefaultActionGroup
    if (options.isEmpty()) {
      actionGroup = DefaultActionGroup()
    }
    else {
      val propertyArg = property ?: PropertyGraph().graphProperty { options.first() }
      actionGroup = DefaultActionGroup(options.map { SegmentedButtonAction(it, propertyArg, renderer(it)) })
    }

    val toolbar = SegmentedButtonToolbar(actionGroup, SpacingConfiguration.createIntelliJSpacingConfiguration())
    toolbar.targetComponent = null // any data context is supported, suppress warning
    component = toolbar
  }

  private fun getSelectedItem(): T? {
    val result = property?.get()
    if (result != null) {
      return result
    }

    val c = component
    @Suppress("UNCHECKED_CAST")
    return when (c) {
      is ComboBox<*> -> c.selectedItem as? T
      is SegmentedButtonToolbar -> c.getSelectedOption() as? T
      else -> null
    }
  }
}
