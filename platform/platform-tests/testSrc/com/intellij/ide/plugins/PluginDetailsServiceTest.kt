// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginDetailsService.ModuleDependencyInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.PluginSpec
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
// Synthetic plugins in this test do not contribute index extensions.
@SystemProperty(propertyKey = "intellij.indexes.skip.reload.on.plugin.load.unload", propertyValue = "true")
class PluginDetailsServiceTest {
  private val tempDir by tempPathFixture()

  private val detailsService: PluginDetailsService get() = PluginDetailsService.getInstance()

  @Test
  fun `getInstance returns the application service`() {
    assertThat(PluginDetailsService.getInstance()).isSameAs(service<PluginDetailsService>())
  }

  @Test
  fun `findDetails maps all plugin metadata`(): Unit = timeoutRunBlocking {
    val spec = plugin("test.details.full") {
      name = "Full Details Plugin"
      version = "1.2.3"
      description = "Plugin with all metadata"
      body = """
        <vendor email="dev@example.com" url="https://example.com">Example Vendor</vendor>
        <change-notes>Initial release</change-notes>
      """.trimIndent()
    }
    withLoadedPlugins(spec) {
      val details = detailsService.findDetails(PluginId.getId("test.details.full"))!!
      assertThat(details.id.idString).isEqualTo("test.details.full")
      assertThat(details.name).isEqualTo("Full Details Plugin")
      assertThat(details.version).isEqualTo("1.2.3")
      assertThat(details.description).isEqualTo("Plugin with all metadata")
      assertThat(details.changeNotes).isEqualTo("Initial release")
      assertThat(details.vendor.name).isEqualTo("Example Vendor")
      assertThat(details.vendor.email).isEqualTo("dev@example.com")
      assertThat(details.vendor.url).isEqualTo("https://example.com")
      assertThat(details.isBuiltIn).isFalse()
      assertThat(details.modules).isEmpty()
      assertThat(details.dependencies).isEmpty()
    }
  }

  @Test
  fun `findDetails uses defaults without product descriptor`(): Unit = timeoutRunBlocking {
    val spec = plugin("test.details.minimal") {}
    withLoadedPlugins(spec) {
      val details = detailsService.findDetails(PluginId.getId("test.details.minimal"))!!
      assertThat(details.changeNotes).isNull()
      assertThat(details.releaseVersion).isZero()
      assertThat(details.releaseDate).isNull()
      assertThat(details.isLicenseOptional).isFalse()
      assertThat(details.isBuiltIn).isFalse()
    }
  }

  @Test
  fun `findDetails lists content modules`(): Unit = timeoutRunBlocking {
    val spec = plugin("test.details.modules") {
      content {
        module("test.details.modules.first") {}
        module("test.details.modules.second") {}
      }
    }
    withLoadedPlugins(spec) {
      val details = detailsService.findDetails(PluginId.getId("test.details.modules"))!!
      assertThat(details.modules.map { it.name })
        .containsExactlyInAnyOrder("test.details.modules.first", "test.details.modules.second")
    }
  }

  @Test
  fun `findDetails lists required dependencies only`(): Unit = timeoutRunBlocking {
    val dependencySpec = plugin("test.details.dependency") {
      // A module dependency without a namespace resolves in the `jetbrains` namespace.
      content(namespace = PluginModuleId.JETBRAINS_NAMESPACE) {
        module("test.details.dependency.module") {
          moduleVisibility = ModuleVisibilityValue.PUBLIC
        }
      }
    }
    val dependentSpec = plugin("test.details.dependent") {
      dependsIntellijModulesLang()
      depends("test.details.dependency", configFile = "optional-dependency.xml") {}
      dependencies {
        plugin("test.details.dependency")
        module("test.details.dependency.module")
      }
    }
    withLoadedPlugins(dependencySpec, dependentSpec) {
      val details = detailsService.findDetails(PluginId.getId("test.details.dependent"))!!
      val dependencies = details.dependencies.map {
        when (it) {
          is ModuleDependencyInfo.OnPlugin -> "plugin:${it.pluginId.idString}"
          is ModuleDependencyInfo.OnModule -> "module:${it.name}"
        }
      }
      assertThat(dependencies).containsExactlyInAnyOrder(
        "plugin:com.intellij.modules.lang",
        "plugin:test.details.dependency",
        "module:test.details.dependency.module",
      )
    }
  }

  @Test
  fun `getActivePlugins reflects plugin load and unload`(): Unit = timeoutRunBlocking {
    val pluginId = PluginId.getId("test.details.active")
    val loaded = loadPlugin(plugin(pluginId.idString) {})
    try {
      assertThat(detailsService.isLoaded(pluginId)).isTrue()
      assertThat(detailsService.isDisabled(pluginId)).isFalse()
      assertThat(detailsService.getActivePlugins().map { it.id }.toList())
        .contains(pluginId)
        .doesNotContain(PluginManagerCore.CORE_ID)
    }
    finally {
      unload(loaded)
    }
    assertThat(detailsService.isLoaded(pluginId)).isFalse()
    assertThat(detailsService.getActivePlugins().map { it.id }.toList()).doesNotContain(pluginId)
  }

  @Test
  fun `unknown plugin has no details`() {
    val pluginId = PluginId.getId("test.details.unknown")
    assertThat(detailsService.findDetails(pluginId)).isNull()
    assertThat(detailsService.isLoaded(pluginId)).isFalse()
    assertThat(detailsService.isDisabled(pluginId)).isFalse()
    assertThat(detailsService.isBuiltIn(pluginId)).isFalse()
  }

  @Test
  fun `bundled plugin is built-in`() {
    val bundled = PluginManagerCore.loadedPlugins.first { it.isBundled && it.pluginId != PluginManagerCore.CORE_ID }
    assertThat(detailsService.isBuiltIn(bundled.pluginId)).isTrue()
    assertThat(detailsService.findDetails(bundled.pluginId)!!.isBuiltIn).isTrue()
  }

  /** A dynamic plugin load is a modal operation, so it runs on the EDT. */
  private suspend fun loadPlugin(spec: PluginSpec): Disposable = withContext(Dispatchers.EDT) {
    loadPluginWithText(pluginSpec = spec, pluginsDir = tempDir.resolve("plugins"))
  }

  private suspend fun unload(plugin: Disposable) {
    withContext(Dispatchers.EDT) {
      Disposer.dispose(plugin)
    }
  }

  /** Loads [specs] in order, runs [body], and unloads the plugins in reverse order. */
  private suspend fun withLoadedPlugins(vararg specs: PluginSpec, body: () -> Unit) {
    val loaded = ArrayList<Disposable>()
    try {
      for (spec in specs) {
        loaded.add(loadPlugin(spec))
      }
      body()
    }
    finally {
      for (plugin in loaded.asReversed()) {
        unload(plugin)
      }
    }
  }
}
