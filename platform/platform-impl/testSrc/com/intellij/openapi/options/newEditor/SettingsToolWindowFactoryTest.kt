// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.toolWindow.DefaultToolWindowLayoutBuilderImpl
import com.intellij.util.asDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class SettingsToolWindowFactoryTest {
  @Test
  fun `a project that opens with the registry key off gets no Settings tool window`(): Unit = timeoutRunBlocking {
    assertThat(SettingsToolWindowFactory().isApplicableAsync(ProjectManager.getInstance().defaultProject)).isFalse()
  }

  @Test
  fun `a project that opens with the registry key on gets the Settings tool window`(): Unit = timeoutRunBlocking {
    Registry.get(SETTINGS_TOOL_WINDOW_REGISTRY_KEY).setValue(true, asDisposable())

    assertThat(SettingsToolWindowFactory().isApplicableAsync(ProjectManager.getInstance().defaultProject)).isTrue()
  }

  @Test
  fun `the default layout has no Settings entry while the registry key is off`() {
    val builder = DefaultToolWindowLayoutBuilderImpl()

    SettingsToolWindowLayoutExtension().buildV2Layout(builder)

    assertThat(builder.build()).isEmpty()
  }

  @Test
  fun `the default layout seeds the Settings entry while the registry key is on`(): Unit = timeoutRunBlocking {
    Registry.get(SETTINGS_TOOL_WINDOW_REGISTRY_KEY).setValue(true, asDisposable())
    val builder = DefaultToolWindowLayoutBuilderImpl()

    SettingsToolWindowLayoutExtension().buildV2Layout(builder)

    assertThat(builder.seededIntoExistingLayoutIds()).containsExactly(SETTINGS_TOOL_WINDOW_ID)
  }
}
