// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.write
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class PluginDependenciesTest {
  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginDirPath get() = rootPath.resolve("plugin")

  @Test
  fun `simple depends tag`() {
    PluginBuilder().noDepends().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar").build(pluginDirPath.resolve("bar"))
    assertFooHasPluginDependencyOnBar()
  }

  @Test
  fun `simple depends tag with optional attribute`() {
    writeDescriptor("foo", """
    <idea-plugin>
      <id>foo</id>
      <depends optional="true" config-file="optional-part.xml">bar</depends>
      <vendor>JetBrains</vendor>
    </idea-plugin>
    """)

    pluginDirPath.resolve("foo/META-INF/optional-part.xml").write("""
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

    assertFooHasPluginDependencyOnBar()
  }

  private fun assertFooHasPluginDependencyOnBar() {
    val list = buildPluginSet()
      .enabledPlugins
    assertThat(list).hasSize(2)

    val bar = list[0]
    assertThat(bar.pluginId.idString).isEqualTo("bar")

    val foo = list[1]

    assertThat(foo.pluginDependencies.map { it.pluginId }).containsExactly(bar.pluginId)

    assertThat(foo.pluginId.idString).isEqualTo("foo")
    checkParentClassLoaders(foo, bar)
  }

  private fun checkParentClassLoaders(descriptor: IdeaPluginDescriptorImpl, vararg expectedParents: IdeaPluginDescriptorImpl) {
    val classLoader = descriptor.pluginClassLoader as PluginClassLoader
    assertThat(classLoader._getParents()).containsExactlyInAnyOrder(*expectedParents)
  }

  @Test
  fun `dependency on plugin with content modules with depends tag`() {
    PluginBuilder().noDepends().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar")
      .module("bar.module", PluginBuilder().packagePrefix("bar.module"))
      .build(pluginDirPath.resolve("bar"))

    val pluginSet = buildPluginSet()
    val enabledPlugins = pluginSet.enabledPlugins.sortedBy { it.pluginId.idString }
    assertThat(enabledPlugins).hasSize(2)
    val (bar, foo) = enabledPlugins
    checkParentClassLoaders(foo, bar, pluginSet.findEnabledModule("bar.module")!!)
  }
  
  @Test
  fun `dependency on plugin with content modules with dependencies tag`() {
    PluginBuilder().noDepends().id("foo").pluginDependency("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar")
      .module("bar.module", PluginBuilder().packagePrefix("bar.module"))
      .build(pluginDirPath.resolve("bar"))

    val pluginSet = buildPluginSet()
    val enabledPlugins = pluginSet.enabledPlugins.sortedBy { it.pluginId.idString }
    assertThat(enabledPlugins).hasSize(2)
    val (bar, foo) = enabledPlugins
    checkParentClassLoaders(foo, bar)
  }

  @Test
  fun `disable plugin if dependency of required content module is not available`() {
    PluginManagerCore.getAndClearPluginLoadingErrors() //clear errors which may be registered by other tests
    
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("required.module", PluginBuilder().packagePrefix("required").dependency("unknown"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet()
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
    val result = buildPluginSet()
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
    val result = buildPluginSet()
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
    val result = buildPluginSet()
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
    val result = buildPluginSet()
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

  private fun buildPluginSet() = PluginSetTestBuilder(pluginDirPath).build()
}
