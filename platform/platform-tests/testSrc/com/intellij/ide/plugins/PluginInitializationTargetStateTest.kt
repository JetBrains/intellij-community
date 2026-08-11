// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.pluginSystem.testFramework.PseudoProductTestPluginInitContext
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.platform.testFramework.plugins.pluginAlias
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

/**
 * Tests for plugin initialization target-state computation: [PluginInitializationContext.computeTargetState].
 *
 * Candidate selection performs version selection, configured-subset filtering, and ID conflict resolution. Constraint resolution then
 * excludes disabled, incompatible, and otherwise invalid candidates.
 */
class PluginInitializationTargetStateTest {

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

  private inline fun <reified T : PluginIncompatibilityReason> DescriptorExclusionReason.hasIncompatibilityReason(): Boolean {
    return this is PluginIsIncompatibleWithProduct && incompatibilityReason is T
  }

  private inline fun <reified T : IntelliJImposedModuleExclusionReason> DescriptorExclusionReason.hasProductReason(): Boolean {
    return this is ProductRulesImposedExclusion && productReason is T
  }

  private fun createInitContext(
    essentialPlugins: Set<PluginId> = emptySet(),
    disabledPlugins: Set<PluginId> = emptySet(),
    productBuildNumber: BuildNumber = BuildNumber.fromString("241.0")!!,
    explicitPluginSubsetToLoad: Set<PluginId>? = null,
    disablePluginLoadingCompletely: Boolean = false,
  ): PluginInitializationContext {
    return object : PseudoProductTestPluginInitContext() {
      override val productBuildNumber: BuildNumber = productBuildNumber
      override val essentialPlugins: Set<PluginId> = essentialPlugins
      override fun isPluginDisabled(id: PluginId): Boolean = explicitPluginSubsetToLoad == null && id in disabledPlugins
      override val explicitPluginSubsetToLoad: Set<PluginId>? = explicitPluginSubsetToLoad
      override val disablePluginLoadingCompletely: Boolean = disablePluginLoadingCompletely
      override val currentProductModeId: String = ProductMode.MONOLITH.id
      override val environmentConfiguredModules: Map<PluginModuleId, PluginInitializationContext.EnvironmentConfiguredModuleData> =
        emptyMap()
      override val expiredPlugins: Set<PluginId> = emptySet()
    }
  }

  private fun discoverPlugins(path: Path = pluginsDirPath): PluginsDiscoveryResult {
    return PluginSetTestBuilder.fromPath(path).discoverPlugins().second
  }

  private fun computeTargetState(
    essentialPlugins: Set<PluginId> = emptySet(),
    disabledPlugins: Set<PluginId> = emptySet(),
    productBuildNumber: BuildNumber = BuildNumber.fromString("241.0")!!,
    explicitPluginSubsetToLoad: Set<PluginId>? = null,
    disablePluginLoadingCompletely: Boolean = false,
    discoveryResult: PluginsDiscoveryResult = discoverPlugins(),
  ): PluginSet {
    val initContext = createInitContext(
      essentialPlugins = essentialPlugins,
      disabledPlugins = disabledPlugins,
      productBuildNumber = productBuildNumber,
      explicitPluginSubsetToLoad = explicitPluginSubsetToLoad,
      disablePluginLoadingCompletely = disablePluginLoadingCompletely
    )
    return initContext.computeTargetState(discoveryResult, isStartupInit = false, parentActivity = null)
  }

  private val PluginSet.candidateSubset: UnambiguousPluginSet
    get() = resolvedPluginSet.candidateSet

  private fun PluginSet.getCandidatePlugin(id: String): PluginMainDescriptor {
    return candidateSubset.resolvePluginId(PluginId.getId(id)) as? PluginMainDescriptor
           ?: throw AssertionError("Candidate plugin '$id' not found")
  }

