// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.xdebugger.ui.TextValueVisualizer
import javax.swing.JComponent

abstract class TextVisualizerTestCase(private val visualizer: TextValueVisualizer) : LightPlatformTestCase() {

  protected open fun checkPositive(input: String): JComponent {
    val tabs = visualizer.visualize(input)
    assertSize(1, tabs)
    return tabs[0].createComponent(project) // at least it should not crash
  }

  protected fun checkNegative(input: String) {
    val tabs = visualizer.visualize(input)
    assertSize(0, tabs)
  }

}