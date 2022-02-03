// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.NioFiles
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.readText
import junit.framework.TestCase
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.writeText

class BundledPluginsStateTest : LightPlatformTestCase() {
  @Test
  fun testSaving() {
    val file = PathManager.getConfigDir().resolve(BundledPluginsState.BUNDLED_PLUGINS_FILENAME)
    BundledPluginsState.saveBundledPluginsOrLog(listOf(getIdeaDescriptor("a", null), getIdeaDescriptor("b", "Keyboard")))
    assertEquals("a|null\nb|Keyboard\n", file.readText())
  }

  @Test
  fun testParsing() {
    project.basePath
    val dir = Paths.get(project.basePath!!).resolve("kek")
    NioFiles.createDirectories(dir)
    val file = dir.resolve(BundledPluginsState.BUNDLED_PLUGINS_FILENAME)
    file.writeText("a|null\nb|Keyboard\nabs|Themes\nc|null")
    val parsingResult = BundledPluginsState.getBundledPlugins(dir)?.sortedBy(Pair<PluginId, String?>::first)
    TestCase.assertEquals(listOf(
      Pair(PluginId.getId("a"), null),
      Pair(PluginId.getId("abs"), "Themes"),
      Pair(PluginId.getId("b"), "Keyboard"),
      Pair(PluginId.getId("c"), null)
    ), parsingResult)
  }

  @Test
  fun testSavingState() {
    val savedIds = BundledPluginsState.getBundledPlugins(PathManager.getConfigDir())!!.toMutableList()
    val bundledIds = PluginManagerCore.getLoadedPlugins().filter { it.isBundled }
    assertSameElements(bundledIds.map { Pair(it.pluginId, it.category) }, savedIds)
    assertEquals(false, BundledPluginsState.shouldSave())
  }

  private fun getIdeaDescriptor(id: String, category: String?): IdeaPluginDescriptorImpl {
    val descriptor = IdeaPluginDescriptorImpl(RawPluginDescriptor(), Path.of(""), true, PluginId.getId(id), null)
    descriptor.category = category
    return descriptor
  }
}