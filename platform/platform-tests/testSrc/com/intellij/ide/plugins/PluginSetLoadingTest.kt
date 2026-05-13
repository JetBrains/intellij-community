// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.ContentModuleSpec
import com.intellij.platform.testFramework.plugins.PluginPackagingConfig
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.platform.testFramework.plugins.pluginAlias
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.write
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import java.util.function.Function
import kotlin.math.absoluteValue
import kotlin.random.Random

class PluginSetLoadingTest {
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
    Logger.setUnitTestMode() // due to warnInProduction use in IdeaPluginDescriptorImpl
    PluginManagerCore.isUnitTestMode = true // FIXME git rid of this IJPL-220869
  }

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginsDirPath get() = rootPath.resolve("wd/plugins")
  private var loadingErrors: List<PluginLoadingError> = emptyList()

  @Test
  fun `fleet backend logs plugin loading errors without scheduling user notification`() {
    val policy = PluginLoadingErrorReportingPolicy.product(
      isUnitTestMode = false,
      isHeadless = true,
      isFleetBackend = true,
    )
    assertThat(policy.logLevel).isEqualTo(PluginLoadingErrorLogLevel.WARN)
    assertThat(policy.reportToUser).isFalse()
  }

  @Test
  fun `use newer plugin`() {
    writeDescriptor("foo_1-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
      </idea-plugin>""")

    val pluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath).build()
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Test
  fun `use newer plugin if disabled`() {
    writeDescriptor("foo_3-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
      </idea-plugin>""")

    val resultState = PluginSetTestBuilder.fromPath(pluginsDirPath)
      .withDisabledPlugins("foo")
      .buildState()

    val incompletePlugins = resultState.incompletePluginsForLogging
    assertThat(incompletePlugins).hasSize(1)
    val foo = incompletePlugins.single()
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")
  }

  @Test
  fun `prefer bundled if custom is incompatible`() {
    // names are important - will be loaded in alphabetical order
    writeDescriptor("foo_1-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version until-build="2"/>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version until-build="4"/>
      </idea-plugin>""")

    val resultState = PluginSetTestBuilder.fromPath(pluginsDirPath)
      .withProductBuildNumber(BuildNumber.fromString("4.0")!!)
      .buildState()

    val plugins = resultState.pluginSet.enabledPlugins.toList()
    assertThat(plugins).hasSize(1)
    assertThat(resultState.incompletePluginsForLogging).isEmpty()
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")
  }

  @Test
  fun `select compatible plugin if both versions provided`() {
    writeDescriptor("foo_1-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
        <idea-version since-build="1.*" until-build="2.*"/>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version since-build="2.0" until-build="4.*"/>
      </idea-plugin>""")

    val pluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath)
      .withProductBuildNumber(BuildNumber.fromString("3.12")!!)
      .build()
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Test
  fun `use first plugin if both versions the same`() {
    plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)
    plugin("foo") { version = "1.0" }.installAt(pluginsDirPath)

    val pluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath).build()
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("1.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Test
  fun `until build is honored only if it targets 251 and earlier`() {
    if (UntilBuildDeprecation.forceHonorUntilBuild) return

    fun addDescriptor(branch: String) = writeDescriptor("p$branch", """
    <idea-plugin>
      <id>p$branch</id>
      <version>1.0</version>
      <idea-version since-build="$branch" until-build="$branch.100"/>
    </idea-plugin>
    """.trimIndent())
    fun addDescriptorX(branch: String) = writeDescriptor("p$branch.x", """
    <idea-plugin>
      <id>p$branch.x</id>
      <version>1.0</version>
      <idea-version since-build="$branch" until-build="$branch.*"/>
    </idea-plugin>
    """.trimIndent())

    addDescriptor("251")
    addDescriptorX("251")
    addDescriptor("252")
    addDescriptor("253")
    addDescriptor("261")

    assertEnabledPluginsSetEquals(listOf("p251")) { buildNumber = "251.10" }
    assertEnabledPluginsSetEquals(listOf("p252", "p252.x")) { buildNumber = "252.10" }
    assertEnabledPluginsSetEquals(listOf("p252.x", "p253")) { buildNumber = "253.200" }
    assertEnabledPluginsSetEquals(listOf("p252.x", "p253", "p261")) { buildNumber = "261.200" }
  }

  @Test
  fun `broken plugins is honored while until build is not`() {
    if (UntilBuildDeprecation.forceHonorUntilBuild) return

    writeDescriptor("p252", """
      <idea-plugin>
      <id>p252</id>
      <version>1.0</version>
      <idea-version since-build="252" until-build="252.*"/>
      </idea-plugin>
    """.trimIndent())
    writeDescriptor("p253", """
      <idea-plugin>
      <id>p253</id>
      <version>1.0</version>
      <idea-version since-build="253" until-build="253.100"/>
      </idea-plugin>
    """.trimIndent())

    assertEnabledPluginsSetEquals(listOf("p252", "p253")) { buildNumber = "253.200" }
    assertEnabledPluginsSetEquals(listOf("p253")) {
      buildNumber = "253.200"
      withBrokenPlugin("p252", "1.0")
    }
    assertEnabledPluginsSetEquals(listOf("p252")) {
      buildNumber = "253.200"
      withBrokenPlugin("p253", "1.0")
    }
  }

  @Test
  fun `package prefix collision prevents plugin from loading`() {
    // FIXME these plugins are not related, but one of them loads => depends on implicit order
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "common.module" }
      }
    }.installAt(pluginsDirPath)
    plugin("bar") {
      content {
        module("bar.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "common.module" }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    assertThat(loadingErrors).hasSizeGreaterThan(0)
    assertThat(loadingErrors[0].htmlMessage.toString()).contains("conflicts with", "bar.module", "foo.module", "package prefix")
  }
  
  @Test
  fun `package prefix collision in plugin explicitly marked as incompatible`() {
    plugin("foo") {
      incompatibleWith = listOf("bar")
      content {
        module("foo.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "common.module" }
      }
    }.installAt(pluginsDirPath)
    plugin("bar") {
      content {
        module("bar.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "common.module" }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `package prefix collision prevents plugin from loading - same plugin`() {
    plugin("foo") {
      packagePrefix = "common.module"
      content {
        module("foo.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "common.module" }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    assertThat(loadingErrors).hasSizeGreaterThan(0)
    assertThat(loadingErrors[0].htmlMessage.toString()).contains("conflicts with", "foo.module", "package prefix")
  }

  @Test
  fun `package prefix collision does not prevent plugin from loading if module is optional`() {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL) { packagePrefix = "common.module" }
      }
    }.installAt(pluginsDirPath)
    plugin("bar") {
      content {
        module("bar.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL) { packagePrefix = "common.module" }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    // FIXME these plugins are not related, but one of them loads => depends on implicit order
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("foo.module")
    assertThat(loadingErrors).isNotEmpty()
    assertThat(loadingErrors[0].htmlMessage.toString()).contains("conflicts with", "bar", "foo.module", "package prefix")
  }

  @Test
  fun `content module without a package prefix nor isSeparateJar fails to load`() {
    plugin("foo") {
      content {
        module("foo.module") {}
      }
    }.installAt(pluginsDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.packageToMainJar: Boolean get() = true
    })
    assertThatThrownBy {
      buildPluginSet()
    }.hasMessageContaining("Package is not specified")
  }

  @Test
  fun `content module with a package prefix or separate jar loads`() {
    plugin("foo") {
      content {
        module("foo.module") { packagePrefix = "foo.module" }
      }
    }.installAt(pluginsDirPath)
    plugin("bar") {
      content {
        module("bar.module") { isSeparateJar = true }
      }
    }.installAt(pluginsDirPath)
    assertThat(buildPluginSet()).hasExactlyEnabledPlugins("foo", "bar")
  }

  @Test
  fun `id, version, name are inherited in depends sub-descriptors`() {
    plugin("foo") {}.installAt(pluginsDirPath)
    plugin("bar") {
      name = "Bar"
      version = "1.0.0"
      depends("foo", "foo.xml") {}
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar", "foo")
    val descriptor = pluginSet.getEnabledPlugin("bar")
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.name).isEqualTo("Bar")
    assertThat(descriptor.version).isEqualTo("1.0.0")
    assertThat(descriptor.dependencies).hasSize(1)
    val subDesc = descriptor.dependencies[0].subDescriptor!!
    assertThat(subDesc.pluginId.idString).isEqualTo("bar")
    assertThat(subDesc.name).isEqualTo("Bar")
    assertThat(subDesc.version).isEqualTo("1.0.0")
  }

  @Test
  fun `id, version, name can't be overridden in depends sub-descriptors`() {
    plugin("foo") {}.installAt(pluginsDirPath)
    plugin("bar") {
      name = "Bar"
      version = "1.0.0"
      depends("foo", "foo.xml") {
        id = "bar2"
        name = "Bar Sub"
        version = "2.0.0"
      }
    }.installAt(pluginsDirPath)

    val (pluginSet, errs) = runAndReturnWithLoggedErrors { buildPluginSet() }
    assertThat(errs.joinToString { it.message ?: "" }).isNotNull
      .contains("element 'version'", "element 'name'", "element 'id'")
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar", "foo")
    val descriptor = pluginSet.getEnabledPlugin("bar")
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.name).isEqualTo("Bar")
    assertThat(descriptor.version).isEqualTo("1.0.0")
    assertThat(descriptor.dependencies).hasSize(1)
    val subDesc = descriptor.dependencies[0].subDescriptor!!
    assertThat(subDesc.pluginId.idString).isEqualTo("bar")
    assertThat(subDesc.name).isEqualTo("Bar")
    assertThat(subDesc.version).isEqualTo("1.0.0")
  }

  @Test
  fun `resource bundle is inherited in depends sub-descriptors`() {
    plugin("foo") {}.installAt(pluginsDirPath)
    plugin("bar") {
      name = "Bar"
      resourceBundle = "resourceBundle"
      depends("foo", "foo.xml") {}
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar", "foo")
    val descriptor = pluginSet.getEnabledPlugin("bar")
    assertThat(descriptor.resourceBundleBaseName).isEqualTo("resourceBundle")
    assertThat(descriptor.dependencies).hasSize(1)
    val subDesc = descriptor.dependencies[0].subDescriptor!!
    assertThat(subDesc.resourceBundleBaseName).isEqualTo("resourceBundle")
  }

  @Test
  fun `resource bundle can be overridden in depends sub-descriptors`() {
    plugin("foo") {}.installAt(pluginsDirPath)
    plugin("bar") {
      name = "Bar"
      resourceBundle = "resourceBundle"
      depends("foo", "foo.xml") { resourceBundle = "sub" }
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar", "foo")
    val descriptor = pluginSet.getEnabledPlugin("bar")
    assertThat(descriptor.resourceBundleBaseName).isEqualTo("resourceBundle")
    assertThat(descriptor.dependencies).hasSize(1)
    val subDesc = descriptor.dependencies[0].subDescriptor!!
    assertThat(subDesc.resourceBundleBaseName).isEqualTo("sub")
  }

  @Test
  fun `additional core plugin aliases`() {
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("embedded.module", loadingRule = ModuleLoadingRuleValue.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "required" }
        module("optional.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL) { packagePrefix = "optional" }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet()
    val core = pluginSet.getEnabledPlugin("com.intellij")
    for (alias in IdeaPluginOsRequirement.getHostOsModuleIds() + productModeAliasesForCorePlugin()) {
      assertThat(pluginSet.findEnabledPlugin(alias)).isSameAs(core)
    }
  }

  @Test
  fun `findEnabledPlugin resolves plugin alias to declaring plugin`() {
    plugin("com.example.owner") {
      pluginAlias("com.example.owner.alias")
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet()
    val owner = pluginSet.getEnabledPlugin("com.example.owner")
    assertThat(pluginSet.findEnabledPlugin(PluginId.getId("com.example.owner.alias"))).isSameAs(owner)
  }

  @Test
  fun `plugin with duplicate content module fails to load`() {
    plugin("foo") {
      content {
        module("foo.module") { isSeparateJar = true }
        module("foo.module") { packagePrefix = "foo.module" }
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    assertThat(loadingErrors).hasSizeGreaterThan(0)
    assertThat(loadingErrors[0].htmlMessage.toString()).contains("foo", "invalid plugin descriptor")
  }

  @Test
  fun `test a module graph take into account aliases and sort them correctly`() {
    plugin("d") {
      content {
        module("d.a", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          dependencies {
            plugin("BBB")
          }
        }
      }
    }.installAt(pluginsDirPath)

    plugin("a") {
      content {
        module("a.a", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          dependencies {
            plugin("BBB")
          }
        }
      }
    }.installAt(pluginsDirPath)

    plugin("b") {
      content {
        module("b1", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        module("b2", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          pluginAlias("BBB")
          dependencies {
            module("b1")
          }
        }
      }
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("a", "b", "d")
  }

  @Test
  fun `test a fail of one required module leads to not loading of all plugins`() {
    plugin("d") {
      content {
        module("d.a", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          dependencies {
            plugin("BBB")
          }
        }
      }
    }.installAt(pluginsDirPath)

    plugin("a") {
      content {
        module("a.a", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          dependencies {
            plugin("BBB")
          }
        }
      }
    }.installAt(pluginsDirPath)

    plugin("b") {
      content {
        module("b1", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        module("b2", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          pluginAlias("BBB")
          dependencies {
            module("b1")
          }
        }
        module("b0", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          dependencies {
            module("unresolved")
          }
        }
      }
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins()
  }

  @Test
  fun testLoadDisabledPlugin() {
    plugin("disabled") { }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet {
      withDisabledPlugins("disabled")
    }
    val descriptor = pluginSet.getPlugin("disabled")
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    assertThat(descriptor).isNotMarkedEnabled()
  }

  @Test
  fun `dependency on a plugin alias in core content module from a plugin required content module is allowed`() {
    // note: result can be order-dependent
    val rnd = Random(239)
    val ids = (1..20).map { rnd.nextInt().absoluteValue.toString(36) }.distinct()
    for (id in ids) {
      plugin("intellij.textmate.$id") {
        content {
          module("intellij.textmate.impl.$id", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies {
              plugin("com.intellij.modules.spellchecker")
            }
          }
        }
      }.installAt(pluginsDirPath)
    }
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("intellij.spellchecker") {
          isSeparateJar = true
          pluginAlias("com.intellij.modules.spellchecker")
        }
        module("intellij.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
      }
    }.installAt(pluginsDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, *ids.map { "intellij.textmate.$it" }.toTypedArray())
  }

  @Test
  fun `getEnabledModules honors module dependencies`() {
    plugin("com.intellij") {
      pluginAlias("com.intellij.modules.microservices")
    }.installAt(pluginsDirPath)

    plugin("com.intellij.microservices.ui") {
      name = "Endpoints"
      vendor = "JetBrains"
      category = "Microservices"
      dependencies {
        plugin("com.intellij.modules.microservices")
      }
    }.installAt(pluginsDirPath)

    plugin("com.jetbrains.restClient") {
      name = "HTTP Client"
      category = "Other Tools"
      vendor = "JetBrains"
      dependencies {
        plugin("com.intellij.modules.microservices")
      }
      content {
        module("intellij.restClient.microservicesUI") {
          dependencies {
            plugin("com.intellij.microservices.ui")
          }
        }
      }
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet()
    assertThat(loadingErrors).isEmpty()
    val moduleOrder = pluginSet.getEnabledModules().map { it.getPluginId().idString + ":" + it.contentModuleName }
    val validOrders = listOf(
      listOf(
        "com.intellij:null",
        "com.jetbrains.restClient:null",
        "com.intellij.microservices.ui:null",
        "com.jetbrains.restClient:intellij.restClient.microservicesUI"
      ),
      listOf(
        "com.intellij:null",
        "com.intellij.microservices.ui:null",
        "com.jetbrains.restClient:null",
        "com.jetbrains.restClient:intellij.restClient.microservicesUI"
      )
    ) // both are correct
    assert(moduleOrder in validOrders) { "Invalid module order: $moduleOrder" }
  }

  @Test
  fun `incompatible-with's origin gets excluded instead of target`() {
    plugin("foo") {}.installAt(pluginsDirPath)
    plugin("bar") {
      incompatibleWith = listOf("foo")
    }.installAt(pluginsDirPath)

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    assertThat(loadingErrors).hasSize(1)
    val error = loadingErrors[0]
    assertThat(error.htmlMessage.toString()).contains("bar", "not compatible", "foo")
  }

  private fun writeDescriptor(id: String, @Language("xml") data: String) {
    pluginsDirPath.resolve(id)
      .resolve(PluginManagerCore.PLUGIN_XML_PATH)
      .write(data.trimIndent())
  }

  private fun assertEnabledPluginsSetEquals(enabledIds: List<String>, builder: PluginSetTestBuilder.() -> Unit) {
    val pluginSet = buildPluginSet(builder)
    assertThat(pluginSet).hasExactlyEnabledPlugins(*enabledIds.toTypedArray())
  }

  private fun buildPluginSet(builder: PluginSetTestBuilder.() -> Unit = {}): PluginSet {
    val state = PluginSetTestBuilder.fromPath(pluginsDirPath).apply(builder).buildState()
    loadingErrors = state.loadingErrors
    return state.pluginSet
  }
}
