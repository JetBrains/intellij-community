// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.platform.projectView.frontend.window.PROJECT_VIEW_TOOL_WINDOW_CUSTOMIZER_EP
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowCustomizer
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowServiceImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests that the split Project View calls each [ProjectViewToolWindowCustomizer] when it sets up its tool window.
 */
@TestApplication
internal class ProjectViewToolWindowCustomizerTest {
  private val projectFixture = projectFixture()
  private val project: Project get() = projectFixture.get()

  @Suppress("unused") // the fixture registers the customizer for each test
  private val customizerFixture = extensionPointFixture(PROJECT_VIEW_TOOL_WINDOW_CUSTOMIZER_EP) { TabbedCustomizer() }

  @Test
  fun `a customizer replaces the default content UI type`() = timeoutRunBlocking {
    val toolWindow = RecordingToolWindow(project)

    withContext(Dispatchers.EDT) {
      ProjectViewToolWindowServiceImpl.getInstance(project).setupToolWindow(toolWindow)
    }

    assertEquals(listOf(ToolWindowContentUiType.COMBO, ToolWindowContentUiType.TABBED), toolWindow.defaultContentUiTypes)
  }
}

private class TabbedCustomizer : ProjectViewToolWindowCustomizer {
  override fun customize(toolWindow: ToolWindow) {
    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.TABBED)
  }
}

/**
 * Records each default content UI type that the code sets.
 */
private class RecordingToolWindow(project: Project) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
  val defaultContentUiTypes: MutableList<ToolWindowContentUiType> = mutableListOf()

  override fun setDefaultContentUiType(type: ToolWindowContentUiType) {
    defaultContentUiTypes += type
  }
}
