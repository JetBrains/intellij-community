// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConditionalModuleLoadingRuleValueTest {
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
    Logger.setUnitTestMode() // due to warnInProduction use in IdeaPluginDescriptorImpl
    PluginManagerCore.isUnitTestMode = true // FIXME git rid of this IJPL-220869
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginsDirPath get() = rootPath.resolve("wd/plugins")
  private var loadingErrors: List<PluginLoadingError> = emptyList()

  @ParameterizedTest
  @ValueSource(strings = ["monolith", "frontend", "backend"])
  fun `plugin loads only in frontend mode with a missing dependency and required-if-available on backend`(appMode: String) {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL, requiredIfAvailable = "intellij.platform.backend") {
          dependencies { module("unavailable") }
        }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet { withProductMode(ProductMode.findById(appMode)!!) }
    if (appMode == "frontend") {
      assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    } else {
      assertThat(pluginSet).doesNotHaveEnabledPlugins()
      assertThat(loadingErrors).hasSizeGreaterThan(0)
      assertThat(loadingErrors[0].htmlMessage.toString()).contains("foo", "requires plugin", "unavailable", "to be installed")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["monolith", "frontend", "backend"])
  fun `plugin loads only in backend mode with a missing dependency and required-if-available on frontend`(appMode: String) {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL, requiredIfAvailable = "intellij.platform.frontend") {
          dependencies { module("unavailable") }
        }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet { withProductMode(ProductMode.findById(appMode)!!) }
    if (appMode == "backend") {
      assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    } else {
      assertThat(pluginSet).doesNotHaveEnabledPlugins()
      assertThat(loadingErrors).hasSizeGreaterThan(0)
      assertThat(loadingErrors[0].htmlMessage.toString()).contains("foo", "requires plugin", "unavailable", "to be installed")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["monolith", "frontend", "backend"])
  fun `plugin loads only in monolith mode with a missing dependency and required-if-available on frontend-split`(appMode: String) {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL, requiredIfAvailable = "intellij.platform.frontend.split") {
          dependencies { module("unavailable") }
        }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet { withProductMode(ProductMode.findById(appMode)!!) }
    if (appMode != "frontend") {
      assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    } else {
      assertThat(pluginSet).doesNotHaveEnabledPlugins()
      assertThat(loadingErrors).hasSizeGreaterThan(0)
      assertThat(loadingErrors[0].htmlMessage.toString()).contains("foo", "requires plugin", "unavailable", "to be installed")
    }
  }

  @Test
  fun `content module with required-if-available and a dependency on an optional content module loads`() {
    plugin("foo") {
      content {
        module("foo.optional", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
        module("foo.maybe.req", loadingRule = ModuleLoadingRuleValue.OPTIONAL, requiredIfAvailable = "intellij.platform.backend") {
          dependencies { module("foo.optional") }
        }
      }
    }.installAt(pluginsDirPath)

    val pluginSetFrontend = buildPluginSet { withProductMode(ProductMode.findById("frontend")!!) }
    assertThat(pluginSetFrontend).hasExactlyEnabledPlugins("foo")

    val pluginSetMonolith = buildPluginSet { withProductMode(ProductMode.findById("monolith")!!) }
    assertThat(pluginSetMonolith).hasExactlyEnabledPlugins("foo")
    assertThat(pluginSetMonolith.getEnabledModules()).hasSize(3)
    assertThat(loadingErrors).isEmpty()
  }

  private fun buildPluginSet(builder: PluginSetTestBuilder.() -> Unit = {}): PluginSet {
    val state = PluginSetTestBuilder.fromPath(pluginsDirPath).apply(builder).buildState()
    loadingErrors = state.loadingErrors
    return state.pluginSet
  }
}