package com.intellij.settingsSync.core

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.getEnabledPlugin
import com.intellij.idea.TestFor
import com.intellij.openapi.components.SettingsCategory
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.PluginSpecBuilder
import com.intellij.platform.testFramework.plugins.buildDir
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginCategoryFinder
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test

class SettingsSyncPluginCategoryFinderTest {

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  @Test
  @TestFor(issues = ["IJPL-157044"])
  fun `test plugin with UI category gets a UI settings category`() {
    checkCategory({ category = "Theme" }, SettingsCategory.UI)
  }

  @Test
  @TestFor(issues = ["IJPL-157044"])
  fun `test plugin with UI extensions gets a UI settings category`() {
    checkCategory({ extensions("""<themeProvider id="some_id"/>""", ns = "com.intellij") }, SettingsCategory.UI)
  }

  @Test
  @TestFor(issues = ["IJPL-157044"])
  fun `test plugin with non-UI category and without extensions gets PLUGINS settings category`() {
    checkCategory({ category = "AI-Powered" }, SettingsCategory.PLUGINS)
  }

  @Test
  @TestFor(issues = ["IJPL-157044"])
  fun `test plugin without category and extensions gets PLUGINS settings category`() {
    checkCategory({ }, SettingsCategory.PLUGINS)
  }

  @Test
  @TestFor(issues = ["IJPL-157044"])
  fun `test plugin with non-UI category and only ui extensions gets PLUGINS settings category`() {
    checkCategory({ category = "AI-Powered"; extensions("""<themeProvider id="some_id"/>""", ns = "com.intellij") }, SettingsCategory.PLUGINS)
  }

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginDirPath get() = rootPath.resolve("plugin")

  private fun pluginDescriptor(body: PluginSpecBuilder.() -> Unit): IdeaPluginDescriptorImpl {
    plugin("plugin") {
      body()
    }.buildDir(pluginDirPath.resolve("plugin"))
    val pluginSet = buildPluginSet()
    return pluginSet.getEnabledPlugin("plugin")
  }

  private fun checkCategory(pluginSpecBuilderBody: PluginSpecBuilder.() -> Unit, expectedCategory: SettingsCategory) {
    val category = SettingsSyncPluginCategoryFinder.getPluginCategory(pluginDescriptor { pluginSpecBuilderBody() })
    assertThat(category).isEqualTo(expectedCategory)
  }

  private fun buildPluginSet() =
    PluginSetTestBuilder.fromPath(pluginDirPath)
      .build()
}