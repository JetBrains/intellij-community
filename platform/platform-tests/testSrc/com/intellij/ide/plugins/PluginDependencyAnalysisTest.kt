// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.buildDir
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Tests for [PluginDependencyAnalysis] utility functions.
 */
class PluginDependencyAnalysisTest {

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

  private fun createInitContext(
    essentialPlugins: Set<PluginId> = emptySet(),
    environmentConfiguredModules: Map<PluginModuleId, PluginInitializationContext.EnvironmentConfiguredModuleData> = emptyMap(),
  ): PluginInitializationContext {
    return object : PluginInitializationContext {
      override val productBuildNumber: BuildNumber = BuildNumber.fromString("241.0")!!
      override val essentialPlugins: Set<PluginId> = essentialPlugins
      override fun isPluginDisabled(id: PluginId): Boolean = false
      override fun isPluginExpired(id: PluginId): Boolean = false
      override fun isPluginBroken(id: PluginId, version: String?): Boolean = false
      override val requirePlatformAliasDependencyForLegacyPlugins: Boolean = false
      override val checkEssentialPlugins: Boolean = false
      override val explicitPluginSubsetToLoad: Set<PluginId>? = null
      override val disablePluginLoadingCompletely: Boolean = false
      override val pluginsPerProjectConfig: PluginsPerProjectConfig? = null
      override val currentProductModeId: String = "test"
      override val environmentConfiguredModules: Map<PluginModuleId, PluginInitializationContext.EnvironmentConfiguredModuleData> = environmentConfiguredModules
    }
  }

  private fun discoverPlugins(): List<PluginMainDescriptor> {
    val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
    return discoveryResult.pluginLists.flatMap { it.plugins }
  }

