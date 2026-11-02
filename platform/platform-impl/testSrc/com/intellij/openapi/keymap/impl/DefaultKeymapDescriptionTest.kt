// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class DefaultKeymapDescriptionTest {
  @Test
  fun `every keymap of the platform has a description`() {
    val defaultKeymap = DefaultKeymap.getInstance()
    val platformKeymaps = defaultKeymap.keymaps
      .filterIsInstance<DefaultKeymapImpl>()
      .filter { it.plugin.pluginId == PluginManagerCore.CORE_ID }
    assertThat(platformKeymaps).isNotEmpty()

    for (keymap in platformKeymaps) {
      assertThat(defaultKeymap.getKeymapDescription(keymap)).describedAs(keymap.name).isNotBlank()
    }
  }

  @Test
  fun `a user keymap has no description`() {
    val keymap = KeymapImpl()
    keymap.name = KeymapManager.DEFAULT_IDEA_KEYMAP

    assertThat(DefaultKeymap.getInstance().getKeymapDescription(keymap)).isNull()
  }
}
