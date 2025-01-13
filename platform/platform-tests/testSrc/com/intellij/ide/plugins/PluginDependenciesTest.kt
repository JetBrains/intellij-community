// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.write
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test

internal class PluginDependenciesTest {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginDirPath get() = rootPath.resolve("plugin")

  @Test
  fun classLoader() {
    PluginBuilder().noDepends().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar").build(pluginDirPath.resolve("bar"))
    checkClassLoader()
  }

  @Test
  fun `classLoader - optional dependency`() {
    writeDescriptor("foo", """
    <idea-plugin>
      <id>foo</id>
      <depends optional="true" config-file="stream-debugger.xml">bar</depends>
      <vendor>JetBrains</vendor>
    </idea-plugin>
    """)

    pluginDirPath.resolve("foo/META-INF/stream-debugger.xml").write("""
     <idea-plugin>
      <actions>
      </actions>
    </idea-plugin>
    """)

    writeDescriptor("bar", """
    <idea-plugin>
      <id>bar</id>
      <vendor>JetBrains</vendor>
    </idea-plugin>
    """)

    checkClassLoader()
  }

  private fun checkClassLoader() {
    val list = PluginSetTestBuilder(pluginDirPath)
      .build()
      .enabledPlugins
    assertThat(list).hasSize(2)

    val bar = list[0]
    assertThat(bar.pluginId.idString).isEqualTo("bar")

    val foo = list[1]

    assertThat(foo.pluginDependencies.map { it.pluginId }).containsExactly(bar.pluginId)

    assertThat(foo.pluginId.idString).isEqualTo("foo")
    val fooClassLoader = foo.pluginClassLoader as PluginClassLoader
    assertThat(fooClassLoader._getParents()).containsExactly(bar)
  }

  @Test
  fun `disable plugin if dependency of required content module is not available`() {
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("required.module", PluginBuilder().packagePrefix("required").dependency("unknown"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).isEmpty()
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).isNotEmpty
    assertThat(errors.first().toString()).contains("sample.plugin", "requires plugin", "unknown")
  }

  @Test
  fun `embedded content module uses same classloader as the main module`() {
    val samplePluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder().packagePrefix("embedded"), loadingRule = ModuleLoadingRule.EMBEDDED, separateJar = true)
      .module("required.module", PluginBuilder().packagePrefix("required"), loadingRule = ModuleLoadingRule.REQUIRED, separateJar = true)
      .module("optional.module", PluginBuilder().packagePrefix("optional"))
      .build(samplePluginDir)
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).hasSize(1)
    val mainClassLoader = result.enabledPlugins.single().pluginClassLoader
    val embeddedModuleClassLoader = result.findEnabledModule("embedded.module")!!.pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
    assertThat((mainClassLoader as PluginClassLoader).files).containsExactly(
      samplePluginDir.resolve("lib/sample.plugin.jar"),
      samplePluginDir.resolve("lib/modules/embedded.module.jar"),
    )
    val requiredModuleClassLoader = result.findEnabledModule("required.module")!!.pluginClassLoader
    assertThat(requiredModuleClassLoader).isNotSameAs(mainClassLoader)
    val optionalModuleClassLoader = result.findEnabledModule("optional.module")!!.pluginClassLoader
    assertThat(optionalModuleClassLoader).isNotSameAs(mainClassLoader)
  }

  @Test
  fun `embedded content module without package prefix`() {
    val samplePluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder(), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(samplePluginDir)
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).hasSize(1)
    val mainClassLoader = result.enabledPlugins.single().pluginClassLoader
    val embeddedModuleClassLoader = result.findEnabledModule("embedded.module")!!.pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
  }

  @Test
  fun `dependencies of embedded content module are added to the main class loader`() {
    PluginBuilder()
      .noDepends()
      .id("dep")
      .build(pluginDirPath.resolve("dep"))
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder().packagePrefix("embedded").pluginDependency("dep"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).hasSize(2)
    val depPluginDescriptor = result.findEnabledPlugin(PluginId.getId("dep"))!!
    val mainClassLoader = result.findEnabledPlugin(PluginId.getId("sample.plugin"))!!.pluginClassLoader
    assertThat((mainClassLoader as PluginClassLoader)._getParents()).contains(depPluginDescriptor)
  }

  @Test
  fun `content module in separate JAR`() {
    val pluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("dep", PluginBuilder(), separateJar = true)
      .build(pluginDir)
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).hasSize(1)
    assertThat(result.getEnabledModules()).hasSize(2)
    val depModuleDescriptor = result.findEnabledModule("dep")!!
    assertThat(depModuleDescriptor.jarFiles).containsExactly(pluginDir.resolve("lib/modules/dep.jar"))
  }

  @Test
  fun testExpiredPluginNotLoaded() {
    PluginBuilder()
      .noDepends()
      .id("foo")
      .build(pluginDirPath.resolve("foo"))

    PluginBuilder()
      .noDepends()
      .id("bar")
      .build(pluginDirPath.resolve("bar"))

    val enabledPlugins = PluginSetTestBuilder(pluginDirPath)
      .withExpiredPlugins("foo")
      .build()
      .enabledPlugins

    assertThat(enabledPlugins).hasSize(1)
    assertThat(enabledPlugins.single().pluginId.idString).isEqualTo("bar")
  }

  @Test
  fun testLoadPluginWithDisabledDependency() {
    PluginBuilder().noDepends().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar").build(pluginDirPath.resolve("bar"))

    val pluginSet = PluginSetTestBuilder(pluginDirPath)
      .withDisabledPlugins("bar")
      .build()
    assertThat(pluginSet.enabledPlugins).isEmpty()
  }

  @Test
  fun testLoadPluginWithDisabledTransitiveDependency() {
    PluginBuilder()
      .noDepends()
      .id("org.jetbrains.plugins.gradle.maven")
      .implementationDetail()
      .depends("org.jetbrains.plugins.gradle")
      .build(pluginDirPath.resolve("intellij.gradle.java.maven"))
    PluginBuilder()
      .noDepends()
      .id("org.jetbrains.plugins.gradle")
      .depends("com.intellij.gradle")
      .implementationDetail()
      .build(pluginDirPath.resolve("intellij.gradle.java"))
    PluginBuilder()
      .noDepends()
      .id("com.intellij.gradle")
      .build(pluginDirPath.resolve("intellij.gradle"))

    val result = PluginSetTestBuilder(pluginDirPath)
      .withDisabledPlugins("com.intellij.gradle")
      .build()
    assertThat(result.enabledPlugins).isEmpty()
  }


  private fun writeDescriptor(id: String, @Language("xml") data: String) {
    pluginDirPath.resolve(id)
      .resolve(PluginManagerCore.PLUGIN_XML_PATH)
      .write(data.trimIndent())
  }
}
