// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.xdebugger.impl.ui.visualizedtext.TextBasedContentTab
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Checks the tabs that [visualizer] makes for a text value.
 * A positive check also creates the component of the tab in the project of [projectFixture].
 */
internal class TextVisualizerChecker(
  private val visualizer: TextValueVisualizer,
  private val projectFixture: TestFixture<Project>,
) {
  suspend fun checkPositive(input: String): VisualizedContentTab {
    val tabs = visualizer.visualize(input)
    assertEquals(1, tabs.size) { tabs.toString() }
    val tab = tabs[0]
    withContext(Dispatchers.EDT) {
      Disposer.newDisposable().use {
        tab.createComponent(projectFixture.get(), it) // at least it should not crash
      }
    }
    return tab
  }

  suspend fun checkPositive(input: String, formatted: String) {
    val tab = checkPositive(input) as TextBasedContentTab
    assertEquals(formatted, tab.formatText())
  }

  fun checkNegative(input: String) {
    val tabs = visualizer.visualize(input)
    assertEquals(0, tabs.size) { tabs.toString() }
  }
}
