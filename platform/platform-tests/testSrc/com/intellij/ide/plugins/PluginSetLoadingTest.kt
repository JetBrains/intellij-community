// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.write
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import java.util.function.Function

class PluginSetLoadingTest {
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

    val pluginSet = PluginSetTestBuilder(pluginsDirPath).build()
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

    val result = PluginSetTestBuilder(pluginsDirPath)
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

    val result = PluginSetTestBuilder(pluginsDirPath)
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

    val pluginSet = PluginSetTestBuilder(pluginsDirPath)
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
    PluginBuilder.empty().id("foo").version("1.0").build(pluginsDirPath.resolve("foo_1-0"))
    PluginBuilder.empty().id("foo").version("1.0").build(pluginsDirPath.resolve("foo_another"))

    val pluginSet = PluginSetTestBuilder(pluginsDirPath).build()
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("1.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Test
  fun `until build is honored only if it targets 243 and earlier`() {
    fun addDescriptor(build: String) = writeDescriptor("p$build", """
    <idea-plugin>
      <id>p$build</id>
      <version>1.0</version>
      <idea-version since-build="$build" until-build="$build.100"/>
    </idea-plugin>
    """.trimIndent())

    addDescriptor("243")
    addDescriptor("251")
    addDescriptor("252")
    addDescriptor("261")

    assertEnabledPluginsSetEquals(listOf("p243")) { buildNumber = "243.10" }
    assertEnabledPluginsSetEquals(listOf("p251")) { buildNumber = "251.10" }
    assertEnabledPluginsSetEquals(listOf("p251", "p252")) { buildNumber = "252.200" }
    assertEnabledPluginsSetEquals(listOf("p251", "p252", "p261")) { buildNumber = "261.200" }
  }

  @Test
  fun `broken plugins is honored while until build is not`() {
    writeDescriptor("p251", """
      <idea-plugin>
      <id>p251</id>
      <version>1.0</version>
      <idea-version since-build="251" until-build="251.100"/>
      </idea-plugin>
    """.trimIndent())
    writeDescriptor("p252", """
      <idea-plugin>
      <id>p252</id>
      <version>1.0</version>
      <idea-version since-build="252" until-build="252.100"/>
      </idea-plugin>
    """.trimIndent())

    assertEnabledPluginsSetEquals(listOf("p251", "p252")) { buildNumber = "252.200" }
    assertEnabledPluginsSetEquals(listOf("p252")) {
      buildNumber = "252.200"
      withBrokenPlugin("p251", "1.0")
    }
    assertEnabledPluginsSetEquals(listOf("p251")) {
      buildNumber = "252.200"
      withBrokenPlugin("p252", "1.0")
    }
  }

  private fun writeDescriptor(id: String, @Language("xml") data: String) {
    pluginsDirPath.resolve(id)
      .resolve(PluginManagerCore.PLUGIN_XML_PATH)
      .write(data.trimIndent())
  }

  private fun assertEnabledPluginsSetEquals(enabledIds: List<String>, builder: PluginSetTestBuilder.() -> Unit) {
    val pluginSet = PluginSetTestBuilder(pluginsDirPath).apply(builder).build()
    assertThat(pluginSet.enabledPlugins)
      .hasSize(enabledIds.size)
    assertThat(pluginSet.enabledPlugins.map { it.pluginId.idString })
      .containsExactlyInAnyOrderElementsOf(enabledIds)
  }
}