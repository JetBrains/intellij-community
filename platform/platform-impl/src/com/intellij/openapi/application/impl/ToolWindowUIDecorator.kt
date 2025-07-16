// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.Border

@ApiStatus.Internal
open class ToolWindowUIDecorator() {
  open fun decorateAndReturnHolder(divider: JComponent, child: JComponent, originalBorderBuilder: () -> Border): JComponent? = null

  open fun createCustomToolWindowPaneHolder(): JPanel = JPanel()
}