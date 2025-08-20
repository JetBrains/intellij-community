// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.directoryStreamIfExists
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

internal class ClassLoaderConfiguratorTest {
  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  val rootDir: Path get() = inMemoryFs.fs.getPath("/")

  @Test
  fun `plugin must be after child`() {
    val emptyPath = Path.of("")
    val kotlin = PluginMainDescriptor(PluginDescriptorBuilder.builder().apply { id = "org.jetbrains.kotlin" }.build(), emptyPath, isBundled = false)
    val gradle = PluginMainDescriptor(PluginDescriptorBuilder.builder().apply { id = "org.jetbrains.plugins.gradle" }.build(), emptyPath, isBundled = false)
    val emptyBuilder = PluginDescriptorBuilder.builder()
    val kotlinGradleJava = kotlin.createContentModuleInTest(
      subBuilder = emptyBuilder,
      descriptorPath = "",
      module = PluginContentDescriptor.ModuleItem(moduleId = PluginModuleId("kotlin.gradle.gradle-java"),
                                                  loadingRule = ModuleLoadingRule.OPTIONAL,
                                                  configFile = null,
                                                  descriptorContent = null))
    val kotlinCompilerGradle = kotlin.createContentModuleInTest(
      subBuilder = emptyBuilder,
      descriptorPath = "",
      module = PluginContentDescriptor.ModuleItem(moduleId = PluginModuleId("kotlin.compiler-plugins.annotation-based-compiler-support.gradle"),
                                                  loadingRule = ModuleLoadingRule.OPTIONAL,
                                                  configFile = null,
                                                  descriptorContent = null))
    val plugins = arrayOf(kotlin, gradle, kotlinGradleJava, kotlinCompilerGradle)
    sortDependenciesInPlace(plugins)
    assertThat(plugins.last().contentModuleId).isNull()
  }

  @Test
  fun `child with common package prefix must be after included sibling`() {
    val plugin = PluginMainDescriptor(
      PluginDescriptorBuilder.builder().apply {
        id = "com.example"
      }.build(),
      Path.of(""),
      false,
    )
    fun createModuleDescriptor(moduleId: String): ContentModuleDescriptor {
      return plugin.createContentModuleInTest(
        subBuilder = PluginDescriptorBuilder.builder().apply { `package` = moduleId },
        descriptorPath = "",
        module = PluginContentDescriptor.ModuleItem(moduleId = PluginModuleId(moduleId), configFile = null, descriptorContent = null, loadingRule = ModuleLoadingRule.OPTIONAL),
      )
    }
    val modules = arrayOf(
      createModuleDescriptor("com.foo"),
      createModuleDescriptor("com.foo.bar"),
    )
    sortDependenciesInPlace(modules)
    assertThat(modules.map { it.moduleId.id }).containsExactly("com.foo.bar", "com.foo")
  }

  @Test
  fun regularPluginClassLoaderIsUsedIfPackageSpecified() {
    val loadingResult = loadPlugins(modulePackage = "com.example.extraSupportedFeature")
    val plugin = loadingResult
      .enabledPlugins
      .get(1)
    assertThat(plugin.contentModules[0].pluginClassLoader).isInstanceOf(PluginAwareClassLoader::class.java)

    val scope = createPluginDependencyAndContentBasedScope(
      plugin,
      PluginSetBuilder(loadingResult.enabledPlugins.toSet()).createPluginSetWithEnabledModulesMap()
    )!!
    assertThat(scope.isDefinitelyAlienClass(name = "dd", packagePrefix = "dd", force = false)).isNull()
    assertThat(scope.isDefinitelyAlienClass(name = "com.example.extraSupportedFeature.Foo", packagePrefix = "com.example.extraSupportedFeature.", force = false))
      .isEqualToIgnoringWhitespace("Class com.example.extraSupportedFeature.Foo must not be requested from main classloader of p_dependent plugin. " +
                 "Matches content module (packagePrefix=com.example.extraSupportedFeature., moduleId=com.example.sub).")
  }

  @Test
  fun `inject content module if another plugin specifies dependency in old format`() {
    val rootDir = inMemoryFs.fs.getPath("/")
    plugin("1-foo") {
      packagePrefix = "com.foo"
      content {
        module("com.example.sub") { packagePrefix="com.foo.sub" }
      }
    }.buildDir(rootDir.resolve("1-foo"))
    plugin("2-bar") {
      depends("1-foo")
    }.buildDir(rootDir.resolve("2-bar"))

    val plugins = runBlocking { loadDescriptors (rootDir).enabledPlugins }
    assertThat(plugins).hasSize(2)
    val barPlugin = plugins.get(1)
    assertThat(barPlugin.pluginId.idString).isEqualTo("2-bar")

    val classLoaderConfigurator = ClassLoaderConfigurator(PluginSetBuilder(plugins.toSet()).createPluginSetWithEnabledModulesMap())
    classLoaderConfigurator.configure()

    assertThat((barPlugin.pluginClassLoader as PluginClassLoader)._getParents().map { it.descriptorPath })
      .containsExactly("com.example.sub.xml", null)
  }

  private fun loadPlugins(modulePackage: String?): PluginLoadingResult {
    plugin("p_dependency") {
      packagePrefix = "com.bar"
      extensionPoints = """<extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"""
    }.buildDir(rootDir.resolve("p_dependency"))
    plugin("p_dependent") {
      packagePrefix = "com.example"
      content {
        module("com.example.sub") {
          packagePrefix = modulePackage
          extensionPoints = """<extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"""
        }
      }
    }.buildDir(rootDir.resolve("p_dependent"))

    val loadResult = runBlocking { loadDescriptors(rootDir) }
    val plugins = loadResult.enabledPlugins
    assertThat(plugins).hasSize(2)

    val classLoaderConfigurator = ClassLoaderConfigurator(PluginSetBuilder(plugins.toSet()).createPluginSetWithEnabledModulesMap())
    classLoaderConfigurator.configure()
    return loadResult
  }
}

internal fun loadDescriptors(dir: Path): PluginLoadingResult {
  val buildNumber = BuildNumber.fromString("2042.0")!!
  val result = PluginLoadingResult()
  val initContext = PluginInitializationContext.buildForTest(
    essentialPlugins = emptySet(),
    disabledPlugins = emptySet(),
    expiredPlugins = emptySet(),
    brokenPluginVersions = emptyMap(),
    getProductBuildNumber = { buildNumber },
    requirePlatformAliasDependencyForLegacyPlugins = false,
    checkEssentialPlugins = false,
    explicitPluginSubsetToLoad = null,
    disablePluginLoadingCompletely = false,
    currentProductModeId = ProductMode.MONOLITH.id,
  )
  val loadingContext = PluginDescriptorLoadingContext(getBuildNumberForDefaultDescriptorVersion = { buildNumber })
  // constant order in tests
  val paths = dir.directoryStreamIfExists { it.sorted() }!!
  val descriptors = paths.mapNotNull { loadDescriptorFromFileOrDir(file = it, loadingContext = loadingContext, pool = ZipFilePoolImpl()) }
  loadingContext.use {
    result.initAndAddAll(
      descriptorLoadingResult = PluginDescriptorLoadingResult.build(listOf(DiscoveredPluginsList(descriptors, PluginsSourceContext.Custom))),
      initContext = initContext
    )
  }
  return result
}
