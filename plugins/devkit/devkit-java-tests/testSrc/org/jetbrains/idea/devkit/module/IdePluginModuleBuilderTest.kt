// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module

import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.setupTestModule
import com.intellij.ide.starters.shared.JAVA_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_17
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4
import org.jetbrains.idea.devkit.module.IdePluginModuleBuilder.PluginType
import org.junit.Assert.assertFalse
import org.junit.Test

class IdePluginModuleBuilderTest : LightJavaCodeInsightFixtureTestCase4(JAVA_17) {
  @Test
  fun pluginKotlinProject() {
    IdePluginModuleBuilder().setupTestModule(fixture.module) {
      language = KOTLIN_STARTER_LANGUAGE
      isCreatingNewProject = true
    }

    fixture.configureFromTempProjectFile("build.gradle.kts")
    val buildGradleText = fixture.editor.document.text

    assertFalse("build.gradle.kts contains unprocessed VTL directives", buildGradleText.contains("\${context"))

    expectFile("src/main/resources/META-INF/plugin.xml", PLUGIN_XML)

    expectFile("settings.gradle.kts", """
      pluginManagement {
          repositories {
              mavenCentral()
              gradlePluginPortal()
          }
      }

      rootProject.name = "demo"
    """.trimIndent())
  }

  @Test
  fun themeProject() {
    val builder = IdePluginModuleBuilder()
    builder.setPluginType(PluginType.THEME)

    builder.setupTestModule(fixture.module) {
      language = JAVA_STARTER_LANGUAGE
      isCreatingNewProject = true
    }

    expectFile("resources/META-INF/plugin.xml", THEME_PLUGIN_XML)
    expectFile("resources/theme/demo.theme.json", THEME_JSON)
  }

  private val PLUGIN_XML: String = """
    <!-- Plugin Configuration File. Read more: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html -->
    <idea-plugin>
        <!-- Unique identifier of the plugin. It should be FQN. It cannot be changed between the plugin versions. -->
        <id>com.example.demo</id>
    
        <!-- Public plugin name should be written in Title Case.
             Guidelines: https://plugins.jetbrains.com/docs/marketplace/plugin-overview-page.html#plugin-name -->
        <name>Demo</name>
    
        <!-- A displayed Vendor name or Organization ID displayed on the Plugins Page. -->
        <vendor email="support@yourcompany.com" url="https://www.yourcompany.com">YourCompany</vendor>
    
        <!-- Description of the plugin displayed on the Plugin Page and IDE Plugin Manager.
             Simple HTML elements (text formatting, paragraphs, and lists) can be added inside of <![CDATA[ ]]> tag.
             Guidelines: https://plugins.jetbrains.com/docs/marketplace/plugin-overview-page.html#plugin-description -->
        <description><![CDATA[
        Enter short description for your plugin here.<br>
        <em>most HTML tags may be used</em>
      ]]></description>
    
        <!-- Product and plugin compatibility requirements.
             Read more: https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html -->
        <depends>com.intellij.modules.platform</depends>
    
        <!-- Extension points defined by the plugin.
             Read more: https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html -->
        <extensions defaultExtensionNs="com.intellij">
    
        </extensions>
    </idea-plugin>
  """.trimIndent()

  private val THEME_PLUGIN_XML: String = """
    <!-- Plugin Configuration File. Read more: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html -->
    <idea-plugin>
        <!-- Unique identifier of the plugin. It should be FQN. It cannot be changed between the plugin versions. -->
        <id>demo</id>
        <version>1.0.0</version>

        <!-- Public plugin name should be written in Title Case.
             Guidelines: https://plugins.jetbrains.com/docs/marketplace/plugin-overview-page.html#plugin-name -->
        <name>Demo</name>
        <category>UI</category>

        <!-- A displayed Vendor name or Organization ID displayed on the Plugins Page. -->
        <vendor email="support@yourcompany.com" url="https://www.yourcompany.com">YourCompany</vendor>

        <idea-version since-build="241" until-build="243.*"/>

        <!-- Description of the plugin displayed on the Plugin Page and IDE Plugin Manager.
             Simple HTML elements (text formatting, paragraphs, and lists) can be added inside of <![CDATA[ ]]> tag.
             Guidelines: https://plugins.jetbrains.com/docs/marketplace/plugin-overview-page.html#plugin-description -->
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
  """.trimIndent()

  private val THEME_JSON: String = """
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
  """.trimIndent()

  private fun expectFile(path: String, content: String) {
    fixture.configureFromTempProjectFile(path)
    fixture.checkResult(content)
  }
}