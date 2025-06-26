// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.ui.content.Content
import com.intellij.xdebugger.ui.XDebugTabLayouter

internal class XCombinedDebugTabLayouter(
  var tabLayouters: MutableList<XDebugTabLayouter>,
  val consoleLayouter: XDebugTabLayouter
) : XDebugTabLayouter() {
  var ui : RunnerLayoutUi? = null

  override fun registerConsoleContent(ui: RunnerLayoutUi, console: ExecutionConsole): Content {
    return consoleLayouter.registerConsoleContent(ui, console)
  }

  override fun registerAdditionalContent(ui: RunnerLayoutUi) {
    this.ui = ui
    tabLayouters.forEach { it.registerAdditionalContent(ui) }
  }

  fun replaceFirstLayouterAndApply(layouter: XDebugTabLayouter) {
    tabLayouters[0] = layouter
    if (ui != null)
      layouter.registerAdditionalContent(ui!!)
  }
}