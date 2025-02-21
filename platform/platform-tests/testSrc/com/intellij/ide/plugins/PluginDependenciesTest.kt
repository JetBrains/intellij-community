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
  fun `depends - plugin loads when dependency is resolved`() {
    PluginBuilder.empty().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder.empty().id("bar").build(pluginDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasClassloaderParents(bar)
  }

  @Test
  fun `depends - plugin does not load when dependency is not resolved`() {
    PluginBuilder.empty().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasNoEnabledPlugins()
  }

  @Test
  fun `depends optional - plugin loads when dependency is resolved`() {
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

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasClassloaderParents(bar)
  }

  @Test
  fun `depends optional - plugin loads when dependency is not resolved`() {
    PluginBuilder.empty().id("foo")
      .depends("bar", PluginBuilder.empty().actions(""), "foo.opt.xml")
      .build(pluginDirPath.resolve("foo"))
    PluginBuilder.empty().id("baz").build(pluginDirPath.resolve("baz"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
    val (foo, baz) = pluginSet.getEnabledPlugins("foo", "baz")
    assertThat(foo).doesNotHaveClassloaderParents(baz)
    assertThat(baz).doesNotHaveClassloaderParents(foo)
  }

  @Test
  fun `depends - v1 plugin gets v2 content module in classloader parents even without direct dependency`() {
    PluginBuilder.empty().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder.empty().id("bar")
      .module("bar.module", PluginBuilder.withModulesLang().packagePrefix("bar.module"))
      .build(pluginDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasClassloaderParents(bar, pluginSet.getEnabledModule("bar.module"))
  }

  @Test
  fun `dependency plugin - v2 plugin dependency brings only the implicit main module`() {
    PluginBuilder.empty().id("foo").pluginDependency("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder.empty().id("bar")
      .module("bar.module", PluginBuilder.withModulesLang().packagePrefix("bar.module"))
      .build(pluginDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo)
      .hasClassloaderParents(bar)
      .doesNotHaveClassloaderParents(pluginSet.getEnabledModule("bar.module"))
  }

  @Test
  fun `disable plugin if dependency of required content module is not available`() {
    PluginManagerCore.getAndClearPluginLoadingErrors() //clear errors which may be registered by other tests
    
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("required.module", PluginBuilder.withModulesLang().packagePrefix("required").dependency("unknown"), loadingRule = ModuleLoadingRule.REQUIRED)
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
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder.withModulesLang().packagePrefix("embedded"), loadingRule = ModuleLoadingRule.EMBEDDED, separateJar = true)
      .module("required.module", PluginBuilder.withModulesLang().packagePrefix("required"), loadingRule = ModuleLoadingRule.REQUIRED, separateJar = true)
      .module("optional.module", PluginBuilder.withModulesLang().packagePrefix("optional"))
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
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder.withModulesLang(), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(samplePluginDir)
    val result = buildPluginSet()
    assertThat(result.enabledPlugins).hasSize(1)
    val mainClassLoader = result.enabledPlugins.single().pluginClassLoader
    val embeddedModuleClassLoader = result.findEnabledModule("embedded.module")!!.pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
  }

  @Test
  fun `dependencies of embedded content module are added to the main class loader`() {
    PluginBuilder.empty()
      .id("dep")
      .build(pluginDirPath.resolve("dep"))
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder.withModulesLang().packagePrefix("embedded").pluginDependency("dep"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet()
    assertThat(result.enabledPlugins).hasSize(2)
    val depPluginDescriptor = result.findEnabledPlugin(PluginId.getId("dep"))!!
    val mainClassLoader = result.findEnabledPlugin(PluginId.getId("sample.plugin"))!!.pluginClassLoader
    assertThat((mainClassLoader as PluginClassLoader)._getParents()).contains(depPluginDescriptor)
  }

  @Test
  fun `dependencies between plugin modules`() {
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder.withModulesLang().packagePrefix("embedded"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .module("required.module", PluginBuilder.withModulesLang().packagePrefix("required").dependency("embedded.module"), loadingRule = ModuleLoadingRule.REQUIRED)
      .module("required2.module", PluginBuilder.withModulesLang().packagePrefix("required2").dependency("required.module"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet.getEnabledModules()).hasSize(4)
    val requiredModuleDescriptor = pluginSet.findEnabledModule("required.module")!!
    val requiredModule2Descriptor = pluginSet.findEnabledModule("required2.module")!!
    val embeddedModuleDescriptor = pluginSet.findEnabledModule("embedded.module")!!
    assertThat(requiredModule2Descriptor).hasClassloaderParents(requiredModuleDescriptor)
    assertThat(requiredModuleDescriptor).hasClassloaderParents(embeddedModuleDescriptor)
  }

  @Test
  fun `content module in separate JAR`() {
    val pluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("dep", PluginBuilder.withModulesLang(), separateJar = true)
      .build(pluginDir)
    val result = buildPluginSet()
    assertThat(result.enabledPlugins).hasSize(1)
    assertThat(result.getEnabledModules()).hasSize(2)
    val depModuleDescriptor = result.findEnabledModule("dep")!!
    assertThat(depModuleDescriptor.jarFiles).containsExactly(pluginDir.resolve("lib/modules/dep.jar"))
  }

  @Test
  fun testExpiredPluginNotLoaded() {
    PluginBuilder.empty()
      .id("foo")
      .build(pluginDirPath.resolve("foo"))

    PluginBuilder.empty()
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
    PluginBuilder.empty().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder.empty().id("bar").build(pluginDirPath.resolve("bar"))

    val pluginSet = PluginSetTestBuilder(pluginDirPath)
      .withDisabledPlugins("bar")
      .build()
    assertThat(pluginSet.enabledPlugins).isEmpty()
  }

  @Test
  fun testLoadPluginWithDisabledTransitiveDependency() {
    PluginBuilder.empty()
      .id("org.jetbrains.plugins.gradle.maven")
      .implementationDetail()
      .depends("org.jetbrains.plugins.gradle")
      .build(pluginDirPath.resolve("intellij.gradle.java.maven"))
    PluginBuilder.empty()
      .id("org.jetbrains.plugins.gradle")
      .depends("com.intellij.gradle")
      .implementationDetail()
      .build(pluginDirPath.resolve("intellij.gradle.java"))
    PluginBuilder.empty()
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
