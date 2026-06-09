// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path

@Execution(ExecutionMode.SAME_THREAD)
class KotlinK1andK2ModesTest {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val rootDir: Path get() = inMemoryFs.fs.getPath("/")

  @Test
  fun `plugin depending on kotlin enabled by default in K2 mode`() {
    plugin("foo") {
      depends("org.jetbrains.kotlin")
    }.installAt(rootDir)
    val (_, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNull()
  }


  @Test
  fun `plugin depending on kotlin is enabled when with supportsK2`() {
    plugin("foo") {
      depends("org.jetbrains.kotlin")
    }.installAt(rootDir)
    val (_, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNull()
  }


  @Test
  fun `plugin optionally depending on kotlin plugin is not disabled by default in K2 mode and optional dependency is enabled`() {
    plugin("foo") {
      depends("org.jetbrains.kotlin", configFile = "kt.xml") { }
    }.installAt(rootDir)
    val (plugin, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNull()
    val dependency = plugin.dependencies.single()
    assertThat(dependency.subDescriptor).isNotNull
  }
}

private fun getSinglePlugin(rootDir: Path): Pair<IdeaPluginDescriptorImpl, PluginNonLoadReason?> {
  val allPlugins = PluginSetTestBuilder.fromPath(rootDir).discoverPlugins().second.pluginLists.flatMap { it.plugins }
  val plugin = allPlugins.single()
  return plugin to PluginInitContextFactory.getInstance().createActualContext().validatePluginIsCompatible(plugin)
}
