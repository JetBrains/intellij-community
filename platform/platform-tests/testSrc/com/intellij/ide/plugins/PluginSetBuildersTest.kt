// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.buildDir
import com.intellij.platform.testFramework.plugins.content
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
 * Tests for [UnambiguousPluginSet.tryBuild] and [AmbiguousPluginSet.build] builder methods.
 * These tests focus on the low-level plugin set construction logic.
 */
class PluginSetBuildersTest {

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

  private fun discoverPlugins(): List<PluginMainDescriptor> {
    val (_, discoveryResult) = PluginSetTestBuilder.fromPath(pluginsDirPath).discoverPlugins()
    return discoveryResult.pluginLists.flatMap { it.plugins }
  }

  @Nested
  inner class UnambiguousPluginSetTests {

    @Test
    fun `empty plugin list returns empty set`() {
      val result = UnambiguousPluginSet.tryBuild(emptyList())

      assertThat(result).isNotNull
      assertThat(result!!.plugins).isEmpty()
      assertThat(result.buildFullPluginIdMapping()).isEmpty()
      assertThat(result.buildFullContentModuleIdMapping()).isEmpty()
    }

    @Test
    fun `single plugin with no aliases succeeds`() {
      plugin("foo") {
        version = "1.0"
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNotNull
      assertThat(result!!.plugins).hasSize(1)
      assertThat(result.plugins[0].pluginId.idString).isEqualTo("foo")
      
      // Verify resolution
      assertThat(result.resolvePluginId(PluginId.getId("foo"))).isNotNull
      assertThat(result.resolvePluginId(PluginId.getId("unknown"))).isNull()
      
      // Verify full mapping
      assertThat(result.buildFullPluginIdMapping()).hasSize(1)
      assertThat(result.buildFullPluginIdMapping()[PluginId.getId("foo")]).isNotNull
    }

    @Test
    fun `single plugin with aliases succeeds and all IDs resolvable`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias-1", "foo-alias-2")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNotNull
      assertThat(result!!.plugins).hasSize(1)
      
      // Verify main ID and aliases resolve to same descriptor
      val fooDescriptor = result.resolvePluginId(PluginId.getId("foo"))
      assertThat(fooDescriptor).isNotNull
      assertThat(result.resolvePluginId(PluginId.getId("foo-alias-1"))).isSameAs(fooDescriptor)
      assertThat(result.resolvePluginId(PluginId.getId("foo-alias-2"))).isSameAs(fooDescriptor)
      
      // Verify full mapping contains all IDs
      val fullMapping = result.buildFullPluginIdMapping()
      assertThat(fullMapping).hasSize(3) // main ID + 2 aliases
      assertThat(fullMapping).containsKeys(
        PluginId.getId("foo"),
        PluginId.getId("foo-alias-1"),
        PluginId.getId("foo-alias-2")
      )
    }

    @Test
    fun `multiple plugins with unique IDs succeed`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      plugin("bar") { version = "1.0" }.buildDir(pluginsDirPath.resolve("bar"))
      plugin("baz") { version = "1.0" }.buildDir(pluginsDirPath.resolve("baz"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNotNull
      assertThat(result!!.plugins).hasSize(3)
      assertThat(result.plugins.map { it.pluginId.idString })
        .containsExactlyInAnyOrder("foo", "bar", "baz")
      
      // Verify all plugins are resolvable
      assertThat(result.resolvePluginId(PluginId.getId("foo"))).isNotNull
      assertThat(result.resolvePluginId(PluginId.getId("bar"))).isNotNull
      assertThat(result.resolvePluginId(PluginId.getId("baz"))).isNotNull
    }

    @Test
    fun `plugin with content modules and unique IDs succeeds`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.module1", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            pluginAlias("module1-alias")
          }
          module("foo.module2", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {
            // no alias
          }
        }
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNotNull
      assertThat(result!!.plugins).hasSize(1)
      
      // Verify plugin ID is resolvable
      assertThat(result.resolvePluginId(PluginId.getId("foo"))).isNotNull
      assertThat(result.resolvePluginId(PluginId.getId("module1-alias"))).isNotNull
      
      // Get the actual namespace from the content modules
      val fooPlugin = result.plugins[0]
      val module1Id = fooPlugin.contentModules[0].moduleId
      val module2Id = fooPlugin.contentModules[1].moduleId
      
      // Verify content modules are resolvable
      assertThat(result.resolveContentModuleId(module1Id)).isNotNull
      assertThat(result.resolveContentModuleId(module2Id)).isNotNull
      
      // Verify full content module mapping
      val contentMapping = result.buildFullContentModuleIdMapping()
      assertThat(contentMapping).hasSize(2)
      assertThat(contentMapping).containsKeys(module1Id, module2Id)
    }

