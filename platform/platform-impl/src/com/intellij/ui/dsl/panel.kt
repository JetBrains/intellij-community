// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.gridLayout.JBGridLayout
import com.intellij.ui.dsl.impl.DialogPanelConfig
import com.intellij.ui.dsl.impl.PanelBuilderImpl
import org.jetbrains.annotations.ApiStatus

/*
todo
remove first/last gaps
*/

@ApiStatus.Experimental
fun panel(init: PanelBuilderBase.() -> Unit): DialogPanel { // Dialog panel content supports only PanelBuilderBase, no CellBuilderBase
  val dialogPanelConfig = DialogPanelConfig()
  val builder = PanelBuilderImpl(dialogPanelConfig)
  builder.init()

  val layout = JBGridLayout()
  val result = DialogPanel(layout = layout)
  builder.build(result, layout.rootGrid)
  initPanel(dialogPanelConfig, result)
  return result
}

private fun initPanel(dialogPanelConfig: DialogPanelConfig, panel: DialogPanel) {
  /* todo
  panel.preferredFocusedComponent = builder.builder.preferredFocusedComponent
  panel.validateCallbacks = builder.builder.validateCallbacks
  panel.componentValidateCallbacks = builder.builder.componentValidateCallbacks
  panel.customValidationRequestors = builder.builder.customValidationRequestors
  */
  panel.applyCallbacks = dialogPanelConfig.applyCallbacks
  panel.resetCallbacks = dialogPanelConfig.resetCallbacks
  panel.isModifiedCallbacks = dialogPanelConfig.isModifiedCallbacks
}
