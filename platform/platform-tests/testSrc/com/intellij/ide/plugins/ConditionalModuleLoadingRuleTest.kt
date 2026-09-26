// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
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
      assertThat(pluginSet.getRootExclusionReason("foo")).isInstanceOf(DependencyIsNotResolved::class.java)
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
      assertThat(pluginSet.getRootExclusionReason("foo")).isInstanceOf(DependencyIsNotResolved::class.java)
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
      assertThat(pluginSet.getRootExclusionReason("foo")).isInstanceOf(DependencyIsNotResolved::class.java)
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
  }

  @Test
  @SystemProperty(propertyKey = "idea.plugins.required.if.available.disabled", propertyValue = "false")
  fun `the product context stores the flag at creation`() {
    val context = ProductPluginInitContext()
    System.setProperty("idea.plugins.required.if.available.disabled", "true")

    assertThat(context.disableRequiredIfAvailable).isFalse()
    assertThat(ProductPluginInitContext().disableRequiredIfAvailable).isTrue()
  }

  @Test
  fun `a false flag preserves required-if-available`() {
    plugin("foo") {
      content {
        module("foo.module", requiredIfAvailable = "intellij.platform.backend") {
          dependencies { module("unavailable") }
        }
      }
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet { withRequiredIfAvailableDisabled(false) }
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    assertThat(pluginSet.getRootExclusionReason("foo")).isInstanceOf(DependencyIsNotResolved::class.java)
  }

  @ParameterizedTest
  @ValueSource(strings = ["monolith", "frontend", "backend"])
  fun `the flag skips only optional conditional modules with missing dependencies in every product mode`(appMode: String) {
    val targets = listOf("intellij.platform.backend", "intellij.platform.frontend", "intellij.platform.frontend.split", "unknown")
    for (loadingRule in ModuleLoadingRuleValue.entries) {
      val pluginId = "foo.${loadingRule.name.lowercase()}"
      plugin(pluginId) {
        content {
          for ((index, target) in targets.withIndex()) {
            module("$pluginId.$index", loadingRule = loadingRule, requiredIfAvailable = target) {
              dependencies { module("unavailable") }
            }
          }
        }
      }.installAt(pluginsDirPath)
    }

    val pluginSet = buildPluginSet {
      withProductMode(ProductMode.findById(appMode)!!)
      withRequiredIfAvailableDisabled(true)
    }
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo.optional", "foo.on_demand").doesNotHaveEnabledModulesWithoutMainDescriptors()
    for ((pluginId, expectedRule) in listOf("foo.optional" to ModuleLoadingRule.OPTIONAL, "foo.on_demand" to ModuleLoadingRule.ON_DEMAND)) {
      assertThat(pluginSet.getEnabledPlugin(pluginId).contentModules).allSatisfy {
        assertThat(it.moduleLoadingRule).isEqualTo(expectedRule)
      }
    }
    for (pluginId in listOf("foo.required", "foo.embedded")) {
      assertThat(pluginSet.getRootExclusionReason(pluginId)).isInstanceOf(DependencyIsNotResolved::class.java)
    }
  }

  @ParameterizedTest
  @CsvSource("REQUIRED, REQUIRED", "EMBEDDED, EMBEDDED", "OPTIONAL, OPTIONAL", "ON_DEMAND, ON_DEMAND")
  fun `the flag loads conditional modules and preserves required loading rules`(
    loadingRule: ModuleLoadingRuleValue,
    expectedRule: ModuleLoadingRule,
  ) {
    plugin("foo") {
      content {
        module("foo.dependency", loadingRule = ModuleLoadingRuleValue.EMBEDDED) {}
        module("foo.module", loadingRule = loadingRule, requiredIfAvailable = "intellij.platform.backend") {
          dependencies { module("foo.dependency") }
        }
        // A module needed to `foo.module` be loaded as on-demand
        module("foo.requires", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          dependencies { module("foo.module") }
        }
      }
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet { withRequiredIfAvailableDisabled(true) }
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo").hasExactlyEnabledModulesWithoutMainDescriptors("foo.dependency", "foo.module", "foo.requires")
    assertThat(pluginSet.getEnabledModule("foo.module").moduleLoadingRule).isEqualTo(expectedRule)
  }

  @Test
  fun `the flag does not affect on-demand modules`() {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRuleValue.ON_DEMAND) {}
      }
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet { withRequiredIfAvailableDisabled(true) }
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo").hasExactlyEnabledModulesWithoutMainDescriptors()
  }

  @ParameterizedTest
  @EnumSource(ModuleLoadingRuleValue::class)
  fun `the flag preserves loading rules for modules without required-if-available`(loadingRule: ModuleLoadingRuleValue) {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = loadingRule) {
          dependencies { module("unavailable") }
        }
      }
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet { withRequiredIfAvailableDisabled(true) }
    val effectiveRule = pluginSet.getPlugin("foo").contentModules.single().moduleLoadingRule
    assertThat(effectiveRule.name).isEqualTo(loadingRule.name)
    assertThat(pluginSet).doesNotHaveEnabledModulesWithoutMainDescriptors()
    if (effectiveRule.required) {
      assertThat(pluginSet).doesNotHaveEnabledPlugins()
      assertThat(pluginSet.getRootExclusionReason("foo")).isInstanceOf(DependencyIsNotResolved::class.java)
    }
    else {
      assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    }
  }

  private fun buildPluginSet(builder: PluginSetTestBuilder.() -> Unit = {}): PluginSet {
    return PluginSetTestBuilder.fromPath(pluginsDirPath).apply(builder).build()
  }

  private fun PluginSet.getRootExclusionReason(pluginId: String): DescriptorExclusionReason? {
    val plugin = resolvedPluginSet.candidateSet.resolvePluginId(PluginId(pluginId)) ?: return null
    val rootCauseDescriptor = plugin.sequenceDescriptorExclusionChain { resolvedPluginSet.getExclusionReason(it) }.last()
    return resolvedPluginSet.getExclusionReason(rootCauseDescriptor)
  }
}