    @Test
    fun `two plugins with same main ID return null`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo1"))
      plugin("foo") { version = "2.0" }.buildDir(pluginsDirPath.resolve("foo2"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNull()
    }

    @Test
    fun `two plugins with conflicting alias return null`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared-alias")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared-alias")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNull()
    }

    @Test
    fun `plugin main ID conflicts with another plugin alias returns null`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("foo")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNull()
    }

    @Test
    fun `plugin declares same alias twice returns null - self conflict`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("duplicate", "duplicate")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNull()
    }

    @Test
    fun `content module ID conflict returns null`() {
      plugin("foo") {
        version = "1.0"
        content(namespace = "shared") {
          module("shared.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        content(namespace = "shared") {
          module("shared.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        }
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNull()
    }

    @Test
    fun `content module alias conflict returns null`() {
      plugin("foo") {
        version = "1.0"
        content {
          module("foo.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            pluginAlias("shared-alias")
          }
        }
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        content {
          module("bar.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            pluginAlias("shared-alias")
          }
        }
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNull()
    }

    @Test
    fun `content module alias conflicts with plugin ID returns null`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        content {
          module("bar.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            pluginAlias("foo")
          }
        }
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val result = UnambiguousPluginSet.tryBuild(plugins)

      assertThat(result).isNull()
    }
  }

  @Nested
  inner class AmbiguousPluginSetTests {

    @Test
    fun `empty plugin list succeeds with empty mappings`() {
      val result = AmbiguousPluginSet.build(emptyList())

      assertThat(result).isNotNull
      assertThat(result.plugins).isEmpty()
      assertThat(result.buildFullPluginIdMapping()).isEmpty()
      assertThat(result.buildFullContentModuleIdMapping()).isEmpty()
    }

    @Test
    fun `single plugin succeeds with correct mappings`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("foo-alias")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = AmbiguousPluginSet.build(plugins)

      assertThat(result).isNotNull
      assertThat(result.plugins).hasSize(1)
      
      // Verify resolution returns sequences with single element
      assertThat(result.resolvePluginId(PluginId.getId("foo")).toList()).hasSize(1)
      assertThat(result.resolvePluginId(PluginId.getId("foo-alias")).toList()).hasSize(1)
      assertThat(result.resolvePluginId(PluginId.getId("unknown")).toList()).isEmpty()
      
      // Verify full mapping
      val fullMapping = result.buildFullPluginIdMapping()
      assertThat(fullMapping).hasSize(2) // main ID + alias
      assertThat(fullMapping[PluginId.getId("foo")]).hasSize(1)
      assertThat(fullMapping[PluginId.getId("foo-alias")]).hasSize(1)
    }

    @Test
    fun `two plugins with same ID succeed - resolvePluginId returns both`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo1"))
      plugin("foo") { version = "2.0" }.buildDir(pluginsDirPath.resolve("foo2"))

      val plugins = discoverPlugins()
      val result = AmbiguousPluginSet.build(plugins)

      assertThat(result).isNotNull
      assertThat(result.plugins).hasSize(2)
      
      // Verify both plugins are returned
      val resolved = result.resolvePluginId(PluginId.getId("foo")).toList()
      assertThat(resolved).hasSize(2)
      assertThat(resolved.map { it.pluginId.idString }).containsOnly("foo")
      
      // Verify full mapping
      val fullMapping = result.buildFullPluginIdMapping()
      assertThat(fullMapping[PluginId.getId("foo")]).hasSize(2)
    }

    @Test
    fun `conflicting aliases succeed - resolve returns all matches`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("shared-alias")
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("shared-alias")
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val result = AmbiguousPluginSet.build(plugins)

      assertThat(result).isNotNull
      assertThat(result.plugins).hasSize(2)
      
      // Verify shared alias resolves to both plugins
      val resolvedByAlias = result.resolvePluginId(PluginId.getId("shared-alias")).toList()
      assertThat(resolvedByAlias).hasSize(2)
      assertThat(resolvedByAlias.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
      
      // Verify individual IDs still work
      assertThat(result.resolvePluginId(PluginId.getId("foo")).toList()).hasSize(1)
      assertThat(result.resolvePluginId(PluginId.getId("bar")).toList()).hasSize(1)
    }

    @Test
    fun `content module conflicts succeed with sequences`() {
      plugin("foo") {
        version = "1.0"
        content(namespace = "shared") {
          module("shared.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        }
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        content(namespace = "shared") {
          module("shared.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        }
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val result = AmbiguousPluginSet.build(plugins)

      assertThat(result).isNotNull
      assertThat(result.plugins).hasSize(2)
      
      // Verify both content modules are returned
      val resolved = result.resolveContentModuleId(PluginModuleId("shared.module", "shared")).toList()
      assertThat(resolved).hasSize(2)
      assertThat(resolved.map { it.pluginId.idString }).containsExactlyInAnyOrder("foo", "bar")
      
      // Verify full content module mapping
      val contentMapping = result.buildFullContentModuleIdMapping()
      assertThat(contentMapping[PluginModuleId("shared.module", "shared")]).hasSize(2)
    }

    @Test
    fun `multiple conflicts succeed - all tracked in mappings`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("alias1", "shared")
        content(namespace = "test") {
          module("foo.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            pluginAlias("module-alias")
          }
        }
      }.buildDir(pluginsDirPath.resolve("foo"))
      
      plugin("bar") {
        version = "1.0"
        pluginAliases = listOf("alias2", "shared")
        content(namespace = "test") {
          module("bar.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            pluginAlias("module-alias")
          }
        }
      }.buildDir(pluginsDirPath.resolve("bar"))

      val plugins = discoverPlugins()
      val result = AmbiguousPluginSet.build(plugins)

      assertThat(result).isNotNull
      assertThat(result.plugins).hasSize(2)
      
      // Verify shared plugin alias resolves to both
      assertThat(result.resolvePluginId(PluginId.getId("shared")).toList()).hasSize(2)
      
      // Verify unique aliases resolve to one
      assertThat(result.resolvePluginId(PluginId.getId("alias1")).toList()).hasSize(1)
      assertThat(result.resolvePluginId(PluginId.getId("alias2")).toList()).hasSize(1)
      
      // Verify shared content module alias resolves to both
      assertThat(result.resolvePluginId(PluginId.getId("module-alias")).toList()).hasSize(2)
      
      // Verify full mappings
      val pluginMapping = result.buildFullPluginIdMapping()
      assertThat(pluginMapping[PluginId.getId("shared")]).hasSize(2)
      assertThat(pluginMapping[PluginId.getId("module-alias")]).hasSize(2)
      
      val contentMapping = result.buildFullContentModuleIdMapping()
      assertThat(contentMapping[PluginModuleId("foo.module", "test")]).hasSize(1)
      assertThat(contentMapping[PluginModuleId("bar.module", "test")]).hasSize(1)
    }

    @Test
    fun `resolve unknown ID returns empty sequence`() {
      plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = AmbiguousPluginSet.build(plugins)

      assertThat(result.resolvePluginId(PluginId.getId("unknown")).toList()).isEmpty()
      assertThat(result.resolveContentModuleId(PluginModuleId("unknown", "test")).toList()).isEmpty()
    }

    @Test
    fun `plugin with duplicate alias in same descriptor tracked multiple times`() {
      plugin("foo") {
        version = "1.0"
        pluginAliases = listOf("duplicate", "duplicate")
      }.buildDir(pluginsDirPath.resolve("foo"))

      val plugins = discoverPlugins()
      val result = AmbiguousPluginSet.build(plugins)

      assertThat(result).isNotNull
      assertThat(result.plugins).hasSize(1)
      
      // The duplicate alias should appear twice in the mapping
      val resolved = result.resolvePluginId(PluginId.getId("duplicate")).toList()
      assertThat(resolved).hasSize(2) // same descriptor appears twice
      
      val fullMapping = result.buildFullPluginIdMapping()
      assertThat(fullMapping[PluginId.getId("duplicate")]).hasSize(2)
    }
  }
}
