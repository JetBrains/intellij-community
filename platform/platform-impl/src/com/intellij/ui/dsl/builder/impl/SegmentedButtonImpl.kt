// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.observable.util.whenItemSelectedFromUi
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComboBox.SelectableItem
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.SpacingConfiguration
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
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JList

@ApiStatus.Internal
internal class SegmentedButtonImpl<T>(dialogPanelConfig: DialogPanelConfig, parent: RowImpl,
                                      private val renderer: SegmentedButton.ItemPresentation.(T) -> Unit) : PlaceholderBaseImpl<SegmentedButton<T>>(
  parent), SegmentedButton<T> {

  override var items: Collection<T> = emptyList()
    set(value) {
      field = value
      rebuildPresentations()
      rebuildUI()
    }

  private var property: ObservableProperty<T>? = null
  private var maxButtonsCount = SegmentedButton.DEFAULT_MAX_BUTTONS_COUNT

  private val comboBox = ComboBox<T>()
  private val segmentedButtonComponent = SegmentedButtonComponent(this)

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
      if (value != null && presentations[value]?.enabled != true) {
        return
      }

      when (component) {
        comboBox -> comboBox.selectedItem = value
        segmentedButtonComponent -> segmentedButtonComponent.selectedItem = value
      }
    }

  internal val presentations: MutableMap<T, SegmentedButton.ItemPresentation> = mutableMapOf()

  init {
    comboBox.isSwingPopup = false
    comboBox.renderer = object : SimpleListCellRenderer<T>(), SelectableItem {

      private var enabled = true

      override fun customize(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
        if (value == null) {
          return
        }

        val presentation = presentations[value]!!
        text = presentation.text
        toolTipText = presentation.toolTipText
        icon = presentation.icon
        enabled = presentation.enabled

        if (!enabled) {
          foreground = NamedColorUtil.getInactiveTextColor()
          presentation.icon?.let {
            icon = IconLoader.getDisabledIcon(it)
          }
        }
      }

      override fun isSelectable(): Boolean {
        return enabled
      }
    }

    segmentedButtonComponent.isOpaque = false
    rebuildUI()
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
  @ApiStatus.ScheduledForRemoval
  override fun customize(customGaps: Gaps): SegmentedButton<T> {
    return customize(customGaps.toUnscaled())
  }

  override fun customize(customGaps: UnscaledGaps): SegmentedButton<T> {
    super.customize(customGaps)
    return this
  }

  override fun update(vararg items: T) {
    presentations.keys.removeAll(items.toSet())
    rebuildPresentations()

    // Can be improved later if needed
    rebuildUI()
  }

  override fun bind(property: ObservableMutableProperty<T>): SegmentedButton<T> {
    this.property = property
    comboBox.bind(property)
    segmentedButtonComponent.bind(property)
    rebuildUI()
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
    rebuildUI()
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

  private fun rebuildPresentations() {
    val newPresentations = items.map { it to (presentations[it] ?: createPresentation(it)) }.toList()
    presentations.clear()
    presentations.putAll(newPresentations)
  }

  private fun createPresentation(item: T): ItemPresentationImpl {
    val result = ItemPresentationImpl()
    result.renderer(item)

    if (result.text.isNullOrEmpty()) {
      throw UiDslException("Empty text in segmented button presentation is not allowed")
    }

    return result
  }

  private fun rebuildUI() {
    val oldSelectedItem = selectedItem
    val newSelectedItem = if (presentations[oldSelectedItem]?.enabled == true) oldSelectedItem else null

    if (ScreenReader.isActive() || items.size > maxButtonsCount) {
      val model = DefaultComboBoxModel<T>()
      model.addAll(items)
      comboBox.model = model
      component = comboBox

      if (comboBox.selectedItem != newSelectedItem) {
        comboBox.selectedItem = newSelectedItem
      }
    }
    else {
      segmentedButtonComponent.rebuild()
      if (component === segmentedButtonComponent) {
        segmentedButtonComponent.revalidate()
      } else {
        component = segmentedButtonComponent
      }

      if (segmentedButtonComponent.selectedItem != newSelectedItem) {
        segmentedButtonComponent.selectedItem = newSelectedItem
      }
    }
  }
}

private data class ItemPresentationImpl(override var text: @Nls String? = null,
                                        override var toolTipText: @Nls String? = null,
                                        override var icon: Icon? = null,
                                        override var enabled: Boolean = true) : SegmentedButton.ItemPresentation
