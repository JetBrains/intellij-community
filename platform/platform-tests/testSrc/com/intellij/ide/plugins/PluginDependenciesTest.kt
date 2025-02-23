// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.InMemoryFsExtension
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
  fun `plugin is loaded when dependency is resolved - depends`() {
    bar()
    `foo depends bar`()

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasClassloaderParents(bar)
  }

  @Test
  fun `plugin is not loaded when dependency is not resolved - depends`() {
    `foo depends bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
  }

  @Test
  fun `plugin is loaded when dependency is resolved - depends optional`() {
    `foo depends-optional bar`()
    bar()

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasClassloaderParents(bar)
  }

  @Test
  fun `plugin is loaded when dependency is not resolved - depends optional`() {
    `foo depends-optional bar`()
    baz()

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
    val (foo, baz) = pluginSet.getEnabledPlugins("foo", "baz")
    assertThat(foo).doesNotHaveClassloaderParents(baz)
    assertThat(baz).doesNotHaveClassloaderParents(foo)
  }

  @Test
  fun `v1 plugin gets v2 content module in classloader parents even without direct dependency - depends`() {
    `foo depends bar`()
    `bar with optional module`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasClassloaderParents(bar, pluginSet.getEnabledModule("bar.module"))
  }

  @Test
  fun `v2 plugin dependency brings only the implicit main module - plugin dependency`() {
    `foo plugin-dependency bar`()
    `bar with optional module`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo)
      .hasClassloaderParents(bar)
      .doesNotHaveClassloaderParents(pluginSet.getEnabledModule("bar.module"))
  }

  @Test
  fun `plugin is not loaded if it has a dependency on v2 module - depends`() {
    `bar with optional module`()
    PluginBuilder.empty().id("foo").depends("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it has a dependency on v2 module - plugin dependency`() {
    `bar with optional module`()
    PluginBuilder.empty().id("foo").pluginDependency("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded if it has a dependency on v2 module - module dependency`() {
    `bar with optional module`()
    PluginBuilder.empty().id("foo").dependency("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
  }

  @Test
  fun `plugin is not loaded if it has a module dependency on v2 plugin without package prefix`() {
    bar()
    PluginBuilder.empty().id("foo").dependency("bar").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded if it has a module dependency on v2 plugin with package prefix`() {
    PluginBuilder.empty().id("bar").packagePrefix("idk").build(pluginDirPath.resolve("bar"))
    PluginBuilder.empty().id("foo").dependency("bar").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
  }

  @Test
  fun `plugin is not loaded if required module is not available`() {
    PluginManagerCore.getAndClearPluginLoadingErrors() //clear errors which may be registered by other tests
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("required.module", PluginBuilder.withModulesLang().packagePrefix("required").dependency("unknown"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet()
    assertThat(result).doesNotHaveEnabledPlugins()
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
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    val mainClassLoader = result.getEnabledPlugin("sample.plugin").pluginClassLoader
    val embeddedModuleClassLoader = result.getEnabledModule("embedded.module").pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
    assertThat((mainClassLoader as PluginClassLoader).files).containsExactly(
      samplePluginDir.resolve("lib/sample.plugin.jar"),
      samplePluginDir.resolve("lib/modules/embedded.module.jar"),
    )
    val requiredModuleClassLoader = result.getEnabledModule("required.module").pluginClassLoader
    assertThat(requiredModuleClassLoader).isNotSameAs(mainClassLoader)
    val optionalModuleClassLoader = result.getEnabledModule("optional.module").pluginClassLoader
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
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    val mainClassLoader = result.getEnabledPlugin("sample.plugin").pluginClassLoader
    val embeddedModuleClassLoader = result.getEnabledModule("embedded.module").pluginClassLoader
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
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin", "dep")
    val (sample, dep) = result.getEnabledPlugins("sample.plugin", "dep")
    assertThat(sample).hasClassloaderParents(dep)
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
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("embedded.module", "required.module", "required2.module")
    val (req, req2, embed) = pluginSet.getEnabledModules("required.module", "required2.module", "embedded.module")
    assertThat(req2).hasClassloaderParents(req)
    assertThat(req).hasClassloaderParents(embed)
  }

  @Test
  fun `content module in separate JAR`() {
    val pluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("dep", PluginBuilder.withModulesLang(), separateJar = true)
      .build(pluginDir)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    assertThat(result).hasExactlyEnabledModulesWithoutMainDescriptors("dep")
    val depModuleDescriptor = result.getEnabledModule("dep")
    assertThat(depModuleDescriptor.jarFiles).containsExactly(pluginDir.resolve("lib/modules/dep.jar"))
  }

  @Test
  fun `plugin is not loaded if it is expired`() {
    foo()
    bar()
    val pluginSet = buildPluginSet(expiredPluginIds = arrayOf("foo"))
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded when it has a disabled dependency - depends`() {
    `foo depends bar`()
    bar()
    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("bar"))
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
  }

  @Test
  fun `plugin is not loaded when it has a transitive disabled dependency - depends`() {
    PluginBuilder.empty()
      .id("com.intellij.gradle")
      .build(pluginDirPath.resolve("intellij.gradle"))
    PluginBuilder.empty()
      .id("org.jetbrains.plugins.gradle")
      .depends("com.intellij.gradle")
      .implementationDetail()
      .build(pluginDirPath.resolve("intellij.gradle.java"))
    PluginBuilder.empty()
      .id("org.jetbrains.plugins.gradle.maven")
      .implementationDetail()
      .depends("org.jetbrains.plugins.gradle")
      .build(pluginDirPath.resolve("intellij.gradle.java.maven"))
    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("com.intellij.gradle"))
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
  }

  @Test
  fun `plugin is loaded when it has an optional disabled dependency - depends optional`() {
    `foo depends-optional bar`()
    bar()
    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("bar"))
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
  }

  @Test
  fun `plugin is loaded when it has a dependency on plugin alias (depends)`() {
    `foo depends bar`()
    `baz with alias bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
  }

  @Test
  fun `plugin is loaded when it has a plugin dependency on plugin alias`() {
    `foo plugin-dependency bar`()
    `baz with alias bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
  }

  @Test
  fun `plugin is not loaded when it has a module dependency on plugin alias`() {
    `foo module-dependency bar`()
    `baz with alias bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("baz")
  }

  private fun foo() = PluginBuilder.empty().id("foo").build(pluginDirPath.resolve("foo"))
  private fun `foo depends bar`() = PluginBuilder.empty().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
  private fun `foo depends-optional bar`() = PluginBuilder.empty().id("foo")
    .depends("bar", PluginBuilder.empty().actions(""))
    .build(pluginDirPath.resolve("foo"))
  private fun `foo plugin-dependency bar`() = PluginBuilder.empty().id("foo").pluginDependency("bar").build(pluginDirPath.resolve("foo"))
  private fun `foo module-dependency bar`() = PluginBuilder.empty().id("foo").dependency("bar").build(pluginDirPath.resolve("foo"))

  private fun bar() = PluginBuilder.empty().id("bar").build(pluginDirPath.resolve("bar"))
  private fun `bar with optional module`() = PluginBuilder.empty().id("bar")
    .module("bar.module", PluginBuilder.withModulesLang().packagePrefix("bar.module"))
    .build(pluginDirPath.resolve("bar"))

  private fun baz() = PluginBuilder.empty().id("baz").build(pluginDirPath.resolve("baz"))
  private fun `baz with alias bar`() = PluginBuilder.empty().id("baz").pluginAlias("bar").build(pluginDirPath.resolve("baz"))

  private fun buildPluginSet(expiredPluginIds: Array<String> = emptyArray(), disabledPluginIds: Array<String> = emptyArray()) =
    PluginSetTestBuilder(pluginDirPath)
      .withExpiredPlugins(*expiredPluginIds)
      .withDisabledPlugins(*disabledPluginIds)
      .build()
}
