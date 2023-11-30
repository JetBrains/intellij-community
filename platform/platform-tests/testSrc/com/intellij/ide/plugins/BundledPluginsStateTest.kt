// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
class BundledPluginsStateTest {
  @Test
  fun saving(@TempDir dir: Path) {
    val pluginIds = listOf(
      "foo" to null,
      "bar" to "UI",
    ).mapTo(LinkedHashSet()) {
      getIdeaDescriptor(it.first, it.second)
    }

    writePluginIdsToFile(pluginIds = pluginIds, configDir = dir)
    assertThat(readPluginIdsFromFile(configDir = dir)).hasSameElementsAs(pluginIds.map { it.pluginId to it.category })
  }

  @Test
  fun categoryLocalized() {
    for (descriptor in BundledPluginsState.loadedPlugins.filter { it.category != null }) {
      val category = descriptor.category ?: continue
      assertThat(CoreBundle.messageOrNull("plugin.category.${category.replace(' ', '.')}")).isEqualTo(category)
    }
  }
}

private fun getIdeaDescriptor(id: String, category: Category): IdeaPluginDescriptorImpl {
  val descriptor = IdeaPluginDescriptorImpl(RawPluginDescriptor(), Path.of(""), true, PluginId.getId(id), null)
  descriptor.category = category
  return descriptor
}