  @Nested
  inner class BFSTests {

    @Test
    fun `single node traversal`() {
      val visited = mutableListOf<String>()
      val bfs = object : PluginDependencyAnalysis.BFS<String>() {
        override fun visit(node: String) {
          visited.add(node)
        }
      }

      bfs.schedule("A")
      bfs.run()

      assertThat(visited).containsExactly("A")
      assertThat(bfs.isVisited("A")).isTrue()
      assertThat(bfs.getVisitedSet()).containsExactly("A")
    }

    @Test
    fun `multi-node traversal with correct visit order`() {
      val visited = mutableListOf<String>()
      val bfs = object : PluginDependencyAnalysis.BFS<String>() {
        override fun visit(node: String) {
          visited.add(node)
          // Schedule children
          when (node) {
            "A" -> {
              schedule("B")
              schedule("C")
            }
            "B" -> schedule("D")
            "C" -> schedule("E")
          }
        }
      }

      bfs.schedule("A")
      bfs.run()

      // BFS order: A -> B, C -> D, E
      assertThat(visited).containsExactly("A", "B", "C", "D", "E")
      assertThat(bfs.getVisitedSet()).containsExactlyInAnyOrder("A", "B", "C", "D", "E")
    }

    @Test
    fun `already visited nodes are not revisited`() {
      val visitCount = mutableMapOf<String, Int>()
      val bfs = object : PluginDependencyAnalysis.BFS<String>() {
        override fun visit(node: String) {
          visitCount[node] = visitCount.getOrDefault(node, 0) + 1
          when (node) {
            "A" -> {
              schedule("B")
              schedule("C")
            }
            "B" -> schedule("D")
            "C" -> schedule("D") // D scheduled twice
          }
        }
      }

      bfs.schedule("A")
      bfs.run()

      // Each node should be visited exactly once
      assertThat(visitCount["A"]).isEqualTo(1)
      assertThat(visitCount["B"]).isEqualTo(1)
      assertThat(visitCount["C"]).isEqualTo(1)
      assertThat(visitCount["D"]).isEqualTo(1)
    }

    @Test
    fun `scheduling same node multiple times only visits once`() {
      val visited = mutableListOf<String>()
      val bfs = object : PluginDependencyAnalysis.BFS<String>() {
        override fun visit(node: String) {
          visited.add(node)
        }
      }

      bfs.schedule("A")
      bfs.schedule("A")
      bfs.schedule("A")
      bfs.run()

      assertThat(visited).containsExactly("A")
    }

    @Test
    fun `empty graph - no scheduled nodes`() {
      val visited = mutableListOf<String>()
      val bfs = object : PluginDependencyAnalysis.BFS<String>() {
        override fun visit(node: String) {
          visited.add(node)
        }
      }

      bfs.run()

      assertThat(visited).isEmpty()
      assertThat(bfs.getVisitedSet()).isEmpty()
    }

    @Test
    fun `complex graph with cycles handled correctly`() {
      val visited = mutableListOf<String>()
      val bfs = object : PluginDependencyAnalysis.BFS<String>() {
        override fun visit(node: String) {
          visited.add(node)
          when (node) {
            "A" -> {
              schedule("B")
              schedule("C")
            }
            "B" -> {
              schedule("C")
              schedule("D")
            }
            "C" -> {
              schedule("A") // cycle back to A
              schedule("D")
            }
            "D" -> schedule("B") // cycle back to B
          }
        }
      }

      bfs.schedule("A")
      bfs.run()

      // Each node visited exactly once despite cycles
      assertThat(visited).containsExactly("A", "B", "C", "D")
      assertThat(bfs.getVisitedSet()).containsExactlyInAnyOrder("A", "B", "C", "D")
    }

    @Test
    fun `schedule returns true for new nodes and false for visited`() {
      val bfs = object : PluginDependencyAnalysis.BFS<String>() {
        override fun visit(node: String) {
          // no-op
        }
      }

      assertThat(bfs.schedule("A")).isTrue() // first time
      assertThat(bfs.schedule("B")).isTrue() // first time
      
      bfs.run()
      
      assertThat(bfs.schedule("A")).isFalse() // already visited
      assertThat(bfs.schedule("B")).isFalse() // already visited
      assertThat(bfs.schedule("C")).isTrue() // new node
    }

    @Test
    fun `visited set preserves insertion order`() {
      val bfs = object : PluginDependencyAnalysis.BFS<String>() {
        override fun visit(node: String) {
          when (node) {
            "A" -> {
              schedule("B")
              schedule("C")
            }
            "B" -> schedule("D")
          }
        }
      }

      bfs.schedule("A")
      bfs.run()

      val visitedList = bfs.getVisitedSet().toList()
      assertThat(visitedList).containsExactly("A", "B", "C", "D")
    }
  }

