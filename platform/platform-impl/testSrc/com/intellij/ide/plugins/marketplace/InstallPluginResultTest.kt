// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class InstallPluginResultTest {
  @Test
  fun `installed dependencies exclude the displayed plugin and preserve first occurrence order`() {
    val displayed = plugin("plugin.id", "Plugin")
    val firstDependency = plugin("dependency.first", "First")
    val duplicateDependency = plugin("dependency.first", "Duplicate")
    val secondDependency = plugin("dependency.second", "Second")

    val dependencies = collectInstalledDependencyDescriptors(
      displayed.pluginId,
      listOf(displayed, firstDependency, duplicateDependency, secondDependency),
    )

    assertThat(dependencies.map { it.pluginId.idString })
      .containsExactly("dependency.first", "dependency.second")
    assertThat(dependencies.map { it.name }).containsExactly("First", "Second")
  }

  private fun plugin(id: String, name: String): PluginUiModel {
    return PluginNodeModelBuilderFactory.createBuilder(PluginId.getId(id)).setName(name).build()
  }
}
