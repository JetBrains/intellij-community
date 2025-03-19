// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab

abstract class TextVisualizerTestCase(private val visualizer: TextValueVisualizer) : LightPlatformTestCase() {

  protected open fun checkPositive(input: String): VisualizedContentTab {
    val tabs = visualizer.visualize(input)
    assertSize(1, tabs)
    val tab = tabs[0]
    Disposer.newDisposable().use {
      tab.createComponent(project, it) // at least it should not crash
    }
    return tab
  }

  protected fun checkNegative(input: String) {
    val tabs = visualizer.visualize(input)
    assertSize(0, tabs)
  }

}