// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.DialogPanel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun panel(init: PanelBuilder.() -> Unit): DialogPanel {
  val builder = PanelBuilder()
  builder.init()

  val panel = builder.build()
  initPanel(builder, panel)
  return panel
}

private fun initPanel(builder: PanelBuilder, panel: DialogPanel) {
  /* todo
  panel.preferredFocusedComponent = builder.builder.preferredFocusedComponent
  panel.validateCallbacks = builder.builder.validateCallbacks
  panel.componentValidateCallbacks = builder.builder.componentValidateCallbacks
  panel.customValidationRequestors = builder.builder.customValidationRequestors
  */
  panel.applyCallbacks = builder.applyCallbacks
  panel.resetCallbacks = builder.resetCallbacks
  panel.isModifiedCallbacks = builder.isModifiedCallbacks
}
