// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module

import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.setupTestModule
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4
import com.intellij.testFramework.utils.editor.getVirtualFile
import org.jetbrains.idea.devkit.module.IdePluginModuleBuilder.PluginType
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertIterableEquals

private const val PLUGIN_XML_LOCATION = "src/main/resources/META-INF/plugin.xml"

private val BUNDLED_PLUGIN_REGEX = Regex("bundledPlugin\\(\"(.+?)\"\\)")
private val PLUGIN_REGEX = Regex("compatiblePlugin\\(\"(.+?)\"\\)")

class IdePluginModuleBuilderTest : LightJavaCodeInsightFixtureTestCase4(JAVA_21) {

  private fun assertNoUnprocessedTemplates() {
    val text = fixture.editor.document.text
    assertFalse("${fixture.editor.document.getVirtualFile()} contains unprocessed VTL directives", text.contains($$"${context"))
  }

  private fun expectFile(path: String, content: String) {
    fixture.configureFromTempProjectFile(path)
    fixture.checkResult(content)
  }

  private fun genModuleWithDependencies(vararg dependencies: String) {
    IdePluginModuleBuilder().setupTestModule(fixture.module) {
      isCreatingNewProject = true
      libraryIds.addAll(dependencies)
    }
  }

  private fun assertBuildGradlePlugins(vararg expectedPluginIds: String) {
    fixture.configureFromTempProjectFile("build.gradle.kts")
    assertNoUnprocessedTemplates()

    val text = fixture.editor.document.text
    val bundledPluginIds = BUNDLED_PLUGIN_REGEX.findAll(text)
      .map { it.groupValues[1] }
      .toList()

    val pluginIds = PLUGIN_REGEX.findAll(text)
      .map { it.groupValues[1] }
      .toList()

    assertIterableEquals(expectedPluginIds.toList(), bundledPluginIds + pluginIds, "plugins of build.gradle.kts do not match")
  }

  private fun assertPluginXmlDependencies(vararg libraries: String) {
    fixture.configureFromTempProjectFile(PLUGIN_XML_LOCATION)
    assertNoUnprocessedTemplates()

    val text = fixture.editor.document.text

    val dependencies = Regex("<depends>(.+?)</depends>\\s*")
      .findAll(text)
      .map { it.groupValues[1].trim() }
      .filter { it.isNotBlank() }
      .toList()

    assertIterableEquals(libraries.toList(), dependencies, "dependencies of plugin.xml do not match")
  }

  @Test
  fun pluginKotlinProject() {
    IdePluginModuleBuilder().setupTestModule(fixture.module) {
      isCreatingNewProject = true
    }

    fixture.configureFromTempProjectFile("build.gradle.kts")
    assertNoUnprocessedTemplates()

    expectFile(PLUGIN_XML_LOCATION,
               /* language=XML */
               """
      <!-- Plugin Configuration File: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html -->
      <idea-plugin>
          <!-- Unique identifier of the plugin. It should be FQN, cannot be changed between the plugin versions. -->
          <id>com.example.demo</id>
      
          <!-- Public plugin name should be written in Title Case.
               Guidelines: https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#plugin-name -->
          <name>Demo</name>
      
          <!-- A displayed Vendor name or Organization ID displayed on the Plugins Page. -->
          <vendor url="https://www.yourcompany.com">YourCompany</vendor>
      
          <!-- Description of the plugin displayed on the Plugin Page and IDE Plugin Manager.
               Guidelines: https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#plugin-description -->
          <description><![CDATA[
              Enter short description for your plugin here.<br>
              <em>most HTML tags may be used</em>
          ]]></description>
      
          <!-- Product and plugin compatibility requirements.
               Read more: https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html -->
          <depends>com.intellij.modules.platform</depends>
      
          <resource-bundle>messages.MyMessageBundle</resource-bundle>
          <!-- Extensions defined by the plugin.
               Read more: https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html -->
          <extensions defaultExtensionNs="com.intellij">
              <toolWindow id="MyToolWindow" factoryClass="com.example.demo.MyToolWindowFactory"
                          icon="AllIcons.Toolwindows.ToolWindowPalette"/>
          </extensions>
      </idea-plugin>
    """.trimIndent())

    expectFile("settings.gradle.kts", """
      rootProject.name = "demo"
    """.trimIndent())
  }

  @Test
  fun pluginComposeAndKotlinDependencies() {
    genModuleWithDependencies("compose", "kotlin")

    fixture.configureFromTempProjectFile("build.gradle.kts")
    assertNoUnprocessedTemplates()

    assertBuildGradlePlugins("org.jetbrains.kotlin")

    assertPluginXmlDependencies(
      "com.intellij.modules.compose",
      "org.jetbrains.kotlin"
    )
  }

