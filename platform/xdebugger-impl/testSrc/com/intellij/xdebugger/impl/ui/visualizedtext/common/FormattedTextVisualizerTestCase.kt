// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.xdebugger.impl.ui.visualizedtext.TextBasedContentTab
import com.intellij.xdebugger.ui.TextValueVisualizer

abstract class FormattedTextVisualizerTestCase(visualizer: TextValueVisualizer) : TextVisualizerTestCase(visualizer) {

  protected fun checkPositive(input: String, formatted: String) {
    val tab = super.checkPositive(input) as TextBasedContentTab
    assertEquals(formatted, tab.formatText())
  }

}