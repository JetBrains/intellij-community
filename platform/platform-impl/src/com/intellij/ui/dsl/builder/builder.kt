// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.impl.DialogPanelConfig
import com.intellij.ui.dsl.builder.impl.PanelBuilder
import com.intellij.ui.dsl.builder.impl.PanelImpl
import com.intellij.ui.dsl.gridLayout.GridLayout

@DslMarker
internal annotation class LayoutDslMarker

/**
 * Root panel that provided by [init] does not support [CellBase] methods now. May be added later but seems not needed now
 */
fun panel(init: Panel.() -> Unit): DialogPanel {
  val dialogPanelConfig = DialogPanelConfig()
  val panel = PanelImpl(dialogPanelConfig, IntelliJSpacingConfiguration(), null)
  panel.init()
  dialogPanelConfig.context.postInit()

  val layout = GridLayout()
  val result = DialogPanel(layout = layout)
  val builder = PanelBuilder(panel.rows, dialogPanelConfig, panel.spacingConfiguration, result, layout.rootGrid)
  builder.build()
  initPanel(dialogPanelConfig, result)
  return result
}

private fun initPanel(dialogPanelConfig: DialogPanelConfig, panel: DialogPanel) {
  panel.preferredFocusedComponent = dialogPanelConfig.preferredFocusedComponent

  panel.applyCallbacks = dialogPanelConfig.applyCallbacks
  panel.resetCallbacks = dialogPanelConfig.resetCallbacks
  panel.isModifiedCallbacks = dialogPanelConfig.isModifiedCallbacks

  panel.validationRequestors = dialogPanelConfig.validationRequestors
  panel.validationsOnInput = dialogPanelConfig.validationsOnInput
  panel.validationsOnApply = dialogPanelConfig.validationsOnApply
}
