// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class InstalledPluginsStateTest {
  @Test
  fun `a successful dynamic update clears the uninstall marker`() {
    val plugin = PluginNodeModelBuilderFactory.createBuilder(PluginId.getId("updated.plugin"))
      .setName("Updated Plugin")
      .build()
      .getDescriptor()
    val state = InstalledPluginsState()
    state.onPluginUninstall(plugin, true)

    state.onPluginInstall(plugin, true, false)

    assertThat(state.wasUninstalledWithoutRestart(plugin.pluginId)).isFalse()
    assertThat(state.wasUpdated(plugin.pluginId)).isTrue()
  }
}