  @Test
  fun pluginJavaDependencies() {
    genModuleWithDependencies("java")

    assertBuildGradlePlugins("com.intellij.java")

    assertPluginXmlDependencies(
      "com.intellij.java"
    )
  }

  @Test
  fun pluginJavaScriptAndPythonDependencies() {
    genModuleWithDependencies("javascript", "python")

    assertBuildGradlePlugins("JavaScript", "PythonCore")

    assertPluginXmlDependencies(
      "JavaScript",
      "PythonCore"
    )
  }

  @Test
  fun pluginMarkdownAndJsonDependencies() {
    genModuleWithDependencies("json", "markdown")

    assertBuildGradlePlugins("com.intellij.modules.json", "org.intellij.plugins.markdown")

    assertPluginXmlDependencies(
      "com.intellij.modules.json",
      "org.intellij.plugins.markdown"
    )
  }

  @Test
  fun pluginLspDependencies() {
    genModuleWithDependencies("lsp")

    fixture.configureFromTempProjectFile("build.gradle.kts")
    assertNoUnprocessedTemplates()

    assertPluginXmlDependencies(
      "com.intellij.modules.lsp"
    )
  }

  @Test
  fun pluginGoYamlDependencies() {
    genModuleWithDependencies("go", "yaml")

    fixture.configureFromTempProjectFile("build.gradle.kts")
    assertBuildGradlePlugins("org.jetbrains.plugins.yaml", "org.jetbrains.plugins.go")

    assertPluginXmlDependencies(
      "org.jetbrains.plugins.yaml",
      "org.jetbrains.plugins.go"
    )
  }

  @Test
  fun pluginPhpXmlDatabaseDependencies() {
    genModuleWithDependencies("xml", "php", "database")

    fixture.configureFromTempProjectFile("build.gradle.kts")
    assertBuildGradlePlugins("com.intellij.database", "com.jetbrains.php")

    assertPluginXmlDependencies(
      "com.intellij.modules.xml",
      "com.jetbrains.php",
      "com.intellij.database"
    )
  }

  @Test
  fun pluginRubyDependencies() {
    genModuleWithDependencies("ruby")

    fixture.configureFromTempProjectFile("build.gradle.kts")
    assertBuildGradlePlugins("org.jetbrains.plugins.ruby")

    assertPluginXmlDependencies(
      "org.jetbrains.plugins.ruby"
    )
  }

  @Test
  fun pluginPropertiesDependencies() {
    genModuleWithDependencies("properties")

    fixture.configureFromTempProjectFile("build.gradle.kts")
    assertBuildGradlePlugins("com.intellij.properties")

    assertPluginXmlDependencies(
      "com.intellij.properties"
    )
  }

  @Test
  fun themeProject() {
    val builder = IdePluginModuleBuilder()
    builder.setPluginType(PluginType.THEME)

    builder.setupTestModule(fixture.module) {
      isCreatingNewProject = true
    }

    expectFile("resources/META-INF/plugin.xml",
               /* language=XML */
               """
      <!-- Plugin Configuration File. Read more: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html -->
      <idea-plugin>
          <!-- Unique identifier of the plugin. It should be FQN. It cannot be changed between the plugin versions. -->
          <id>demo</id>
          <version>1.0.0</version>
      
          <!-- Public plugin name should be written in Title Case.
               Guidelines: https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#plugin-name -->
          <name>Demo</name>
          <category>UI</category>
      
          <!-- A displayed Vendor name or Organization ID displayed on the Plugins Page. -->
          <vendor url="https://www.yourcompany.com">YourCompany</vendor>
      
          <idea-version since-build="253"/>
      
          <!-- Description of the plugin displayed on the Plugin Page and IDE Plugin Manager.
               Guidelines: https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#plugin-description -->
          <description><![CDATA[
              Enter short description for your theme here.<br>
              <em>most HTML tags may be used</em>
          ]]></description>
      
          <!-- Short summary of new features and bugfixes in the latest plugin version.
               Displayed on the Plugin Page and IDE Plugin Manager. Simple HTML elements can be included between <![CDATA[  ]]> tags. -->
          <change-notes><![CDATA[
              Initial release of the theme.
          ]]></change-notes>
      
          <!-- Product and plugin compatibility requirements.
               Read more: https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html -->
          <depends>com.intellij.modules.platform</depends>
      
          <extensions defaultExtensionNs="com.intellij">
              <themeProvider id="demo" path="/theme/demo.theme.json"/>
          </extensions>
      </idea-plugin>
    """.trimIndent())

    expectFile("resources/theme/demo.theme.json",
               /* language=JSON */
               """
      {
        "name": "Demo",
        "author": "YourCompany",
        "dark": true,
        "colors": {
          "primaryRed": "#821010"
        },
        "ui": {
          "Button.background": "primaryRed"
        }
      }
    """.trimIndent())
  }
}