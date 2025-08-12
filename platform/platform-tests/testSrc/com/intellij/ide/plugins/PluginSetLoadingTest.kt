// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginMainDescriptor.Companion.productModeAliasesForCorePlugin
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.*
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
  }

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginsDirPath get() = rootPath.resolve("wd/plugins")

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

    val (_, result) = PluginSetTestBuilder.fromPath(pluginsDirPath)
      .withDisabledPlugins("foo")
      .buildLoadingResult()

    val incompletePlugins = result.getIncompleteIdMap().values
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

    val (_, result) = PluginSetTestBuilder.fromPath(pluginsDirPath)
      .withProductBuildNumber(BuildNumber.fromString("4.0")!!)
      .buildLoadingResult()

    assertThat(result.hasPluginErrors).isFalse()
    val plugins = result.enabledPlugins.toList()
    assertThat(plugins).hasSize(1)
    assertThat(result.duplicateModuleMap).isNull()
    assertThat(result.getIncompleteIdMap()).isEmpty()
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(result.getIdMap()).containsOnlyKeys(foo.pluginId)
    assertThat(result.getIdMap().get(foo.pluginId)).isSameAs(foo)
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
    plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo_1-0"))
    plugin("foo") { version = "1.0" }.buildDir(pluginsDirPath.resolve("foo_another"))

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
        module("foo.module", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "common.module" }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))
    plugin("bar") {
      content {
        module("bar.module", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "common.module" }
      }
    }.buildDir(pluginsDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).hasSizeGreaterThan(0)
    assertThat(errors[0].get().toString()).contains("conflicts with", "bar.module", "foo.module", "package prefix")
  }
  
  @Test
  fun `package prefix collision in plugin explicitly marked as incompatible`() {
    plugin("foo") {
      incompatibleWith = listOf("bar")
      content {
        module("foo.module", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "common.module" }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))
    plugin("bar") {
      content {
        module("bar.module", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "common.module" }
      }
    }.buildDir(pluginsDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `package prefix collision prevents plugin from loading - same plugin`() {
    plugin("foo") {
      packagePrefix = "common.module"
      content {
        module("foo.module", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "common.module" }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).hasSizeGreaterThan(0)
    assertThat(errors[0].get().toString()).contains("conflicts with", "foo.module", "package prefix")
  }

  @Test
  fun `package prefix collision does not prevent plugin from loading if module is optional`() {
    plugin("foo") {
      content {
        module("foo.module", loadingRule = ModuleLoadingRule.OPTIONAL) { packagePrefix = "common.module" }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))
    plugin("bar") {
      content {
        module("bar.module", loadingRule = ModuleLoadingRule.OPTIONAL) { packagePrefix = "common.module" }
      }
    }.buildDir(pluginsDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    // FIXME these plugins are not related, but one of them loads => depends on implicit order
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("foo.module")
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).isNotEmpty()
    assertThat(errors[0].get().toString()).contains("conflicts with", "bar", "foo.module", "package prefix")
  }

  @Test
  fun `content module without a package prefix nor isSeparateJar fails to load`() {
    plugin("foo") {
      content {
        module("foo.module") {}
      }
    }.buildDir(pluginsDirPath.resolve("foo"), object : PluginPackagingConfig() {
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
    }.buildDir(pluginsDirPath.resolve("foo"))
    plugin("bar") {
      content {
        module("bar.module") { isSeparateJar = true }
      }
    }.buildDir(pluginsDirPath.resolve("bar"))
    assertThat(buildPluginSet()).hasExactlyEnabledPlugins("foo", "bar")
  }

  @Test
  fun `id, version, name are inherited in depends sub-descriptors`() {
    plugin("foo") {}.buildDir(pluginsDirPath.resolve("foo"))
    plugin("bar") {
      name = "Bar"
      version = "1.0.0"
      depends("foo", "foo.xml") {}
    }.buildDir(pluginsDirPath.resolve("bar"))

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
    plugin("foo") {}.buildDir(pluginsDirPath.resolve("foo"))
    plugin("bar") {
      name = "Bar"
      version = "1.0.0"
      depends("foo", "foo.xml") {
        id = "bar2"
        name = "Bar Sub"
        version = "2.0.0"
      }
    }.buildDir(pluginsDirPath.resolve("bar"))

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
    plugin("foo") {}.buildDir(pluginsDirPath.resolve("foo"))
    plugin("bar") {
      name = "Bar"
      resourceBundle = "resourceBundle"
      depends("foo", "foo.xml") {}
    }.buildDir(pluginsDirPath.resolve("bar"))

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
    plugin("foo") {}.buildDir(pluginsDirPath.resolve("foo"))
    plugin("bar") {
      name = "Bar"
      resourceBundle = "resourceBundle"
      depends("foo", "foo.xml") { resourceBundle = "sub" }
    }.buildDir(pluginsDirPath.resolve("bar"))

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
        module("embedded.module", loadingRule = ModuleLoadingRule.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "required" }
        module("optional.module", loadingRule = ModuleLoadingRule.OPTIONAL) { packagePrefix = "optional" }
      }
    }.buildDir(pluginsDirPath.resolve("core"))
    val pluginSet = buildPluginSet()
    val core = pluginSet.getEnabledPlugin("com.intellij")
    for (alias in IdeaPluginOsRequirement.getHostOsModuleIds() + productModeAliasesForCorePlugin()) {
      assertThat(pluginSet.findEnabledPlugin(alias)).isSameAs(core)
    }
  }

  @Test
  fun `plugin with duplicate content module fails to load`() {
    plugin("foo") {
      content {
        module("foo.module") { isSeparateJar = true }
        module("foo.module") { packagePrefix = "foo.module" }
      }
    }.buildDir(pluginsDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).hasSizeGreaterThan(0)
    assertThat(errors[0].get().toString()).contains("foo", "duplicate", "content module")
  }

  @Test
  fun testLoadDisabledPlugin() {
    plugin("disabled") { }.buildDir(pluginsDirPath.resolve("disabled"))
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
          module("intellij.textmate.impl.$id", loadingRule = ModuleLoadingRule.REQUIRED) {
            dependencies {
              plugin("com.intellij.modules.spellchecker")
            }
          }
        }
      }.buildDir(pluginsDirPath.resolve("foo.$id"))
    }
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("intellij.spellchecker") {
          isSeparateJar = true
          pluginAlias("com.intellij.modules.spellchecker")
        }
        module("intellij.required", loadingRule = ModuleLoadingRule.REQUIRED) {}
      }
    }.buildDir(pluginsDirPath.resolve("core"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, *ids.map { "intellij.textmate.$it" }.toTypedArray())
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

  private fun buildPluginSet(builder: PluginSetTestBuilder.() -> Unit = {}): PluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath).apply(builder).build()
}