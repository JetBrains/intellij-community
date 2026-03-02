// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.buildDir
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path

// The system property `idea.kotlin.plugin.use.k1` is changed so tests should be sequential
@Execution(ExecutionMode.SAME_THREAD)
class KotlinK1andK2ModesTest {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val rootDir: Path get() = inMemoryFs.fs.getPath("/")

  @Test
  fun `plugin depending on kotlin disabled by default in K2 mode`() = withKotlinPluginMode(isK2 = true) {
    plugin("foo") {
      depends("org.jetbrains.kotlin")
    }.buildDir(rootDir.resolve("foo"))
    val (_, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNotNull()
    assertThat(reason).isInstanceOf(PluginIsIncompatibleWithKotlinMode::class.java)
  }

  @Test
  fun `explicitly incompatible plugin depending on kotlin disabled in K2 Mode`() = withKotlinPluginMode(isK2 = true) {
    plugin("foo") {
      depends("org.jetbrains.kotlin")
      extensions("""<supportsKotlinPluginMode supportsK2="false"/>""", ns = "org.jetbrains.kotlin")
    }.buildDir(rootDir.resolve("foo"))
    val (_, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNotNull()
    assertThat(reason).isInstanceOf(PluginIsIncompatibleWithKotlinMode::class.java)
  }

  @Test
  fun `explicitly incompatible plugin depending on kotlin disabled in K1 Mode`() = withKotlinPluginMode(isK2 = false) {
    plugin("foo") {
      depends("org.jetbrains.kotlin")
      extensions("""<supportsKotlinPluginMode supportsK1="false"/>""", ns = "org.jetbrains.kotlin")
    }.buildDir(rootDir.resolve("foo"))
    val (_, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNotNull()
    assertThat(reason).isInstanceOf(PluginIsIncompatibleWithKotlinMode::class.java)
  }

  @Test
  fun `plugin depending on kotlin is enabled when with supportsK2`() = withKotlinPluginMode(isK2 = true) {
    plugin("foo") {
      depends("org.jetbrains.kotlin")
      extensions("""<supportsKotlinPluginMode supportsK2="true"/>""", ns = "org.jetbrains.kotlin")
    }.buildDir(rootDir.resolve("foo"))
    val (_, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNull()
  }


  @Test
  fun `plugin optionally depending on kotlin plugin is not disabled by default in K2 mode and optional dependency is disabled`() = withKotlinPluginMode(isK2 = true) {
    plugin("foo") {
      depends("org.jetbrains.kotlin", configFile = "kt.xml") { }
    }.buildDir(rootDir.resolve("foo"))
    val (plugin, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNull()
    val dependency = plugin.dependencies.single()
    assertThat(dependency.subDescriptor).isNull()
  }

  @Test
  fun `plugin optionally depending on kotlin plugin is not disabled by default in K2 mode and optional dependency is not disabled`() = withKotlinPluginMode(isK2 = true) {
    plugin("foo") {
      depends("org.jetbrains.kotlin", configFile = "kt.xml") { }
      extensions("""<supportsKotlinPluginMode supportsK2="true"/>""", ns = "org.jetbrains.kotlin")
    }.buildDir(rootDir.resolve("foo"))
    val (plugin, reason) = getSinglePlugin(rootDir)
    assertThat(reason).isNull()
    val dependency = plugin.dependencies.single()
    assertThat(dependency.subDescriptor).isNotNull()
  }
}

private fun getSinglePlugin(rootDir: Path): Pair<IdeaPluginDescriptorImpl, PluginNonLoadReason?> {
  val allPlugins = PluginSetTestBuilder.fromPath(rootDir).discoverPlugins().second.pluginLists.flatMap { it.plugins }
  val plugin = allPlugins.single()
  return plugin to ProductPluginInitContext().validatePluginIsCompatible(plugin)
}

private inline fun withKotlinPluginMode(isK2: Boolean, action: () -> Unit) {
  val current = System.getProperty("idea.kotlin.plugin.use.k1")
  System.setProperty("idea.kotlin.plugin.use.k1", (!isK2).toString())
  try {
    action()
  }
  finally {
    if (current == null) {
      System.clearProperty("idea.kotlin.plugin.use.k1")
    }
    else {
      System.setProperty("idea.kotlin.plugin.use.k1", current)
    }
  }
}