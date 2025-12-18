// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConditionalModuleLoadingRuleTest {
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
    Logger.setUnitTestMode() // due to warnInProduction use in IdeaPluginDescriptorImpl
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginsDirPath get() = rootPath.resolve("wd/plugins")

  @ParameterizedTest
  @ValueSource(strings = ["monolith", "frontend", "backend"])
  fun `plugin loads only in frontend mode with a missing dependency and required-if-available on backend`(appMode: String) {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRule.OPTIONAL, requiredIfAvailable = "intellij.platform.backend") {
          dependencies { module("unavailable") }
        }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))
    val pluginSet = buildPluginSet { withProductMode(ProductMode.findById(appMode)!!) }
    if (appMode == "frontend") {
      assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    } else {
      assertThat(pluginSet).doesNotHaveEnabledPlugins()
      val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
      assertThat(errors).hasSizeGreaterThan(0)
      assertThat(errors[0].htmlMessage.toString()).contains("foo", "requires plugin", "unavailable", "to be installed")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["monolith", "frontend", "backend"])
  fun `plugin loads only in backend mode with a missing dependency and required-if-available on frontend`(appMode: String) {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRule.OPTIONAL, requiredIfAvailable = "intellij.platform.frontend") {
          dependencies { module("unavailable") }
        }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))
    val pluginSet = buildPluginSet { withProductMode(ProductMode.findById(appMode)!!) }
    if (appMode == "backend") {
      assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    } else {
      assertThat(pluginSet).doesNotHaveEnabledPlugins()
      val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
      assertThat(errors).hasSizeGreaterThan(0)
      assertThat(errors[0].htmlMessage.toString()).contains("foo", "requires plugin", "unavailable", "to be installed")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["monolith", "frontend", "backend"])
  fun `plugin loads only in monolith mode with a missing dependency and required-if-available on frontend-split`(appMode: String) {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRule.OPTIONAL, requiredIfAvailable = "intellij.platform.frontend.split") {
          dependencies { module("unavailable") }
        }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))
    val pluginSet = buildPluginSet { withProductMode(ProductMode.findById(appMode)!!) }
    if (appMode != "frontend") {
      assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    } else {
      assertThat(pluginSet).doesNotHaveEnabledPlugins()
      val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
      assertThat(errors).hasSizeGreaterThan(0)
      assertThat(errors[0].htmlMessage.toString()).contains("foo", "requires plugin", "unavailable", "to be installed")
    }
  }

  @Test
  fun `content module with required-if-available and a dependency on an optional content module may break plugin loading`() {
    plugin("foo") {
      content {
        module("foo.optional", loadingRule = ModuleLoadingRule.OPTIONAL) {}
        module("foo.maybe.req", loadingRule = ModuleLoadingRule.OPTIONAL, requiredIfAvailable = "intellij.platform.backend") {
          dependencies { module("foo.optional") }
        }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))

    val pluginSetFrontend = buildPluginSet { withProductMode(ProductMode.findById("frontend")!!) }
    assertThat(pluginSetFrontend).hasExactlyEnabledPlugins("foo")

    val pluginSetMonolith = buildPluginSet { withProductMode(ProductMode.findById("monolith")!!) }
    assertThat(pluginSetMonolith).doesNotHaveEnabledPlugins()
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).hasSizeGreaterThan(0)
    assertThat(errors[0].htmlMessage.toString()).contains("foo", "cannot be loaded", "form a dependency cycle")
  }

  private fun buildPluginSet(builder: PluginSetTestBuilder.() -> Unit = {}): PluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath).apply(builder).build()
}