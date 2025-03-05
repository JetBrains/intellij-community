// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class DialogPanelConfig {

  val context: Context = Context()

  var preferredFocusedComponent: JComponent? = null

  val applyCallbacks: LinkedHashMap<JComponent?, MutableList<() -> Unit>> = linkedMapOf()
  val resetCallbacks: LinkedHashMap<JComponent?, MutableList<() -> Unit>> = linkedMapOf()
  val isModifiedCallbacks: LinkedHashMap<JComponent?, MutableList<() -> Boolean>> = linkedMapOf()

  val validationRequestors: LinkedHashMap<JComponent, MutableList<DialogValidationRequestor>> = linkedMapOf()
  val validationsOnInput: LinkedHashMap<JComponent, MutableList<DialogValidation>> = linkedMapOf()
  val validationsOnApply: LinkedHashMap<JComponent, MutableList<DialogValidation>> = linkedMapOf()
  var useComboBoxNewRenderer: Boolean = false
}

internal fun <C: JComponent?, T> MutableMap<C, MutableList<T>>.list(component: C): MutableList<T> {
  return getOrPut(component) { SmartList() }
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
