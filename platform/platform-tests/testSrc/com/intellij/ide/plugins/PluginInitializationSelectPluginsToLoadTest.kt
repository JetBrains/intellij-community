// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.buildDir
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.platform.testFramework.plugins.pluginAlias
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Tests for plugin initialization: [PluginInitializationContext.selectPluginsToLoad]
 * 
 * This function performs plugin selection (compatibility, version selection, disabled/incompatible filtering)
 * and ID conflict resolution in a single operation.
 */
class PluginInitializationSelectPluginsToLoadTest {
  
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
    Logger.setUnitTestMode()
    PluginManagerCore.isUnitTestMode = true
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginsDirPath get() = rootPath.resolve("wd/plugins")

  private data class ExcludedPluginInfo(
    val plugin: PluginMainDescriptor,
    val reason: PluginNonLoadReason
  )

  private fun createInitContext(
    essentialPlugins: Set<PluginId> = emptySet(),
    disabledPlugins: Set<PluginId> = emptySet(),
    productBuildNumber: BuildNumber = BuildNumber.fromString("241.0")!!,
    explicitPluginSubsetToLoad: Set<PluginId>? = null,
    disablePluginLoadingCompletely: Boolean = false,
  ): PluginInitializationContext {
    return PluginInitializationContext.buildForTest(
      essentialPlugins = essentialPlugins,
      disabledPlugins = disabledPlugins,
      expiredPlugins = emptySet(),
      brokenPluginVersions = emptyMap(),
      getProductBuildNumber = { productBuildNumber },
      requirePlatformAliasDependencyForLegacyPlugins = false,
      checkEssentialPlugins = false,
      explicitPluginSubsetToLoad = explicitPluginSubsetToLoad,
      disablePluginLoadingCompletely = disablePluginLoadingCompletely,
      currentProductModeId = "test"
    )
  }

  private fun testPluginSelection(
    essentialPlugins: Set<PluginId> = emptySet(),
    disabledPlugins: Set<PluginId> = emptySet(),
    productBuildNumber: BuildNumber = BuildNumber.fromString("241.0")!!,
    explicitPluginSubsetToLoad: Set<PluginId>? = null,
    disablePluginLoadingCompletely: Boolean = false,
    discoveryResult: PluginsDiscoveryResult,
  ): Pair<UnambiguousPluginSet, MutableList<ExcludedPluginInfo>> {
    val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
    val initContext = createInitContext(
      essentialPlugins = essentialPlugins,
      disabledPlugins = disabledPlugins,
      productBuildNumber = productBuildNumber,
      explicitPluginSubsetToLoad = explicitPluginSubsetToLoad,
      disablePluginLoadingCompletely = disablePluginLoadingCompletely
    )

    val result = initContext.selectPluginsToLoad(
      discoveryResult.pluginLists,
      onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
    )

    return result to excludedPlugins
  }

