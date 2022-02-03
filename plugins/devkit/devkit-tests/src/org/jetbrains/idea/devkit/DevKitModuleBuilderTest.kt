// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit

import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.setupTestModule
import com.intellij.ide.starters.shared.JAVA_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4
import org.jetbrains.idea.devkit.module.DevKitModuleBuilder
import org.junit.Assert.assertFalse
import org.junit.Test

class DevKitModuleBuilderTest : LightJavaCodeInsightFixtureTestCase4(JAVA_11) {
  @Test
  fun pluginJavaProject() {
    DevKitModuleBuilder().setupTestModule(fixture.module) {
      language = JAVA_STARTER_LANGUAGE
      isCreatingNewProject = true
    }

    fixture.configureFromTempProjectFile("build.gradle.kts")
    val buildGradleText = fixture.editor.document.text

    assertFalse("build.gradle.kts contains unprocessed VTL directives", buildGradleText.contains("\${context"))
    assertFalse("build.gradle.kts contains kotlin in Java project", buildGradleText.contains("kotlin"))

    expectFile("src/main/resources/META-INF/plugin.xml", PLUGIN_XML)

    expectFile("settings.gradle.kts", """
      rootProject.name = "demo"
    """.trimIndent())
  }

  @Test
  fun pluginKotlinProject() {
    DevKitModuleBuilder().setupTestModule(fixture.module) {
      language = KOTLIN_STARTER_LANGUAGE
      isCreatingNewProject = true
    }

    fixture.configureFromTempProjectFile("build.gradle.kts")
    val buildGradleText = fixture.editor.document.text

    assertFalse("build.gradle.kts contains unprocessed VTL directives", buildGradleText.contains("\${context"))

    expectFile("src/main/resources/META-INF/plugin.xml", PLUGIN_XML)

    expectFile("settings.gradle.kts", """
      rootProject.name = "demo"
    """.trimIndent())
  }

  private val PLUGIN_XML: String = """
    <!-- Plugin Configuration File. Read more: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html -->
    <idea-plugin>
        <id>com.example.demo</id>
        <name>Demo</name>
        <vendor email="support@yourcompany.com" url="http://www.yourcompany.com">YourCompany</vendor>

        <description><![CDATA[
        Enter short description for your plugin here.<br>
        <em>most HTML tags may be used</em>
      ]]></description>

        <!-- please see https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html
             on how to target different products -->
        <depends>com.intellij.modules.platform</depends>

        <extensions defaultExtensionNs="com.intellij">

        </extensions>
    </idea-plugin>
  """.trimIndent()

  private fun expectFile(path: String, content: String) {
    fixture.configureFromTempProjectFile(path)
    fixture.checkResult(content)
  }
}