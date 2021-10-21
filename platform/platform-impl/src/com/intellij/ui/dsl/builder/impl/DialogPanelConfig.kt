// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.util.SmartList
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.ButtonGroup
import javax.swing.JComponent

@ApiStatus.Internal
internal class DialogPanelConfig {

  var spacing = createIntelliJSpacingConfiguration()
  val context = Context()

  var preferredFocusedComponent: JComponent? = null
  val validateCallbacks = mutableListOf<() -> ValidationInfo?>()
  val componentValidateCallbacks = linkedMapOf<JComponent, () -> ValidationInfo?>()
  val customValidationRequestors = linkedMapOf<JComponent, MutableList<(() -> Unit) -> Unit>>()
  val applyCallbacks = linkedMapOf<JComponent?, MutableList<() -> Unit>>()
  val resetCallbacks = linkedMapOf<JComponent?, MutableList<() -> Unit>>()
  val isModifiedCallbacks = linkedMapOf<JComponent?, MutableList<() -> Boolean>>()

}

fun <T> MutableMap<JComponent?, MutableList<() -> T>>.register(component: JComponent?, callback: () -> T) {
  getOrPut(component) { SmartList() }.add(callback)
}

// https://jetbrains.github.io/ui/controls/input_field/#spacing
@ApiStatus.Experimental
fun createIntelliJSpacingConfiguration(): SpacingConfiguration {
  return object : SpacingConfiguration {

    override val horizontalSmallGap = JBUI.scale(6)
    override val horizontalDefaultGap = JBUI.scale(16)
    override val horizontalColumnsGap = JBUI.scale(60)
    override val horizontalIndent = JBUI.scale(20)
    override val horizontalToggleButtonIndent = JBUI.scale(20)
    override val verticalComponentGap = JBUI.scale(6)
    override val verticalSmallGap = JBUI.scale(8)
    override val verticalMediumGap = JBUI.scale(20)
    override val buttonGroupHeaderBottomGap = JBUI.scale(2)
    override val segmentedButtonVerticalGap = JBUI.scale(3)
    override val segmentedButtonHorizontalGap= JBUI.scale(12)
  }
}

internal class Context {

  private val buttonGroupsStack: MutableList<ButtonGroup> = mutableListOf()

  fun addButtonGroup(buttonGroup: ButtonGroup) {
    buttonGroupsStack.add(buttonGroup)
  }

  fun getButtonGroup(): ButtonGroup? {
    return buttonGroupsStack.lastOrNull()
  }

  fun removeLastButtonGroup() {
    buttonGroupsStack.removeLast()
  }
}