  @Nested
  inner class SequenceRequiredModulesTests {

    @Test
    fun `plugin with no content modules returns only main descriptor`() {
      plugin("foo") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      
      val result = PluginDependencyAnalysis.sequenceRequiredModules(initContext, plugins[0]).toList()

      assertThat(result).hasSize(1)
      assertThat(result[0]).isInstanceOf(PluginMainDescriptor::class.java)
      assertThat(result[0].pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `plugin with required content modules includes all required`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.module1", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
          module("foo.module2", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      
      val result = PluginDependencyAnalysis.sequenceRequiredModules(initContext, plugins[0]).toList()

      assertThat(result).hasSize(3) // main + 2 content modules
      assertThat(result[0]).isInstanceOf(PluginMainDescriptor::class.java)
      assertThat(result[1]).isInstanceOf(ContentModuleDescriptor::class.java)
      assertThat(result[2]).isInstanceOf(ContentModuleDescriptor::class.java)
    }

    @Test
    fun `plugin with optional content modules excludes optional`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
          module("foo.optional", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      
      val result = PluginDependencyAnalysis.sequenceRequiredModules(initContext, plugins[0]).toList()

      assertThat(result).hasSize(2) // main + 1 required module
      assertThat(result[0]).isInstanceOf(PluginMainDescriptor::class.java)
      assertThat(result[1]).isInstanceOf(ContentModuleDescriptor::class.java)
      assertThat((result[1] as ContentModuleDescriptor).moduleId.name).isEqualTo("foo.required")
    }

    @Test
    fun `plugin with mixed required and optional modules`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.required1", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
          module("foo.optional1", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
          module("foo.embedded2", loadingRule = ModuleLoadingRuleValue.EMBEDDED) {}
          module("foo.optional2", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      
      val result = PluginDependencyAnalysis.sequenceRequiredModules(initContext, plugins[0]).toList()

      assertThat(result).hasSize(3) // main + 2 required modules
      val contentModules = result.filterIsInstance<ContentModuleDescriptor>()
      assertThat(contentModules).hasSize(2)
      assertThat(contentModules.map { it.moduleId.name }).containsExactlyInAnyOrder("foo.required1", "foo.embedded2")
    }

    @Test
    fun `plugin with requiredIfAvailable when target is available becomes required`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.conditional", loadingRule = ModuleLoadingRuleValue.OPTIONAL, requiredIfAvailable = "target.module") {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val targetModuleId = PluginModuleId("target.module", "namespace")
      val initContext = createInitContext(
        environmentConfiguredModules = mapOf(
          targetModuleId to PluginInitializationContext.EnvironmentConfiguredModuleData(null) // available
        )
      )
      val plugins = withInitContextForLoadingRuleDetermination(initContext) {
        discoverPlugins()
      }

      val result = PluginDependencyAnalysis.sequenceRequiredModules(initContext, plugins[0]).toList()

      assertThat(result).hasSize(2) // main + conditional module (now required)
      assertThat(result[0]).isInstanceOf(PluginMainDescriptor::class.java)
      assertThat(result[1]).isInstanceOf(ContentModuleDescriptor::class.java)
    }

    @Test
    fun `plugin with requiredIfAvailable when target is unavailable stays optional`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.conditional", loadingRule = ModuleLoadingRuleValue.OPTIONAL, requiredIfAvailable = "target.module") {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val targetModuleId = PluginModuleId("target.module", "namespace")
      val initContext = createInitContext(
        environmentConfiguredModules = mapOf(
          targetModuleId to PluginInitializationContext.EnvironmentConfiguredModuleData(
            UnsuitableProductModeModuleUnavailabilityReason(targetModuleId, "test")
          )
        )
      )
      val plugins = withInitContextForLoadingRuleDetermination(initContext) { discoverPlugins() }
      
      val result = PluginDependencyAnalysis.sequenceRequiredModules(initContext, plugins[0]).toList()

      assertThat(result).hasSize(1) // only main, conditional module stays optional
      assertThat(result[0]).isInstanceOf(PluginMainDescriptor::class.java)
    }

    @Test
    fun `plugin with embedded module always included`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.embedded", loadingRule = ModuleLoadingRuleValue.EMBEDDED) {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      
      val result = PluginDependencyAnalysis.sequenceRequiredModules(initContext, plugins[0]).toList()

      assertThat(result).hasSize(2) // main + embedded module
      assertThat(result[1]).isInstanceOf(ContentModuleDescriptor::class.java)
      assertThat((result[1] as ContentModuleDescriptor).moduleLoadingRule).isEqualTo(ModuleLoadingRule.EMBEDDED)
    }
  }

  @Nested
  inner class SequenceStrictDependenciesTests {

    @Test
    fun `plugin with no dependencies returns empty sequence`() {
      plugin("foo") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = PluginDependencyAnalysis.sequenceStrictDependencies(plugins[0]).toList()

      assertThat(result).isEmpty()
    }

    @Test
    fun `plugin with required plugin dependencies only`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
        depends("baz")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = PluginDependencyAnalysis.sequenceStrictDependencies(plugins[0]).toList()

      assertThat(result).hasSize(2)
      assertThat(result).allMatch { it is PluginDependencyAnalysis.DependencyRef.Plugin }
      val pluginIds = result.map { (it as PluginDependencyAnalysis.DependencyRef.Plugin).pluginId.idString }
      assertThat(pluginIds).containsExactlyInAnyOrder("bar", "baz")
    }

