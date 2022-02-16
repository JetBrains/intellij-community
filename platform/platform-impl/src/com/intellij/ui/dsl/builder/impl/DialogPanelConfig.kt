// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class DialogPanelConfig {

  var spacing = SpacingConfiguration.createIntelliJSpacingConfiguration()
  val context = Context()

  var preferredFocusedComponent: JComponent? = null

  val applyCallbacks = linkedMapOf<JComponent?, MutableList<() -> Unit>>()
  val resetCallbacks = linkedMapOf<JComponent?, MutableList<() -> Unit>>()
  val isModifiedCallbacks = linkedMapOf<JComponent?, MutableList<() -> Boolean>>()

  val validationRequestors = linkedMapOf<JComponent, MutableList<DialogValidationRequestor>>()
  val validationsOnInput = linkedMapOf<JComponent, MutableList<DialogValidation>>()
  val validationsOnApply = linkedMapOf<JComponent, MutableList<DialogValidation>>()
}

fun <T> MutableMap<JComponent?, MutableList<() -> T>>.register(component: JComponent?, callback: () -> T) {
  getOrPut(component) { SmartList() }.add(callback)
}

internal class Context {

  private val allButtonsGroups = mutableListOf<ButtonsGroupImpl>()
  private val buttonsGroupsStack = mutableListOf<ButtonsGroupImpl>()

  fun addButtonsGroup(buttonsGroup: ButtonsGroupImpl) {
    allButtonsGroups += buttonsGroup
    buttonsGroupsStack += buttonsGroup
  }

  fun getButtonsGroup(): ButtonsGroupImpl? {
    return buttonsGroupsStack.lastOrNull()
  }

  fun removeLastButtonsGroup() {
    buttonsGroupsStack.removeLast()
  }

  fun postInit() {
    for (buttonsGroup in allButtonsGroups) {
      buttonsGroup.postInit()
    }
  }
}