  @Nested
  inner class PluginSelection {

    @Test
    fun `select newer version when multiple versions exist`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      plugin("foo") { version = "2.0" }.buildDir(pluginsDirPath.resolve("foo_2-0"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].version).isEqualTo("2.0")
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginVersionIsSuperseded::class.java)
      assertThat(excludedPlugins[0].plugin.version).isEqualTo("1.0")
    }

    @Test
    fun `select older version when newer is incompatible`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "300.*"
      }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      
      plugin("foo") {
        version = "2.0"
        untilBuild = "200.*"
      }.buildDir(pluginsDirPath.resolve("foo_2-0"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
        discoveryResult = discoveryResult
      )

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].version).isEqualTo("1.0")
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginUntilBuildConstraintViolation::class.java)
      assertThat(excludedPlugins[0].plugin.version).isEqualTo("2.0")
    }

    @Test
    fun `SystemPropertyProvided source overrides regardless of version`() {
      val customPath = pluginsDirPath.resolve("custom")
      val systemPath = pluginsDirPath.resolve("system")
      
      plugin("foo") { version = "2.0" }.buildDir(customPath.resolve("foo_2-0"))
      plugin("foo") { version = "1.0" }.buildDir(systemPath.resolve("foo_1-0"))

      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext()
      
      val customPlugins = PluginSetTestBuilder.fromPath(customPath).discoverPlugins().second
      val systemPlugins = PluginSetTestBuilder.fromPath(systemPath).discoverPlugins().second
      
      // Manually create discovery result with different sources
      val discoveredPlugins = listOf(
        DiscoveredPluginsList(customPlugins.pluginLists[0].plugins, PluginsSourceContext.Custom),
        DiscoveredPluginsList(systemPlugins.pluginLists[0].plugins, PluginsSourceContext.SystemPropertyProvided)
      )
      
      val result = initContext.selectPluginsToLoad(
        discoveredPlugins,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].version).isEqualTo("1.0")
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginVersionIsSuperseded::class.java)
      assertThat(excludedPlugins[0].plugin.version).isEqualTo("2.0")
    }

    @Test
    fun `bundled plugin with lower version is superseded regardless of discovery order`() {
      val bundledPath = pluginsDirPath.resolve("bundled")
      val customPath = pluginsDirPath.resolve("custom")

      plugin("foo") { version = "1.0" }.buildDir(bundledPath.resolve("foo_1-0"))
      plugin("foo") { version = "2.0" }.buildDir(customPath.resolve("foo_2-0"))

      val bundledPlugins = PluginSetTestBuilder.fromPath(bundledPath).discoverPlugins().second
      val customPlugins = PluginSetTestBuilder.fromPath(customPath).discoverPlugins().second

      val bundledList = DiscoveredPluginsList(bundledPlugins.pluginLists[0].plugins, PluginsSourceContext.Bundled)
      val customList = DiscoveredPluginsList(customPlugins.pluginLists[0].plugins, PluginsSourceContext.Custom)

      fun assertBundledIsSuperseded(discoveryResult: List<DiscoveredPluginsList>) {
        val discoveryResult = PluginsDiscoveryResult.build(discoveryResult)
        val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

        assertThat(result.plugins).hasSize(1)
        assertThat(result.plugins[0].version).isEqualTo("2.0")
        assertThat(excludedPlugins).hasSize(1)
        assertThat(excludedPlugins[0].reason).isInstanceOf(PluginVersionIsSuperseded::class.java)
        assertThat(excludedPlugins[0].plugin.version).isEqualTo("1.0")
      }

      assertBundledIsSuperseded(listOf(bundledList, customList))
      assertBundledIsSuperseded(listOf(customList, bundledList))
    }

    @Test
    fun `three versions select newest compatible`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      plugin("foo") { version = "2.0" }.buildDir(pluginsDirPath.resolve("foo_2-0"))
      plugin("foo") { version = "3.0" }.buildDir(pluginsDirPath.resolve("foo_3-0"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].version).isEqualTo("3.0")
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginVersionIsSuperseded }).isTrue()
    }

    @Test
    fun `incompatible plugin is excluded`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "300.*"
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        untilBuild = "100.*"
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
        discoveryResult = discoveryResult
      )

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].pluginId.idString).isEqualTo("foo")
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginUntilBuildConstraintViolation::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("bar")
    }

    @Test
    fun `all plugins incompatible produces empty result`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "100.*"
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        untilBuild = "100.*"
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
        discoveryResult = discoveryResult
      )

      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginUntilBuildConstraintViolation }).isTrue()
    }

    @Test
    fun `empty plugin list produces empty result`() {
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext()
      
      val result = initContext.selectPluginsToLoad(
        emptyList(),
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).isEmpty()
    }

    @Test
    fun `multiple plugins with different IDs all kept`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))
      plugin("baz") { version = "1.0" }.buildDir(pluginsDirPath.resolve("baz"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).hasSize(3)
      assertThat(result.plugins.map { it.pluginId.idString })
        .containsExactlyInAnyOrder("foo", "bar", "baz")
      assertThat(excludedPlugins).isEmpty()
    }

    @Test
    fun `mixed compatible and incompatible versions`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "300.*"
      }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      
      plugin("foo") {
        version = "2.0"
        untilBuild = "200.*"
      }.buildDir(pluginsDirPath.resolve("foo_2-0"))
      
      plugin("foo") {
        version = "3.0"
        untilBuild = "100.*"
      }.buildDir(pluginsDirPath.resolve("foo_3-0"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
        discoveryResult = discoveryResult
      )

      // Only version 1.0 is compatible
      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].version).isEqualTo("1.0")
      
      // Versions 2.0 and 3.0 are incompatible
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginUntilBuildConstraintViolation }).isTrue()
      assertThat(excludedPlugins.map { it.plugin.version }).containsExactlyInAnyOrder("2.0", "3.0")
    }

    @Test
    fun `incompatible plugin is excluded before disabled check`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "100.*" // incompatible
      }.buildDir(pluginsDirPath.resolve("foo"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        disabledPlugins = setOf(PluginId.getId("foo")),
        productBuildNumber = BuildNumber.fromString("250.0")!!
      )

      val result = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Plugin should be excluded as incompatible (compatibility check happens first)
      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginUntilBuildConstraintViolation::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("foo")
    }
  }

  @Nested
  inner class IdConflictResolution {

    @Test
    fun `essential plugin wins over non-essential on ID conflict`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(
        essentialPlugins = setOf(PluginId.getId("foo")),
        discoveryResult = discoveryResult
      )

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].pluginId.idString).isEqualTo("foo")
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginDeclaresConflictingId::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("bar")
    }

    @Test
    fun `non-essential loses to essential on ID conflict`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(
        essentialPlugins = setOf(PluginId.getId("bar")),
        discoveryResult = discoveryResult
      )

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].pluginId.idString).isEqualTo("bar")
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginDeclaresConflictingId::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `both essential plugins with conflict are excluded`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(
        essentialPlugins = setOf(PluginId.getId("foo"), PluginId.getId("bar")),
        discoveryResult = discoveryResult
      )

      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `both non-essential plugins with conflict are excluded`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `plugin main ID conflicts with another plugin alias`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("foo")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `plugin declares same alias twice - self conflict`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("alias1", "alias1")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginDeclaresConflictingId::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `conflict on alias but not main ID`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("common", "foo-specific")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("common", "bar-specific")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      // Both plugins should be excluded due to conflict on "common" alias
      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginDeclaresConflictingId }).isTrue()

      // Verify the conflicting ID is "common"
      val reasons = excludedPlugins.map { it.reason as PluginDeclaresConflictingId }
      assertThat(reasons.all { it.conflictingId == PluginId.getId("common") }).isTrue()
    }

    @Test
    fun `content module with alias conflicts with plugin ID`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            pluginAlias("shared-id")
          }
        }
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared-id")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      // Both plugins should be excluded due to conflict
      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginDeclaresConflictingId }).isTrue()
    }
  }

  @Nested
  inner class DisabledPluginsAsEssentialDependencies {

    @Test
    fun `disabled plugin loaded when required by essential plugin`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        essentialPlugins = setOf(PluginId.getId("foo")),
        disabledPlugins = setOf(PluginId.getId("bar"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Both foo and bar should be loaded (bar is required by essential foo)
      assertThat(filteredResult.plugins).hasSize(2)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
      assertThat(excludedPlugins).isEmpty()
    }

    @Test
    fun `disabled plugin excluded when not required by essential plugin`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        essentialPlugins = setOf(PluginId.getId("foo")),
        disabledPlugins = setOf(PluginId.getId("bar"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Only foo should be loaded, bar is disabled and not required
      assertThat(filteredResult.plugins).hasSize(1)
      assertThat(filteredResult.plugins[0].pluginId.idString).isEqualTo("foo")
      
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsMarkedDisabled::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("bar")
    }

    @Test
    fun `mixed disabled plugins with essential dependencies`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))
      plugin("baz") { version = "1.0" }.buildDir(pluginsDirPath.resolve("baz"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        essentialPlugins = setOf(PluginId.getId("foo")),
        disabledPlugins = setOf(PluginId.getId("bar"), PluginId.getId("baz"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // foo and bar loaded (bar required by foo), baz excluded
      assertThat(filteredResult.plugins).hasSize(2)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
      
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsMarkedDisabled::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("baz")
    }
  }

  @Nested
  inner class IncompatibleWithEssentialPlugins {

    @Test
    fun `plugin excluded when essential declares incompatible-with it`() {
      plugin("foo") {
        version = "1.0"
        incompatibleWith = listOf("bar")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        essentialPlugins = setOf(PluginId.getId("foo"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Only foo should be loaded, bar is incompatible with essential foo
      assertThat(filteredResult.plugins).hasSize(1)
      assertThat(filteredResult.plugins[0].pluginId.idString).isEqualTo("foo")
      
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsIncompatibleWithAnotherPlugin::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("bar")
    }

    @Test
    fun `plugin loaded when required despite incompatible-with`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
        incompatibleWith = listOf("bar")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        essentialPlugins = setOf(PluginId.getId("foo"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Both foo and bar loaded (dependency wins over incompatibility)
      assertThat(filteredResult.plugins).hasSize(2)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
      assertThat(excludedPlugins).isEmpty()
    }

    @Test
    fun `incompatible-with from non-essential plugin is ignored`() {
      plugin("foo") {
        version = "1.0"
        incompatibleWith = listOf("bar")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext()

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Both loaded (only essential incompatibilities matter)
      assertThat(filteredResult.plugins).hasSize(2)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
      assertThat(excludedPlugins).isEmpty()
    }

    @Test
    fun `plugin excluded when essential declares incompatible-with plugin alias`() {
      plugin("foo") {
        version = "1.0"
        incompatibleWith = listOf("bar-alias")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("bar-alias")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        essentialPlugins = setOf(PluginId.getId("foo"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Only foo should be loaded, bar is incompatible (via alias resolution)
      assertThat(filteredResult.plugins).hasSize(1)
      assertThat(filteredResult.plugins[0].pluginId.idString).isEqualTo("foo")
      
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsIncompatibleWithAnotherPlugin::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("bar")
    }
  }

  @Nested
  inner class ExplicitPluginSubset {

    @Test
    fun `only explicitly configured plugins and their dependencies are loaded`() {
      plugin("foo") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("foo"))

      plugin("bar") {
        version = "1.0"
        depends("foo")
      }.buildDir(pluginsDirPath.resolve("bar"))

      plugin("baz") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("baz"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // bar and its dependency foo should be loaded
      assertThat(filteredResult.plugins).hasSize(2)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")

      // baz excluded as not required
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("baz")
    }

    @Test
    fun `essential plugins are always included in subset`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))
      plugin("baz") { version = "1.0" }.buildDir(pluginsDirPath.resolve("baz"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        essentialPlugins = setOf(PluginId.getId("foo")),
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // foo (essential) and bar (explicit) should be loaded
      assertThat(filteredResult.plugins).hasSize(2)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")

      // baz excluded
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("baz")
    }

    @Test
    fun `transitive dependencies are included in subset`() {
      plugin("a") { version = "1.0" }.buildDir(pluginsDirPath.resolve("a"))
      plugin("b") {
        version = "1.0"
        depends("a")
      }.buildDir(pluginsDirPath.resolve("b"))
      plugin("c") {
        version = "1.0"
        depends("b")
      }.buildDir(pluginsDirPath.resolve("c"))
      plugin("d") { version = "1.0" }.buildDir(pluginsDirPath.resolve("d"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        explicitPluginSubsetToLoad = setOf(PluginId.getId("c"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // c, b, and a should be loaded (transitive chain)
      assertThat(filteredResult.plugins).hasSize(3)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder("a", "b", "c")

      // d excluded
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("d")
    }

    @Test
    fun `disabled plugins are loaded when they are dependencies in explicit subset`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") {
        version = "1.0"
        depends("foo")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        disabledPlugins = setOf(PluginId.getId("foo")),
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Both bar and foo should be loaded (explicit subset does not care about disabled plugins)
      assertThat(filteredResult.plugins).hasSize(2)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")

      // No exclusions
      assertThat(excludedPlugins).isEmpty()
    }

    @Test
    fun `incompatible plugins are filtered before subset selection`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "100.*"
      }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar")),
        productBuildNumber = BuildNumber.fromString("250.0")!!
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Only bar should remain
      assertThat(filteredResult.plugins).hasSize(1)
      assertThat(filteredResult.plugins[0].pluginId.idString).isEqualTo("bar")

      // foo excluded as incompatible (not as "not required")
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginUntilBuildConstraintViolation::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `version selection happens before subset filtering`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      plugin("foo") { version = "2.0" }.buildDir(pluginsDirPath.resolve("foo_2-0"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar"))
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Only bar should remain
      assertThat(filteredResult.plugins).hasSize(1)
      assertThat(filteredResult.plugins[0].pluginId.idString).isEqualTo("bar")

      // Both foo versions excluded: 1.0 superseded, 2.0 not required
      assertThat(excludedPlugins).hasSize(2)
      val supersededExclusion = excludedPlugins.find { it.reason is PluginVersionIsSuperseded }
      val notRequiredExclusion = excludedPlugins.find { it.reason is PluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins }
      
      assertThat(supersededExclusion).isNotNull()
      assertThat(supersededExclusion!!.plugin.version).isEqualTo("1.0")
      
      assertThat(notRequiredExclusion).isNotNull()
      assertThat(notRequiredExclusion!!.plugin.version).isEqualTo("2.0")
    }


    @Test
    fun `empty explicit subset loads only essential plugins`() {
      plugin(PluginManagerCore.CORE_ID.idString) { version = "1.0" }.buildDir(pluginsDirPath.resolve("core"))
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(
        essentialPlugins = setOf(PluginId.getId("foo"), PluginManagerCore.CORE_ID),
        explicitPluginSubsetToLoad = emptySet()
      )

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // CORE and foo (essential) should be loaded
      assertThat(filteredResult.plugins).hasSize(2)
      assertThat(filteredResult.plugins.map { it.pluginId.idString }).containsExactlyInAnyOrder(PluginManagerCore.CORE_ID.idString, "foo")

      // bar excluded
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("bar")
    }
  }

  @Nested
  inner class DisablePluginLoadingCompletely {

    @Test
    fun `only CORE plugin is loaded when plugin loading is disabled`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(disablePluginLoadingCompletely = true)

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // No plugins should be loaded (CORE is not in our test set)
      assertThat(filteredResult.plugins).isEmpty()

      // All plugins excluded with PluginLoadingIsDisabledCompletely
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginLoadingIsDisabledCompletely }).isTrue()
      assertThat(excludedPlugins.map { it.plugin.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
    }

    @Test
    fun `CORE plugin is loaded when plugin loading is disabled`() {
      plugin(PluginManagerCore.CORE_ID.idString) { version = "1.0" }.buildDir(pluginsDirPath.resolve("core"))
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(disablePluginLoadingCompletely = true)

      val filteredResult = initContext.selectPluginsToLoad(
        discoveryResult.pluginLists,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      // Only CORE should be loaded
      assertThat(filteredResult.plugins).hasSize(1)
      assertThat(filteredResult.plugins[0].pluginId).isEqualTo(PluginManagerCore.CORE_ID)

      // Only foo excluded
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginLoadingIsDisabledCompletely::class.java)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `empty plugin list produces empty result when plugin loading is disabled`() {
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext(disablePluginLoadingCompletely = true)

      val filteredResult = initContext.selectPluginsToLoad(
        emptyList(),
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      assertThat(filteredResult.plugins).isEmpty()
      assertThat(excludedPlugins).isEmpty()
    }
  }

  @Nested
  inner class CompletePipeline {

    @Test
    fun `no conflicts produces valid UnambiguousPluginSet`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("bar-alias")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).hasSize(2)
      assertThat(excludedPlugins).isEmpty()

      // Verify resolution by main IDs
      assertThat(result.resolvePluginId(PluginId.getId("foo"))).isNotNull()
      assertThat(result.resolvePluginId(PluginId.getId("bar"))).isNotNull()

      // Verify resolution by aliases
      assertThat(result.resolvePluginId(PluginId.getId("foo-alias"))).isNotNull()
      assertThat(result.resolvePluginId(PluginId.getId("bar-alias"))).isNotNull()

      // Verify full mappings
      val fullMapping = result.buildFullPluginIdMapping()
      assertThat(fullMapping).hasSize(4) // 2 main IDs + 2 aliases
      assertThat(fullMapping).containsKeys(
        PluginId.getId("foo"),
        PluginId.getId("bar"),
        PluginId.getId("foo-alias"),
        PluginId.getId("bar-alias")
      )
    }

    @Test
    fun `resolvePluginId returns null for unknown ID`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, _) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.resolvePluginId(PluginId.getId("unknown"))).isNull()
    }

    @Test
    fun `multiple versions with conflicts - complex scenario`() {
      // foo has 2 versions, bar conflicts with foo via alias, baz is independent
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      
      plugin("foo") {
        version = "2.0"
        pluginAliases = listOf("foo-alias")
      }.buildDir(pluginsDirPath.resolve("foo_2-0"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.buildDir(pluginsDirPath.resolve("bar"))
      
      plugin("baz") { version = "1.0" }.buildDir(pluginsDirPath.resolve("baz"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      // Only baz should remain (foo versions conflict, bar conflicts with foo)
      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].pluginId.idString).isEqualTo("baz")

      // Should have excluded: foo 1.0 (superseded), foo 2.0 (conflict), bar (conflict)
      assertThat(excludedPlugins).hasSize(3)
      assertThat(excludedPlugins.any { it.reason is PluginVersionIsSuperseded }).isTrue()
      assertThat(excludedPlugins.any { it.reason is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `empty plugin list produces empty result`() {
      val discoveryResult = PluginsDiscoveryResult.build(emptyList())
      val (result, _) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).isEmpty()
      assertThat(result.buildFullPluginIdMapping()).isEmpty()
      assertThat(result.buildFullContentModuleIdMapping()).isEmpty()
    }

    @Test
    fun `single plugin with no conflicts`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).hasSize(1)
      assertThat(excludedPlugins).isEmpty()

      val fooPlugin = result.plugins[0]
      assertThat(fooPlugin.pluginId.idString).isEqualTo("foo")

      // Verify both main ID and alias resolve correctly
      assertThat(result.resolvePluginId(PluginId.getId("foo"))).isSameAs(fooPlugin)
      assertThat(result.resolvePluginId(PluginId.getId("foo-alias"))).isSameAs(fooPlugin)
    }

    @Test
    fun `three versions select newest compatible`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      plugin("foo") { version = "2.0" }.buildDir(pluginsDirPath.resolve("foo_2-0"))
      plugin("foo") { version = "3.0" }.buildDir(pluginsDirPath.resolve("foo_3-0"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].version).isEqualTo("3.0")
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginVersionIsSuperseded }).isTrue()
    }

    @Test
    fun `conflicting plugins - neither disabled results in conflict`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      
      // Neither plugin disabled
      val (result, excludedPlugins) = testPluginSelection(discoveryResult = discoveryResult)

      // Both should be excluded due to ID conflict
      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `conflicting plugins - one disabled allows other to load`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      
      // Disable foo, keep bar enabled
      val (result, excludedPlugins) = testPluginSelection(
        disabledPlugins = setOf(PluginId.getId("foo")),
        discoveryResult = discoveryResult
      )

      // bar should load successfully
      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].pluginId.idString).isEqualTo("bar")
      
      // foo should be excluded as disabled
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].plugin.pluginId.idString).isEqualTo("foo")
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginIsMarkedDisabled::class.java)
    }
  }
}
