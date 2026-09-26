// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.PathManager
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Checks the AI flag and the marker module that the IDE takes out while the flag is off. */
internal class AiEnabledStateTest {
  init {
    PluginManagerCore.isUnitTestMode = true // FIXME git rid of this IJPL-220869
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs: InMemoryFsExtension = InMemoryFsExtension()

  private val pluginsDirPath: Path get() = inMemoryFs.fs.getPath("/").resolve("wd/plugins")

  @Test
  fun `a missing file enables AI`(@TempDir configDir: Path) {
    withStateFile(configDir) {
      assertThat(AiEnabledState.isEnabled()).isTrue()
    }
  }

  @Test
  fun `the file disables AI`(@TempDir configDir: Path) {
    withStateFile(configDir) { file ->
      file.writeText("")
      assertThat(AiEnabledState.isEnabled()).isFalse()

      file.deleteExisting()
      assertThat(AiEnabledState.isEnabled()).isTrue()
    }
  }

  @Test
  fun `the content of the file does not matter`(@TempDir configDir: Path) {
    withStateFile(configDir) { file ->
      file.writeText("maybe")
      assertThat(AiEnabledState.isEnabled()).isFalse()
    }
  }

  @Test
  fun `the system property replaces the file`(@TempDir configDir: Path) {
    withStateFile(configDir) { file ->
      withProperty(AiEnabledState.AI_ENABLED_PROPERTY, "false") {
        assertThat(AiEnabledState.isEnabled()).isFalse()
      }

      file.writeText("")
      withProperty(AiEnabledState.AI_ENABLED_PROPERTY, "true") {
        assertThat(AiEnabledState.isEnabled()).isTrue()
      }
    }
  }

  @Test
  fun `the setter creates and deletes the file and reports the change`(@TempDir configDir: Path) {
    withStateFile(configDir) { file ->
      assertThat(AiEnabledState.setEnabled(true)).describedAs("a missing file already means enabled").isFalse()

      assertThat(AiEnabledState.setEnabled(false)).isTrue()
      assertThat(file).exists()
      assertThat(file.readText()).describedAs("the file stays empty").isEmpty()
      assertThat(AiEnabledState.setEnabled(false)).isFalse()

      assertThat(AiEnabledState.setEnabled(true)).isTrue()
      assertThat(file).doesNotExist()
    }
  }

  @Test
  fun `the marker takes its dependent out while AI is off`() {
    installFooPlugin()

    val pluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath).withAiEnabled(false).build()

    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("lib")
  }

  @Test
  fun `every content module loads while AI is on`() {
    installFooPlugin()

    val pluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath).build()

    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors(AIR_AI_MARKER_MODULE_ID.name, "a", "lib")
  }

  /** Installs a plugin that carries the marker, a module under it, and a required library module. */
  private fun installFooPlugin() {
    plugin("foo") {
      content(namespace = PluginModuleId.JETBRAINS_NAMESPACE) {
        module(AIR_AI_MARKER_MODULE_ID.name, loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
        module("a", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {
          dependencies { module(AIR_AI_MARKER_MODULE_ID.name) }
        }
        module("lib", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
      }
    }.installAt(pluginsDirPath)
  }

  /** Points the configuration directory at [directory], so the test never touches the real state file. */
  private fun withStateFile(directory: Path, body: (Path) -> Unit) {
    val old = PathManager.getConfigDir()
    PathManager.setExplicitConfigPath(directory)
    try {
      body(directory.resolve(AiEnabledState.AI_DISABLED_FILENAME))
    }
    finally {
      PathManager.setExplicitConfigPath(old)
    }
  }

  private fun withProperty(name: String, value: String, body: () -> Unit) {
    val old = System.setProperty(name, value)
    try {
      body()
    }
    finally {
      if (old == null) System.clearProperty(name) else System.setProperty(name, old)
    }
  }
}