  @Nested
  inner class PluginSelection {

    @Test
    fun `select newer version when multiple versions exist`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("foo") { version = "2.0" }.installAt(pluginsDirPath)

      val result = computeTargetState()

      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].version).isEqualTo("2.0")
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()).isInstanceOf(PluginVersionIsSuperseded::class.java)
      assertThat(result.excludedFromCandidateSubset.keys.single().version).isEqualTo("1.0")
    }

    @Test
    fun `select older version when newer is incompatible`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "300.*"
      }.installAt(pluginsDirPath)

      plugin("foo") {
        version = "2.0"
        untilBuild = "200.*"
      }.installAt(pluginsDirPath)

      val result = computeTargetState(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
      )

      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].version).isEqualTo("1.0")
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()
                   .hasIncompatibilityReason<PluginIncompatibilityReason.UntilBuildConstraintViolation>()).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.single().version).isEqualTo("2.0")
    }

    @Test
    fun `SystemPropertyProvided source overrides regardless of version`() {
      val customPath = pluginsDirPath.resolve("custom")
      val systemPath = pluginsDirPath.resolve("system")

      plugin("foo") { version = "2.0" }.installAt(customPath)
      plugin("foo") { version = "1.0" }.installAt(systemPath)

      val customPlugins = PluginSetTestBuilder.fromPath(customPath).discoverPlugins().second
      val systemPlugins = PluginSetTestBuilder.fromPath(systemPath).discoverPlugins().second

      // Manually create discovery result with different sources
      val discoveredPlugins = PluginsDiscoveryResult.build(listOf(
        DiscoveredPluginsList(customPlugins.pluginLists[0].plugins, PluginsSourceContext.Custom),
        DiscoveredPluginsList(systemPlugins.pluginLists[0].plugins, PluginsSourceContext.SystemPropertyProvided)
      ))

      val result = computeTargetState(discoveryResult = discoveredPlugins)

      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].version).isEqualTo("1.0")
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()).isInstanceOf(PluginVersionIsSuperseded::class.java)
      assertThat(result.excludedFromCandidateSubset.keys.single().version).isEqualTo("2.0")
    }

    @Test
    fun `bundled plugin with lower version is superseded regardless of discovery order`() {
      val bundledPath = pluginsDirPath.resolve("bundled")
      val customPath = pluginsDirPath.resolve("custom")

      plugin("foo") { version = "1.0" }.installAt(bundledPath)
      plugin("foo") { version = "2.0" }.installAt(customPath)

      val bundledPlugins = PluginSetTestBuilder.fromPath(bundledPath).discoverPlugins().second
      val customPlugins = PluginSetTestBuilder.fromPath(customPath).discoverPlugins().second

      val bundledList = DiscoveredPluginsList(bundledPlugins.pluginLists[0].plugins, PluginsSourceContext.Bundled)
      val customList = DiscoveredPluginsList(customPlugins.pluginLists[0].plugins, PluginsSourceContext.Custom)

      fun assertBundledIsSuperseded(discoveryResult: List<DiscoveredPluginsList>) {
        val discoveryResult = PluginsDiscoveryResult.build(discoveryResult)
        val result = computeTargetState(discoveryResult = discoveryResult)

        assertThat(result.candidateSubset.plugins).hasSize(1)
        assertThat(result.candidateSubset.plugins[0].version).isEqualTo("2.0")
        assertThat(result.excludedFromCandidateSubset).hasSize(1)
        assertThat(result.excludedFromCandidateSubset.values.single()).isInstanceOf(PluginVersionIsSuperseded::class.java)
        assertThat(result.excludedFromCandidateSubset.keys.single().version).isEqualTo("1.0")
      }

      assertBundledIsSuperseded(listOf(bundledList, customList))
      assertBundledIsSuperseded(listOf(customList, bundledList))
    }

    @Test
    fun `selected versions retain discovery order`() {
      val firstPath = pluginsDirPath.resolve("first")
      val middlePath = pluginsDirPath.resolve("middle")
      val lastPath = pluginsDirPath.resolve("last")
      plugin("foo") { version = "1.0" }.installAt(firstPath)
      plugin("bar") { version = "1.0" }.installAt(middlePath)
      plugin("foo") { version = "2.0" }.installAt(lastPath)

      val foo1 = PluginSetTestBuilder.fromPath(firstPath).discoverPlugins().second.pluginLists.single().plugins.single()
      val bar = PluginSetTestBuilder.fromPath(middlePath).discoverPlugins().second.pluginLists.single().plugins.single()
      val foo2 = PluginSetTestBuilder.fromPath(lastPath).discoverPlugins().second.pluginLists.single().plugins.single()
      val discoveryResult = PluginsDiscoveryResult.build(
        listOf(DiscoveredPluginsList(listOf(foo1, bar, foo2), PluginsSourceContext.Custom))
      )

      val result = computeTargetState(discoveryResult = discoveryResult)

      assertThat(result.candidateSubset.plugins.map { it.pluginId.idString }).containsExactly("bar", "foo")
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.keys.single()).isSameAs(foo1)
    }

    @Test
    fun `three versions select newest compatible`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("foo") { version = "2.0" }.installAt(pluginsDirPath)
      plugin("foo") { version = "3.0" }.installAt(pluginsDirPath)

      val result = computeTargetState()

      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].version).isEqualTo("3.0")
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      assertThat(result.excludedFromCandidateSubset.values.all { it is PluginVersionIsSuperseded }).isTrue()
    }

    @Test
    fun `incompatible plugin remains a candidate and is excluded by constraints`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "300.*"
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        untilBuild = "100.*"
      }.installAt(pluginsDirPath)

      val result = computeTargetState(productBuildNumber = BuildNumber.fromString("250.0")!!)
      val foo = result.getCandidatePlugin("foo")
      val bar = result.getCandidatePlugin("bar")

      assertThat(result.candidateSubset.plugins).hasSize(2)
      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.resolvedPluginSet.isResolved(foo)).isTrue()
      assertThat(result.resolvedPluginSet.isExcluded(bar)).isTrue()
      assertThat(
        result.resolvedPluginSet.getExclusionReason(bar)!!
          .hasIncompatibilityReason<PluginIncompatibilityReason.UntilBuildConstraintViolation>()
      ).isTrue()
    }

    @Test
    fun `all incompatible plugins remain candidates and are excluded by constraints`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "100.*"
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        untilBuild = "100.*"
      }.installAt(pluginsDirPath)

      val result = computeTargetState(productBuildNumber = BuildNumber.fromString("250.0")!!)

      assertThat(result.candidateSubset.plugins).hasSize(2)
      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.candidateSubset.plugins.none(result.resolvedPluginSet::isResolved)).isTrue()
      assertThat(result.candidateSubset.plugins.all {
        result.resolvedPluginSet.getExclusionReason(it)!!
          .hasIncompatibilityReason<PluginIncompatibilityReason.UntilBuildConstraintViolation>()
      }).isTrue()
    }

    @Test
    fun `empty plugin list produces empty result`() {
      val result = computeTargetState(discoveryResult = PluginsDiscoveryResult.build(emptyList()))

      assertThat(result.candidateSubset.plugins).isEmpty()
      assertThat(result.candidateSubset.buildFullPluginIdMapping()).isEmpty()
      assertThat(result.candidateSubset.buildFullContentModuleIdMapping()).isEmpty()
      assertThat(result.excludedFromCandidateSubset).isEmpty()
    }

    @Test
    fun `multiple plugins with different IDs all kept`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("baz") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState()

      assertThat(result.candidateSubset.plugins).hasSize(3)
      assertThat(result.candidateSubset.plugins.map { it.pluginId.idString })
        .containsExactlyInAnyOrder("foo", "bar", "baz")
      assertThat(result.excludedFromCandidateSubset).isEmpty()
    }

    @Test
    fun `mixed compatible and incompatible versions`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "300.*"
      }.installAt(pluginsDirPath)

      plugin("foo") {
        version = "2.0"
        untilBuild = "200.*"
      }.installAt(pluginsDirPath)

      plugin("foo") {
        version = "3.0"
        untilBuild = "100.*"
      }.installAt(pluginsDirPath)

      val result = computeTargetState(
        productBuildNumber = BuildNumber.fromString("250.0")!!,
      )

      // Only version 1.0 is compatible
      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].version).isEqualTo("1.0")

      // Versions 2.0 and 3.0 are incompatible
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      assertThat(result.excludedFromCandidateSubset.values.all {
        it.hasIncompatibilityReason<PluginIncompatibilityReason.UntilBuildConstraintViolation>()
      }).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.map { it.version }).containsExactlyInAnyOrder("2.0", "3.0")
    }

    @Test
    fun `incompatible plugin is excluded before disabled check during constraint resolution`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "100.*" // incompatible
      }.installAt(pluginsDirPath)

      val result = computeTargetState(
        disabledPlugins = setOf(PluginId.getId("foo")),
        productBuildNumber = BuildNumber.fromString("250.0")!!
      )
      val plugin = result.candidateSubset.plugins.single()

      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.resolvedPluginSet.isExcluded(plugin)).isTrue()
      assertThat(
        result.resolvedPluginSet.getExclusionReason(plugin)!!
          .hasIncompatibilityReason<PluginIncompatibilityReason.UntilBuildConstraintViolation>()
      ).isTrue()
    }
  }

  @Nested
  inner class IdConflictResolution {

    @Test
    fun `essential plugin wins over non-essential on ID conflict`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      val result = computeTargetState(
        essentialPlugins = setOf(PluginId.getId("foo")),
      )

      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].pluginId.idString).isEqualTo("foo")
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()).isInstanceOf(PluginDeclaresConflictingId::class.java)
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("bar")
    }

    @Test
    fun `non-essential loses to essential on ID conflict`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      val result = computeTargetState(
        essentialPlugins = setOf(PluginId.getId("bar")),
      )

      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].pluginId.idString).isEqualTo("bar")
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()).isInstanceOf(PluginDeclaresConflictingId::class.java)
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `both essential plugins with conflict are excluded`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      val result = computeTargetState(
        essentialPlugins = setOf(PluginId.getId("foo"), PluginId.getId("bar")),
      )

      assertThat(result.candidateSubset.plugins).isEmpty()
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      assertThat(result.excludedFromCandidateSubset.values.all { it is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `both non-essential plugins with conflict are excluded`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      val result = computeTargetState()

      assertThat(result.candidateSubset.plugins).isEmpty()
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      assertThat(result.excludedFromCandidateSubset.values.all { it is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `plugin main ID conflicts with another plugin alias`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("foo")
      }.installAt(pluginsDirPath)

      val result = computeTargetState()

      assertThat(result.candidateSubset.plugins).isEmpty()
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      assertThat(result.excludedFromCandidateSubset.values.all { it is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `plugin declares same alias twice - self conflict`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("alias1", "alias1")
      }.installAt(pluginsDirPath)

      val result = computeTargetState()

      assertThat(result.candidateSubset.plugins).isEmpty()
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()).isInstanceOf(PluginDeclaresConflictingId::class.java)
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `conflict on alias but not main ID`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("common", "foo-specific")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("common", "bar-specific")
      }.installAt(pluginsDirPath)

      val result = computeTargetState()

      // Both plugins should be excluded due to conflict on "common" alias
      assertThat(result.candidateSubset.plugins).isEmpty()
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      assertThat(result.excludedFromCandidateSubset.values.all { it is PluginDeclaresConflictingId }).isTrue()

      // Verify the conflicting ID is "common"
      val reasons = result.excludedFromCandidateSubset.values.map { it as PluginDeclaresConflictingId }
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
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared-id")
      }.installAt(pluginsDirPath)

      val result = computeTargetState()

      // Both plugins should be excluded due to conflict
      assertThat(result.candidateSubset.plugins).isEmpty()
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      assertThat(result.excludedFromCandidateSubset.values.all { it is PluginDeclaresConflictingId }).isTrue()
    }
  }

  @Nested
  inner class DisabledPluginsAsEssentialDependencies {

    @Test
    fun `disabled plugin loaded when required by essential plugin`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
      }.installAt(pluginsDirPath)

      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(
        essentialPlugins = setOf(PluginId.getId("foo")),
        disabledPlugins = setOf(PluginId.getId("bar"))
      )

      assertThat(result).hasExactlyEnabledPlugins("foo", "bar")
      assertThat(result.excludedFromCandidateSubset).isEmpty()
    }

    @Test
    fun `disabled plugin remains a candidate and is excluded when not required by essential plugin`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(
        essentialPlugins = setOf(PluginId.getId("foo")),
        disabledPlugins = setOf(PluginId.getId("bar"))
      )
      val foo = result.getCandidatePlugin("foo")
      val bar = result.getCandidatePlugin("bar")

      assertThat(result.candidateSubset.plugins).hasSize(2)
      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.resolvedPluginSet.isResolved(foo)).isTrue()
      assertThat(result.resolvedPluginSet.isExcluded(bar)).isTrue()
      assertThat(result.resolvedPluginSet.getExclusionReason(bar)).isInstanceOf(PluginIsMarkedDisabled::class.java)
    }

    @Test
    fun `mixed disabled plugins with essential dependencies`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
      }.installAt(pluginsDirPath)

      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("baz") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(
        essentialPlugins = setOf(PluginId.getId("foo")),
        disabledPlugins = setOf(PluginId.getId("bar"), PluginId.getId("baz"))
      )
      val foo = result.getCandidatePlugin("foo")
      val bar = result.getCandidatePlugin("bar")
      val baz = result.getCandidatePlugin("baz")

      assertThat(result.candidateSubset.plugins).hasSize(3)
      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.resolvedPluginSet.isResolved(foo)).isTrue()
      assertThat(result.resolvedPluginSet.isResolved(bar)).isTrue()
      assertThat(result.resolvedPluginSet.isExcluded(baz)).isTrue()
      assertThat(result.resolvedPluginSet.getExclusionReason(baz)).isInstanceOf(PluginIsMarkedDisabled::class.java)
    }
  }

  @Nested
  inner class IncompatibleWithEssentialPlugins {

    @Test
    fun `plugin remains a candidate and is excluded when essential declares incompatible-with it`() {
      plugin("foo") {
        version = "1.0"
        incompatibleWith = listOf("bar")
      }.installAt(pluginsDirPath)

      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(essentialPlugins = setOf(PluginId.getId("foo")))
      val foo = result.getCandidatePlugin("foo")
      val bar = result.getCandidatePlugin("bar")

      assertThat(result.candidateSubset.plugins).hasSize(2)
      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.resolvedPluginSet.isResolved(foo)).isTrue()
      assertThat(result.resolvedPluginSet.isExcluded(bar)).isTrue()
      assertThat(result.resolvedPluginSet.getExclusionReason(bar)).isInstanceOf(IncompatibleWithAnotherModule::class.java)
    }

    @Test
    fun `required plugin remains a candidate despite incompatible-with`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
        incompatibleWith = listOf("bar")
      }.installAt(pluginsDirPath)

      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(essentialPlugins = setOf(PluginId.getId("foo")))
      val foo = result.getCandidatePlugin("foo")
      val bar = result.getCandidatePlugin("bar")

      assertThat(result.candidateSubset.plugins).containsExactlyInAnyOrder(foo, bar)
      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.resolvedPluginSet.isExcluded(foo)).isTrue()
      assertThat(result.resolvedPluginSet.getExclusionReason(bar)).isInstanceOf(IncompatibleWithAnotherModule::class.java)
    }

    @Test
    fun `incompatible-with from non-essential plugin does not affect candidate selection`() {
      plugin("foo") {
        version = "1.0"
        incompatibleWith = listOf("bar")
      }.installAt(pluginsDirPath)

      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState()
      val foo = result.getCandidatePlugin("foo")
      val bar = result.getCandidatePlugin("bar")

      assertThat(result.candidateSubset.plugins).containsExactlyInAnyOrder(foo, bar)
      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.resolvedPluginSet.getExclusionReason(foo)).isInstanceOf(IncompatibleWithAnotherModule::class.java)
      assertThat(result.resolvedPluginSet.isResolved(bar)).isTrue()
    }

    @Test
    fun `plugin remains a candidate and is excluded when essential declares incompatible-with plugin alias`() {
      plugin("foo") {
        version = "1.0"
        incompatibleWith = listOf("bar-alias")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("bar-alias")
      }.installAt(pluginsDirPath)

      val result = computeTargetState(essentialPlugins = setOf(PluginId.getId("foo")))
      val foo = result.getCandidatePlugin("foo")
      val bar = result.getCandidatePlugin("bar")

      assertThat(result.candidateSubset.plugins).hasSize(2)
      assertThat(result.excludedFromCandidateSubset).isEmpty()
      assertThat(result.resolvedPluginSet.isResolved(foo)).isTrue()
      assertThat(result.resolvedPluginSet.isExcluded(bar)).isTrue()
      assertThat(result.resolvedPluginSet.getExclusionReason(bar)).isInstanceOf(IncompatibleWithAnotherModule::class.java)
    }
  }

  @Nested
  inner class ExplicitPluginSubset {

    @Test
    fun `only explicitly configured plugins and their dependencies are loaded`() {
      plugin("foo") {
        version = "1.0"
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        depends("foo")
      }.installAt(pluginsDirPath)

      plugin("baz") {
        version = "1.0"
      }.installAt(pluginsDirPath)

      val result = computeTargetState(
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar"))
      )

      assertThat(result).hasExactlyEnabledPlugins("foo", "bar")

      // baz excluded as not required
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()
                   .hasProductReason<PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading>()).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("baz")
    }

    @Test
    fun `essential plugins are always included in subset`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("baz") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(
        essentialPlugins = setOf(PluginId.getId("foo")),
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar"))
      )

      assertThat(result).hasExactlyEnabledPlugins("foo", "bar")

      // baz excluded
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()
                   .hasProductReason<PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading>()).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("baz")
    }

    @Test
    fun `transitive dependencies are included in subset`() {
      plugin("a") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("b") {
        version = "1.0"
        depends("a")
      }.installAt(pluginsDirPath)
      plugin("c") {
        version = "1.0"
        depends("b")
      }.installAt(pluginsDirPath)
      plugin("d") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(
        explicitPluginSubsetToLoad = setOf(PluginId.getId("c"))
      )

      assertThat(result).hasExactlyEnabledPlugins("a", "b", "c")

      // d excluded
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()
                   .hasProductReason<PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading>()).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("d")
    }

    @Test
    fun `disabled plugins are loaded when they are dependencies in explicit subset`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("bar") {
        version = "1.0"
        depends("foo")
      }.installAt(pluginsDirPath)

      val result = computeTargetState(
        disabledPlugins = setOf(PluginId.getId("foo")),
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar"))
      )

      assertThat(result).hasExactlyEnabledPlugins("foo", "bar")
      assertThat(result.excludedFromCandidateSubset).isEmpty()
    }

    @Test
    fun `incompatible plugin outside explicit subset is excluded by subset selection`() {
      plugin("foo") {
        version = "1.0"
        untilBuild = "100.*"
      }.installAt(pluginsDirPath)
      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar")),
        productBuildNumber = BuildNumber.fromString("250.0")!!
      )

      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins.single().pluginId.idString).isEqualTo("bar")
      assertThat(result.resolvedPluginSet.isResolved(result.candidateSubset.plugins.single())).isTrue()
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()
                   .hasProductReason<PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading>()).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `version selection happens before subset filtering`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("foo") { version = "2.0" }.installAt(pluginsDirPath)
      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(
        explicitPluginSubsetToLoad = setOf(PluginId.getId("bar"))
      )

      // Only bar should remain
      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].pluginId.idString).isEqualTo("bar")

      // Both foo versions excluded: 1.0 superseded, 2.0 not required
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      val supersededExclusion = result.excludedFromCandidateSubset.entries.find { it.value is PluginVersionIsSuperseded }
      val notRequiredExclusion = result.excludedFromCandidateSubset.entries.find {
        it.value.hasProductReason<PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading>()
      }

      assertThat(supersededExclusion).isNotNull()
      assertThat(supersededExclusion!!.key.version).isEqualTo("1.0")

      assertThat(notRequiredExclusion).isNotNull()
      assertThat(notRequiredExclusion!!.key.version).isEqualTo("2.0")
    }


    @Test
    fun `empty explicit subset loads only essential plugins`() {
      plugin(PluginManagerCore.CORE_ID.idString) { version = "1.0" }.installAt(pluginsDirPath)
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(
        essentialPlugins = setOf(PluginId.getId("foo"), PluginManagerCore.CORE_ID),
        explicitPluginSubsetToLoad = emptySet()
      )

      assertThat(result).hasExactlyEnabledPlugins(PluginManagerCore.CORE_ID.idString, "foo")

      // bar excluded
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()
                   .hasProductReason<PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading>()).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("bar")
    }
  }

  @Nested
  inner class DisablePluginLoadingCompletely {

    @Test
    fun `only CORE plugin is loaded when plugin loading is disabled`() {
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(disablePluginLoadingCompletely = true)

      // No plugins should be loaded (CORE is not in our test set)
      assertThat(result.enabledPlugins).isEmpty()

      // All plugins are excluded because plugin loading is disabled
      assertThat(result.excludedFromCandidateSubset).hasSize(2)
      assertThat(result.excludedFromCandidateSubset.values.all {
        it.hasProductReason<PluginLoadingIsDisabledCompletelyExceptCore>()
      }).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
    }

    @Test
    fun `CORE plugin is loaded when plugin loading is disabled`() {
      plugin(PluginManagerCore.CORE_ID.idString) { version = "1.0" }.installAt(pluginsDirPath)
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(disablePluginLoadingCompletely = true)

      assertThat(result).hasExactlyEnabledPlugins(PluginManagerCore.CORE_ID.idString)

      // Only foo excluded
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.values.single()
                   .hasProductReason<PluginLoadingIsDisabledCompletelyExceptCore>()).isTrue()
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `empty plugin list produces empty result when plugin loading is disabled`() {
      val result = computeTargetState(
        disablePluginLoadingCompletely = true,
        discoveryResult = PluginsDiscoveryResult.build(emptyList()),
      )

      assertThat(result.enabledPlugins).isEmpty()
      assertThat(result.excludedFromCandidateSubset).isEmpty()
    }
  }

  @Nested
  inner class ResultViewsAndAdaptation {

    @Test
    fun `no conflicts produces valid UnambiguousPluginSet`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("bar-alias")
      }.installAt(pluginsDirPath)

      val result = computeTargetState()
      val candidateSubset = result.candidateSubset

      assertThat(candidateSubset.plugins).hasSize(2)
      assertThat(result.excludedFromCandidateSubset).isEmpty()

      // Verify resolution by main IDs
      assertThat(candidateSubset.resolvePluginId(PluginId.getId("foo"))).isNotNull()
      assertThat(candidateSubset.resolvePluginId(PluginId.getId("bar"))).isNotNull()

      // Verify resolution by aliases
      assertThat(candidateSubset.resolvePluginId(PluginId.getId("foo-alias"))).isNotNull()
      assertThat(candidateSubset.resolvePluginId(PluginId.getId("bar-alias"))).isNotNull()

      // Verify full mappings
      val fullMapping = candidateSubset.buildFullPluginIdMapping()
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
      plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState()

      assertThat(result.candidateSubset.resolvePluginId(PluginId.getId("unknown"))).isNull()
    }

    @Test
    fun `multiple versions with conflicts - complex scenario`() {
      // foo has 2 versions, bar conflicts with foo via alias, baz is independent
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.installAt(pluginsDirPath)

      plugin("foo") {
        version = "2.0"
        pluginAliases = listOf("foo-alias")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.installAt(pluginsDirPath)

      plugin("baz") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState()

      // Only baz should remain (foo versions conflict, bar conflicts with foo)
      assertThat(result.candidateSubset.plugins).hasSize(1)
      assertThat(result.candidateSubset.plugins[0].pluginId.idString).isEqualTo("baz")

      // Should have excluded: foo 1.0 (superseded), foo 2.0 (conflict), bar (conflict)
      assertThat(result.excludedFromCandidateSubset).hasSize(3)
      assertThat(result.excludedFromCandidateSubset.values.any { it is PluginVersionIsSuperseded }).isTrue()
      assertThat(result.excludedFromCandidateSubset.values.any { it is PluginDeclaresConflictingId }).isTrue()
    }

    @Test
    fun `single plugin with no conflicts`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.installAt(pluginsDirPath)

      val result = computeTargetState()
      val candidateSubset = result.candidateSubset

      assertThat(candidateSubset.plugins).hasSize(1)
      assertThat(result.excludedFromCandidateSubset).isEmpty()

      val fooPlugin = candidateSubset.plugins[0]
      assertThat(fooPlugin.pluginId.idString).isEqualTo("foo")

      // Verify both main ID and alias resolve correctly
      assertThat(candidateSubset.resolvePluginId(PluginId.getId("foo"))).isSameAs(fooPlugin)
      assertThat(candidateSubset.resolvePluginId(PluginId.getId("foo-alias"))).isSameAs(fooPlugin)
    }

    @Test
    fun `conflicting plugins - one disabled allows other to load`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared")
      }.installAt(pluginsDirPath)

      val result = computeTargetState(disabledPlugins = setOf(PluginId.getId("foo")))

      assertThat(result).hasExactlyEnabledPlugins("bar")

      // foo should be excluded as disabled
      assertThat(result.excludedFromCandidateSubset).hasSize(1)
      assertThat(result.excludedFromCandidateSubset.keys.single().pluginId.idString).isEqualTo("foo")
      assertThat(result.excludedFromCandidateSubset.values.single()).isInstanceOf(PluginIsMarkedDisabled::class.java)
    }

    @Test
    fun `dependency on disabled candidate retains disabled dependency error`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
      }.installAt(pluginsDirPath)
      plugin("bar") { version = "1.0" }.installAt(pluginsDirPath)

      val result = computeTargetState(disabledPlugins = setOf(PluginId.getId("bar")))
      val nonLoadReasons = mutableListOf<PluginNonLoadReason>()

      PluginManagerCore.adaptDescriptorExclusionReasonAsPluginNonLoadReason(
        resolvedPluginSet = result.resolvedPluginSet,
        registerLoadingError = nonLoadReasons::add,
      )

      assertThat(nonLoadReasons).hasSize(1)
      assertThat(nonLoadReasons.single()).isInstanceOf(PluginDependencyIsDisabled::class.java)
      assertThat((nonLoadReasons.single() as PluginDependencyIsDisabled).dependencyId).isEqualTo(PluginId.getId("bar"))
    }
  }
}
