// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Tests for the complete plugin initialization pipeline:
 * - Phase 1: Plugin selection ([PluginInitializationContext.selectPluginsToLoad])
 * - Phase 2: ID conflict resolution ([PluginInitializationContext.resolveIdConflicts])
 */
class PluginInitializationTest {
  
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
  ): PluginInitializationContext {
    return PluginInitializationContext.buildForTest(
      essentialPlugins = essentialPlugins,
      disabledPlugins = disabledPlugins,
      expiredPlugins = emptySet(),
      brokenPluginVersions = emptyMap(),
      getProductBuildNumber = { productBuildNumber },
      requirePlatformAliasDependencyForLegacyPlugins = false,
      checkEssentialPlugins = false,
      explicitPluginSubsetToLoad = null,
      disablePluginLoadingCompletely = false,
      currentProductModeId = "test"
    )
  }

  private fun testPhase1Only(
    productBuildNumber: BuildNumber = BuildNumber.fromString("241.0")!!,
    discoveryResult: PluginDescriptorLoadingResult,
  ): Pair<List<DiscoveredPluginsList>, MutableList<ExcludedPluginInfo>> {
    val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
    val initContext = createInitContext(productBuildNumber = productBuildNumber)

    val filteredResult = initContext.selectPluginsToLoad(
      discoveryResult.discoveredPlugins,
      onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
    )

    return filteredResult to excludedPlugins
  }

  private fun testBothPhases(
    essentialPlugins: Set<PluginId> = emptySet(),
    productBuildNumber: BuildNumber = BuildNumber.fromString("241.0")!!,
    discoveryResult: PluginDescriptorLoadingResult,
  ): Pair<UnambiguousPluginSet, MutableList<ExcludedPluginInfo>> {
    val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
    val initContext = createInitContext(essentialPlugins, emptySet(), productBuildNumber)

    // Phase 1: Plugin selection (disabled filtering, compatibility & version selection)
    val compatiblePlugins = initContext.selectPluginsToLoad(
      discoveryResult.discoveredPlugins,
      onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
    )

    // Phase 2: ID conflict resolution
    val result = initContext.resolveIdConflicts(
      compatiblePlugins = compatiblePlugins,
      onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
    )

    return result to excludedPlugins
  }

  @Nested
  inner class Phase1PluginSelection {

    @Test
    fun `select newer version when multiple versions exist`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      plugin("foo") { version = "2.0" }.buildDir(pluginsDirPath.resolve("foo_2-0"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (filteredResult, excludedPlugins) = testPhase1Only(discoveryResult = discoveryResult)

      assertThat(filteredResult).hasSize(1)
      assertThat(filteredResult[0].plugins).hasSize(1)
      assertThat(filteredResult[0].plugins[0].version).isEqualTo("2.0")
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
      val (filteredResult, excludedPlugins) = testPhase1Only(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
        discoveryResult = discoveryResult
      )

      assertThat(filteredResult).hasSize(1)
      assertThat(filteredResult[0].plugins).hasSize(1)
      assertThat(filteredResult[0].plugins[0].version).isEqualTo("1.0")
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
        DiscoveredPluginsList(customPlugins.discoveredPlugins[0].plugins, PluginsSourceContext.Custom),
        DiscoveredPluginsList(systemPlugins.discoveredPlugins[0].plugins, PluginsSourceContext.SystemPropertyProvided)
      )
      
      val filteredResult = initContext.selectPluginsToLoad(
        discoveredPlugins,
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      assertThat(filteredResult).hasSize(2)
      assertThat(filteredResult[0].plugins).hasSize(0)
      assertThat(filteredResult[1].plugins).hasSize(1)
      assertThat(filteredResult[1].plugins[0].version).isEqualTo("1.0")
      assertThat(excludedPlugins).hasSize(1)
      assertThat(excludedPlugins[0].reason).isInstanceOf(PluginVersionIsSuperseded::class.java)
      assertThat(excludedPlugins[0].plugin.version).isEqualTo("2.0")
    }

    @Test
    fun `three versions select newest compatible`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo_1-0"))
      plugin("foo") { version = "2.0" }.buildDir(pluginsDirPath.resolve("foo_2-0"))
      plugin("foo") { version = "3.0" }.buildDir(pluginsDirPath.resolve("foo_3-0"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (filteredResult, excludedPlugins) = testPhase1Only(discoveryResult = discoveryResult)

      assertThat(filteredResult).hasSize(1)
      assertThat(filteredResult[0].plugins).hasSize(1)
      assertThat(filteredResult[0].plugins[0].version).isEqualTo("3.0")
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
      val (filteredResult, excludedPlugins) = testPhase1Only(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
        discoveryResult = discoveryResult
      )

      assertThat(filteredResult).hasSize(1)
      assertThat(filteredResult[0].plugins).hasSize(1)
      assertThat(filteredResult[0].plugins[0].pluginId.idString).isEqualTo("foo")
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
      val (filteredResult, excludedPlugins) = testPhase1Only(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
        discoveryResult = discoveryResult
      )

      assertThat(filteredResult).hasSize(1)
      assertThat(filteredResult[0].plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginUntilBuildConstraintViolation }).isTrue()
    }

    @Test
    fun `empty plugin list produces empty result`() {
      val excludedPlugins = mutableListOf<ExcludedPluginInfo>()
      val initContext = createInitContext()
      
      val filteredResult = initContext.selectPluginsToLoad(
        emptyList(),
        onPluginExcluded = { plugin, reason -> excludedPlugins.add(ExcludedPluginInfo(plugin, reason)) }
      )

      assertThat(filteredResult).isEmpty()
      assertThat(excludedPlugins).isEmpty()
    }

    @Test
    fun `multiple plugins with different IDs all kept`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))
      plugin("baz") { version = "1.0" }.buildDir(pluginsDirPath.resolve("baz"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (filteredResult, excludedPlugins) = testPhase1Only(discoveryResult = discoveryResult)

      assertThat(filteredResult).hasSize(1)
      assertThat(filteredResult[0].plugins).hasSize(3)
      assertThat(filteredResult[0].plugins.map { it.pluginId.idString })
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
      val (filteredResult, excludedPlugins) = testPhase1Only(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
        discoveryResult = discoveryResult
      )

      // Only version 1.0 is compatible
      assertThat(filteredResult).hasSize(1)
      assertThat(filteredResult[0].plugins).hasSize(1)
      assertThat(filteredResult[0].plugins[0].version).isEqualTo("1.0")
      
      // Versions 2.0 and 3.0 are incompatible
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginUntilBuildConstraintViolation }).isTrue()
      assertThat(excludedPlugins.map { it.plugin.version }).containsExactlyInAnyOrder("2.0", "3.0")
    }
  }

  @Nested
  inner class Phase2IdConflictResolution {

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
      val (result, excludedPlugins) = testBothPhases(
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
      val (result, excludedPlugins) = testBothPhases(
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
      val (result, excludedPlugins) = testBothPhases(
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
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

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
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

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
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

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
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

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
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

      // Both plugins should be excluded due to conflict
      assertThat(result.plugins).isEmpty()
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginDeclaresConflictingId }).isTrue()
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
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

      assertThat(result.plugins).hasSize(2)
      assertThat(excludedPlugins).isEmpty()

      // Verify resolution by main IDs
      assertThat(result.resolvePluginId(PluginId.getId("foo"))).isNotNull()
      assertThat(result.resolvePluginId(PluginId.getId("bar"))).isNotNull()

      // Verify resolution by aliases
      assertThat(result.resolvePluginId(PluginId.getId("foo-alias"))).isNotNull()
      assertThat(result.resolvePluginId(PluginId.getId("bar-alias"))).isNotNull()

      // Verify full mappings
      val fullMapping = result.getFullPluginIdMapping()
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
      val (result, _) = testBothPhases(discoveryResult = discoveryResult)

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
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

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
      val discoveryResult = PluginDescriptorLoadingResult.build(emptyList())
      val (result, _) = testBothPhases(discoveryResult = discoveryResult)

      assertThat(result.plugins).isEmpty()
      assertThat(result.getFullPluginIdMapping()).isEmpty()
      assertThat(result.getFullContentModuleIdMapping()).isEmpty()
    }

    @Test
    fun `single plugin with no conflicts`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

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
      val (result, excludedPlugins) = testBothPhases(discoveryResult = discoveryResult)

      assertThat(result.plugins).hasSize(1)
      assertThat(result.plugins[0].version).isEqualTo("3.0")
      assertThat(excludedPlugins).hasSize(2)
      assertThat(excludedPlugins.all { it.reason is PluginVersionIsSuperseded }).isTrue()
    }
  }
}