    @Test
    fun `plugin with optional plugin dependencies excludes optional`() {
      plugin("foo") {
        version = "1.0"
        depends("bar") // required
        depends("baz", optional = true) // optional
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = PluginDependencyAnalysis.sequenceStrictDependencies(plugins[0]).toList()

      assertThat(result).hasSize(1)
      val pluginDep = result[0] as PluginDependencyAnalysis.DependencyRef.Plugin
      assertThat(pluginDep.pluginId.idString).isEqualTo("bar")
    }

    @Test
    fun `plugin with depends dependencies`() {
      plugin("foo") {
        version = "1.0"
        depends("com.intellij.modules.platform")
        depends("com.intellij.modules.lang")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = PluginDependencyAnalysis.sequenceStrictDependencies(plugins[0]).toList()

      assertThat(result).hasSize(2)
      assertThat(result).allMatch { it is PluginDependencyAnalysis.DependencyRef.Plugin }
    }

    @Test
    fun `plugin with content module dependencies`() {
      plugin("foo") {
        version = "1.0"
        dependencies {
          module("bar.module")
          module("baz.module")
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = PluginDependencyAnalysis.sequenceStrictDependencies(plugins[0]).toList()

      assertThat(result).hasSize(2)
      assertThat(result).allMatch { it is PluginDependencyAnalysis.DependencyRef.ContentModule }
      val moduleIds = result.map { (it as PluginDependencyAnalysis.DependencyRef.ContentModule).moduleId.name }
      assertThat(moduleIds).containsExactlyInAnyOrder("bar.module", "baz.module")
    }

    @Test
    fun `plugin with mixed dependencies - plugin and content module`() {
      plugin("foo") {
        version = "1.0"
        dependencies {
          plugin("com.intellij.modules.platform")
          plugin("bar")
          module("baz.module")
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = PluginDependencyAnalysis.sequenceStrictDependencies(plugins[0]).toList()

      assertThat(result).hasSize(3)
      val pluginDeps = result.filterIsInstance<PluginDependencyAnalysis.DependencyRef.Plugin>()
      val contentModuleDeps = result.filterIsInstance<PluginDependencyAnalysis.DependencyRef.ContentModule>()
      
      assertThat(pluginDeps).hasSize(2)
      assertThat(contentModuleDeps).hasSize(1)
    }

    @Test
    fun `mixed required and optional dependencies - only required returned`() {
      plugin("foo") {
        version = "1.0"
        depends("required1")
        depends("optional1", optional = true)
        depends("required2")
        depends("optional2", optional = true)
        depends("com.intellij.modules.platform")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = PluginDependencyAnalysis.sequenceStrictDependencies(plugins[0]).toList()

      assertThat(result).hasSize(3) // required1, required2, platform dependency
      val pluginDeps = result.filterIsInstance<PluginDependencyAnalysis.DependencyRef.Plugin>()
      val pluginIds = pluginDeps.map { it.pluginId.idString }
      assertThat(pluginIds).doesNotContain("optional1", "optional2")
    }

    @Test
    fun `content module descriptor also has dependencies`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies {
              plugin("bar")
            }
          }
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val contentModule = plugins[0].contentModules[0]
      val result = PluginDependencyAnalysis.sequenceStrictDependencies(contentModule).toList()

      assertThat(result).hasSize(1)
      val pluginDep = result[0] as PluginDependencyAnalysis.DependencyRef.Plugin
      assertThat(pluginDep.pluginId.idString).isEqualTo("bar")
    }
  }

  @Nested
  inner class GetRequiredTransitiveModulesTests {

    @Test
    fun `single plugin with no dependencies returns only itself`() {
      plugin("foo") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, plugins, ambiguousPluginSet)

      assertThat(result).hasSize(1)
      assertThat(result.first().pluginId.idString).isEqualTo("foo")
    }

    @Test
    fun `plugin with direct plugin dependency includes both`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(fooPlugin), ambiguousPluginSet)

      assertThat(result).hasSize(2)
      assertThat(result.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
    }

    @Test
    fun `plugin with transitive dependencies - A depends on B depends on C`() {
      plugin("a") {
        version = "1.0"
        depends("b")
      }.buildDir(pluginsDirPath.resolve("a"))
      
      plugin("b") {
        version = "1.0"
        depends("c")
      }.buildDir(pluginsDirPath.resolve("b"))
      
      plugin("c") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("c"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val aPlugin = plugins.first { it.pluginId.idString == "a" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(aPlugin), ambiguousPluginSet)

      assertThat(result).hasSize(3)
      assertThat(result.map { it.pluginId.idString }).containsExactlyInAnyOrder("a", "b", "c")
    }

    @Test
    fun `plugin with content module dependencies includes content modules`() {
      plugin("foo") {
        version = "1.0"
        dependencies {
          module("bar.module")
        }
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        content {
          module("bar.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
        }
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(fooPlugin), ambiguousPluginSet)

      // Should include: foo, bar.module (content module), bar (parent of content module)
      assertThat(result).hasSize(3)
      assertThat(result.filterIsInstance<PluginMainDescriptor>()).hasSize(2)
      assertThat(result.filterIsInstance<ContentModuleDescriptor>()).hasSize(1)
    }

    @Test
    fun `diamond dependency pattern - A depends on B and C, both depend on D`() {
      plugin("a") {
        version = "1.0"
        depends("b")
        depends("c")
      }.buildDir(pluginsDirPath.resolve("a"))
      
      plugin("b") {
        version = "1.0"
        depends("d")
      }.buildDir(pluginsDirPath.resolve("b"))
      
      plugin("c") {
        version = "1.0"
        depends("d")
      }.buildDir(pluginsDirPath.resolve("c"))
      
      plugin("d") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("d"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val aPlugin = plugins.first { it.pluginId.idString == "a" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(aPlugin), ambiguousPluginSet)

      // Should include all 4 plugins, D should not be duplicated
      assertThat(result).hasSize(4)
      assertThat(result.map { it.pluginId.idString }).containsExactlyInAnyOrder("a", "b", "c", "d")
    }

    @Test
    fun `circular dependencies handled correctly - A depends on B depends on A`() {
      plugin("a") {
        version = "1.0"
        depends("b")
      }.buildDir(pluginsDirPath.resolve("a"))
      
      plugin("b") {
        version = "1.0"
        depends("a")
      }.buildDir(pluginsDirPath.resolve("b"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val aPlugin = plugins.first { it.pluginId.idString == "a" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(aPlugin), ambiguousPluginSet)

      // Should include both, each visited only once
      assertThat(result).hasSize(2)
      assertThat(result.map { it.pluginId.idString }).containsExactlyInAnyOrder("a", "b")
    }

    @Test
    fun `plugin with required content modules includes them transitively`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.module1", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
          module("foo.module2", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(fooPlugin), ambiguousPluginSet)

      // Should include: foo + 2 content modules
      assertThat(result).hasSize(3)
      assertThat(result.filterIsInstance<PluginMainDescriptor>()).hasSize(1)
      assertThat(result.filterIsInstance<ContentModuleDescriptor>()).hasSize(2)
    }

    @Test
    fun `ambiguous plugin set with multiple resolutions includes all matches`() {
      plugin("foo") {
        version = "1.0"
        depends("ambiguous")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      // Two plugins both declare "ambiguous" as an alias
      plugin("bar1") {
        version = "1.0"
        pluginAliases = listOf("ambiguous")
      }.buildDir(pluginsDirPath.resolve("bar1"))
      
      plugin("bar2") {
        version = "2.0"
        pluginAliases = listOf("ambiguous")
      }.buildDir(pluginsDirPath.resolve("bar2"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(fooPlugin), ambiguousPluginSet)

      // Should include: foo + both bar1 and bar2 (since "ambiguous" resolves to both)
      assertThat(result).hasSize(3)
      assertThat(result.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar1", "bar2")
    }

    @Test
    fun `plugin with optional content modules excludes them`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
          module("foo.optional", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(fooPlugin), ambiguousPluginSet)

      // Should include: foo + only required module
      assertThat(result).hasSize(2)
      assertThat(result.filterIsInstance<ContentModuleDescriptor>()).hasSize(1)
      val contentModule = result.filterIsInstance<ContentModuleDescriptor>().first()
      assertThat(contentModule.moduleId.name).isEqualTo("foo.required")
    }

    @Test
    fun `plugin with optional plugin dependencies excludes them`() {
      plugin("foo") {
        version = "1.0"
        depends("required")
        depends("optional", optional = true)
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("required") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("required"))
      
      plugin("optional") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("optional"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(fooPlugin), ambiguousPluginSet)

      // Should include: foo + required (but not optional)
      assertThat(result).hasSize(2)
      assertThat(result.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "required")
    }

    @Test
    fun `empty plugin list returns empty set`() {
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(emptyList())
      
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, emptyList(), ambiguousPluginSet)

      assertThat(result).isEmpty()
    }

    @Test
    fun `complex scenario with mixed dependencies and content modules`() {
      plugin("app") {
        version = "1.0"
        dependencies {
          plugin("core")
        }
        content {
          module("app.ui", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies {
              plugin("utils")
            }
          }
          module("app.extra", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
        }
      }.buildDir(pluginsDirPath.resolve("app"))
      
      plugin("core") {
        version = "1.0"
        content {
          module("core.api", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        }
      }.buildDir(pluginsDirPath.resolve("core"))
      
      plugin("utils") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("utils"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
      
      val appPlugin = plugins.first { it.pluginId.idString == "app" }
      val result = PluginDependencyAnalysis.getRequiredTransitiveModules(initContext, listOf(appPlugin), ambiguousPluginSet)

      // Should include: app, app.ui (required), core, core.api (required), utils
      // Should NOT include: app.extra (optional)
      assertThat(result.filterIsInstance<PluginMainDescriptor>()).hasSize(3)
      assertThat(result.filterIsInstance<ContentModuleDescriptor>()).hasSize(2)
      
      val pluginIds = result.filterIsInstance<PluginMainDescriptor>().map { it.pluginId.idString }
      assertThat(pluginIds).containsExactlyInAnyOrder("app", "core", "utils")
      
      val contentModuleNames = result.filterIsInstance<ContentModuleDescriptor>().map { it.moduleId.name }
      assertThat(contentModuleNames).containsExactlyInAnyOrder("app.ui", "core.api")
    }

    @Test
    fun `callback invoked for unresolved plugin dependency`() {
      plugin("foo") {
        version = "1.0"
        depends("missing")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)

      val unresolvedDeps = mutableListOf<Pair<PluginModuleDescriptor, PluginDependencyAnalysis.DependencyRef>>()
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }

      PluginDependencyAnalysis.getRequiredTransitiveModules(
        initContext,
        listOf(fooPlugin),
        ambiguousPluginSet,
        unresolvedDeps
      )

      assertThat(unresolvedDeps).hasSize(1)
      val (node, dep) = unresolvedDeps[0]
      assertThat(node.pluginId.idString).isEqualTo("foo")
      assertThat(dep).isInstanceOf(PluginDependencyAnalysis.DependencyRef.Plugin::class.java)
      assertThat((dep as PluginDependencyAnalysis.DependencyRef.Plugin).pluginId.idString).isEqualTo("missing")
    }

    @Test
    fun `callback invoked for unresolved content module dependency`() {
      plugin("foo") {
        version = "1.0"
        dependencies {
          module("missing.module")
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)

      val unresolvedDeps = mutableListOf<Pair<PluginModuleDescriptor, PluginDependencyAnalysis.DependencyRef>>()
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }

      PluginDependencyAnalysis.getRequiredTransitiveModules(
        initContext,
        listOf(fooPlugin),
        ambiguousPluginSet,
        unresolvedDeps
      )

      assertThat(unresolvedDeps).hasSize(1)
      val (node, dep) = unresolvedDeps[0]
      assertThat(node.pluginId.idString).isEqualTo("foo")
      assertThat(dep).isInstanceOf(PluginDependencyAnalysis.DependencyRef.ContentModule::class.java)
      assertThat((dep as PluginDependencyAnalysis.DependencyRef.ContentModule).moduleId.name).isEqualTo("missing.module")
    }

    @Test
    fun `callback not invoked when all dependencies resolve`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
      }.buildDir(pluginsDirPath.resolve("foo"))

      plugin("bar") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)

      val unresolvedDeps = mutableListOf<Pair<PluginModuleDescriptor, PluginDependencyAnalysis.DependencyRef>>()
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }

      PluginDependencyAnalysis.getRequiredTransitiveModules(
        initContext,
        listOf(fooPlugin),
        ambiguousPluginSet,
        unresolvedDeps
      )

      assertThat(unresolvedDeps).isEmpty()
    }

    @Test
    fun `callback invoked multiple times for multiple unresolved dependencies`() {
      plugin("foo") {
        version = "1.0"
        dependencies {
          plugin("missing1")
          plugin("missing2")
          module("missing.module")
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)

      val unresolvedDeps = mutableListOf<Pair<PluginModuleDescriptor, PluginDependencyAnalysis.DependencyRef>>()
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }

      PluginDependencyAnalysis.getRequiredTransitiveModules(
        initContext,
        listOf(fooPlugin),
        ambiguousPluginSet,
        unresolvedDeps
      )

      assertThat(unresolvedDeps).hasSize(3)
      assertThat(unresolvedDeps.all { it.first.pluginId.idString == "foo" }).isTrue()

      val pluginDeps = unresolvedDeps.mapNotNull { (_, dep) ->
        (dep as? PluginDependencyAnalysis.DependencyRef.Plugin)?.pluginId?.idString
      }
      assertThat(pluginDeps).containsExactlyInAnyOrder("missing1", "missing2")

      val contentModuleDeps = unresolvedDeps.mapNotNull { (_, dep) ->
        (dep as? PluginDependencyAnalysis.DependencyRef.ContentModule)?.moduleId?.name
      }
      assertThat(contentModuleDeps).containsExactly("missing.module")
    }

    @Test
    fun `callback invoked for transitive unresolved dependencies`() {
      plugin("foo") {
        version = "1.0"
        depends("bar")
      }.buildDir(pluginsDirPath.resolve("foo"))

      plugin("bar") {
        version = "1.0"
        depends("missing")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val initContext = createInitContext()
      val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)

      val unresolvedDeps = mutableListOf<Pair<PluginModuleDescriptor, PluginDependencyAnalysis.DependencyRef>>()
      val fooPlugin = plugins.first { it.pluginId.idString == "foo" }

      PluginDependencyAnalysis.getRequiredTransitiveModules(
        initContext,
        listOf(fooPlugin),
        ambiguousPluginSet,
        unresolvedDeps
      )

      // The unresolved dependency should be reported from bar (not foo)
      assertThat(unresolvedDeps).hasSize(1)
      val (node, dep) = unresolvedDeps[0]
      assertThat(node.pluginId.idString).isEqualTo("bar")
      assertThat((dep as PluginDependencyAnalysis.DependencyRef.Plugin).pluginId.idString).isEqualTo("missing")
    }
  }
}