// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.CollapsiblePanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use overloaded collapsibleGroup(...) instead")
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
internal class CollapsiblePanelImpl(dialogPanelConfig: DialogPanelConfig,
                                    parent: RowImpl,
                                    @NlsContexts.BorderTitle title: String,
                                    init: Panel.() -> Unit) :
  PanelImpl(dialogPanelConfig, parent), CollapsiblePanel {

  private val collapsibleTitledSeparator = CollapsibleTitledSeparator(title)

  override var expanded by collapsibleTitledSeparator::expanded

  init {
    val collapsibleTitledSeparator = this.collapsibleTitledSeparator
    row {
      cell(collapsibleTitledSeparator).horizontalAlign(HorizontalAlign.FILL)
    }
    val expandablePanel = panel {
      init()
    }
    collapsibleTitledSeparator.onAction {
      expandablePanel.visible(it)
    }
  }
}
