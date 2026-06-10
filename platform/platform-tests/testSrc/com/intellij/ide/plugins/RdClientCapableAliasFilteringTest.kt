// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.pluginSystem.testFramework.TestPluginInitContextFactory
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.platform.testFramework.plugins.pluginAlias
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.RegisterExtension

private const val RD_CLIENT_CAPABLE = "com.intellij.rd.client.capable"

/**
 * Tests for the temporary workaround that filters the [RD_CLIENT_CAPABLE] plugin alias out of content modules
 * of non-core plugins in product modes where `intellij.platform.backend` is unavailable.
 * See `filterBackendOnlyAliases` in `IdeaPluginDescriptorImpl.kt`.
 * Delete this file together with the workaround (IJPL-242789).
 */
class RdClientCapableAliasFilteringTest {
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
    Logger.setUnitTestMode()
    PluginManagerCore.isUnitTestMode = true
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val pluginsDirPath get() = inMemoryFs.fs.getPath("/wd/plugins")

  // mirrors clion-radler: the alias is declared in a backend-only marker content module
  private fun installRadlerLikePlugin() {
    plugin("radler.like") {
      version = "1.0"
      content(namespace = "jetbrains") {
        module("radler.like.backend.marker", requiredIfAvailable = "intellij.platform.backend") {
          dependencies {
            module("intellij.platform.backend")
          }
          pluginAlias(RD_CLIENT_CAPABLE)
        }
      }
    }.installAt(pluginsDirPath)
  }

  private fun installCoreLikePlugin() {
    plugin(PluginManagerCore.CORE_ID.idString) {
      version = "1.0"
      pluginAlias(RD_CLIENT_CAPABLE)
    }.installAt(pluginsDirPath)
  }

  private fun discoverPlugins(productMode: ProductMode): List<PluginMainDescriptor> {
    val builder = PluginSetTestBuilder.fromPath(pluginsDirPath).withProductMode(productMode)
    val initContext = builder.buildInitContext()
    return PluginInitContextFactory.withCustomFactoryInUnitTests(TestPluginInitContextFactory(initContext)) {
      builder.discoverPlugins().second.pluginLists.flatMap { it.plugins }
    }
  }

  @Test
  fun `alias is removed from a content module in frontend mode`() {
    installRadlerLikePlugin()

    val marker = discoverPlugins(ProductMode.FRONTEND).single().contentModules.single()

    assertThat(marker.pluginAliases).doesNotContain(PluginId.getId(RD_CLIENT_CAPABLE))
  }

  @Test
  fun `alias is kept in monolith and backend modes`() {
    installRadlerLikePlugin()

    for (mode in listOf(ProductMode.MONOLITH, ProductMode.BACKEND)) {
      val marker = discoverPlugins(mode).single().contentModules.single()
      assertThat(marker.pluginAliases).describedAs("mode: ${mode.id}").contains(PluginId.getId(RD_CLIENT_CAPABLE))
    }
  }

  @Test
  fun `alias of the core plugin is not affected in frontend mode`() {
    installCoreLikePlugin()

    val core = discoverPlugins(ProductMode.FRONTEND).single()

    assertThat(core.pluginAliases).contains(PluginId.getId(RD_CLIENT_CAPABLE))
  }

  @Test
  fun `no id conflict between core plugin and the marker module in frontend mode`() {
    installCoreLikePlugin()
    installRadlerLikePlugin()

    val plugins = discoverPlugins(ProductMode.FRONTEND)
    val pluginSet = UnambiguousPluginSet.tryBuild(plugins)

    assertNotNull(pluginSet)
    val resolved = pluginSet.resolvePluginId(PluginId.getId(RD_CLIENT_CAPABLE))
    assertNotNull(resolved)
    assertThat(resolved.pluginId).isEqualTo(PluginManagerCore.CORE_ID)
  }

  @Test
  fun `id conflict remains in monolith mode`() {
    installCoreLikePlugin()
    installRadlerLikePlugin()

    val plugins = discoverPlugins(ProductMode.MONOLITH)

    assertThat(UnambiguousPluginSet.tryBuild(plugins)).isNull()
  }
}
