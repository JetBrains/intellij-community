// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.components.SegmentedButtonAction
import com.intellij.ui.dsl.builder.components.SegmentedButtonToolbar
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultComboBoxModel

/**
 * Represents segmented button or combobox if screen reader mode
 */
@ApiStatus.Internal
interface SegmentedButton<T> {
  fun rebuild(options: Collection<T>)
}

@ApiStatus.Internal
internal class ComboBoxSegmentedButton<T>(private val comboBox: ComboBox<T>, private val property: GraphProperty<T>) : SegmentedButton<T> {

  override fun rebuild(options: Collection<T>) {
    val model = comboBox.model as DefaultComboBoxModel<T>
    model.removeAllElements()
    model.addAll(options)
    val value = property.get()
    if (value != null && options.contains(value)) {
      comboBox.selectedItem = value
    }
  }
}

@ApiStatus.Internal
internal class SegmentedButtonImpl<T>(options: Collection<T>,
                                      private val property: GraphProperty<T>,
                                      private val renderer: (T) -> String) : SegmentedButton<T> {

  val toolbar: SegmentedButtonToolbar
  private val actionGroup: DefaultActionGroup

  init {
    actionGroup = DefaultActionGroup(options.map { SegmentedButtonAction(it, property, renderer(it)) })
    toolbar = SegmentedButtonToolbar(actionGroup, SpacingConfiguration.createIntelliJSpacingConfiguration())
    toolbar.targetComponent = null // any data context is supported, suppress warning
  }

  override fun rebuild(options: Collection<T>) {
    actionGroup.removeAll()
    actionGroup.addAll(options.map { SegmentedButtonAction(it, property, renderer(it)) })
  }
}
