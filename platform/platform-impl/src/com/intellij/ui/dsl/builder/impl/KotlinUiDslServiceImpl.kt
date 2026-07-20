// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.KotlinUiDslService
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.gridLayout.GridLayout
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class KotlinUiDslServiceImpl : KotlinUiDslService {

  override fun panel(init: Panel.() -> Unit): DialogPanel {
    val dialogPanelConfig = DialogPanelConfig()
    val panel = PanelImpl(dialogPanelConfig, IntelliJSpacingConfiguration(), null)
    panel.init()
    dialogPanelConfig.context.postInit()

    val layout = GridLayout()
    layout.respectMinimumSize = true
    val result = DialogPanel(layout = layout)
    val builder = PanelBuilder(panel.rows, dialogPanelConfig, panel.spacingConfiguration, result, layout.rootGrid)
    builder.build()
    initPanel(dialogPanelConfig, result)

    if (LoadingState.COMPONENTS_LOADED.isOccurred) {
      Registry.getColor("ui.kotlin.ui.dsl.color", null)?.let {
        result.background = it
      }
    }
    return result
  }

  override fun createPresentation(
    text: @Nls String?,
    toolTipText: @Nls String?,
    icon: Icon?,
    enabled: Boolean,
  ): SegmentedButton.ItemPresentation {
    return ItemPresentationImpl(text, toolTipText, icon, enabled)
  }
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
