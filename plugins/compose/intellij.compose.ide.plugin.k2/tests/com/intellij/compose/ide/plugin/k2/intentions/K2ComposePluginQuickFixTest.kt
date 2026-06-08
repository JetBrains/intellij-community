// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.intentions

import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile

internal class K2ComposePluginQuickFixTest : KotlinLightCodeInsightFixtureTestCase() {

  private fun runQuickFixTest(initialCode: String, expectedCode: String) {
    val file = myFixture.configureByText("build.gradle.kts", initialCode.trimIndent()) as KtFile

    WriteCommandAction.runWriteCommandAction(project) {
      val success = file.addComposeCompilerPlugin()
      assertTrue("QuickFix failed to apply!", success)
    }

    myFixture.checkResult(expectedCode.trimIndent())
  }

  private fun runQuickFixTestWithIndent(indentSize: Int, initialCode: String, expectedCode: String) {
    configureCodeStyleAndRun(project, configurator = { settings ->
      settings.getCommonSettings(KotlinLanguage.INSTANCE).indentOptions?.INDENT_SIZE = indentSize
    }) {
      runQuickFixTest(initialCode, expectedCode)
    }
  }

  fun `test add to completely empty file`() = runQuickFixTestWithIndent(
    indentSize = 4,
    initialCode = "",
    expectedCode = """
      plugins {
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to file with no plugins block but other content`() = runQuickFixTest(
    initialCode = """
      dependencies {
          implementation("androidx.core:core-ktx:1.9.0")
      }
    """,
    expectedCode = """
      plugins {
          id("org.jetbrains.kotlin.plugin.compose")
      }
      
      dependencies {
          implementation("androidx.core:core-ktx:1.9.0")
      }
    """
  )

  fun `test add to existing empty plugins block`() = runQuickFixTest(
    initialCode = """
      plugins {
      }
    """,
    expectedCode = """
      plugins {
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to existing plugins block with other plugins`() = runQuickFixTest(
    initialCode = """
      plugins {
          id("com.android.application")
          kotlin("android")
      }
    """,
    expectedCode = """
      plugins {
          id("com.android.application")
          kotlin("android")
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to existing plugins block with inline lambda argument`() = runQuickFixTest(
    initialCode = """
      plugins { 
          id("com.android.application") 
      }
    """,
    expectedCode = """
      plugins { 
          id("com.android.application")
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to plugins block with comments`() = runQuickFixTest(
    initialCode = """
      plugins {
          // Android plugin
          id("com.android.application")
          // Kotlin plugin
          kotlin("android")
      }
    """,
    expectedCode = """
      plugins {
          // Android plugin
          id("com.android.application")
          // Kotlin plugin
          kotlin("android")
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to plugins block with comment at last line`() = runQuickFixTest(
    initialCode = """
      plugins {
          // Android plugin
          id("com.android.application")
          // Kotlin plugin
          kotlin("android")
          // Comment
      }
    """,
    expectedCode = """
      plugins {
          // Android plugin
          id("com.android.application")
          // Kotlin plugin
          kotlin("android")
          // Comment
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to plugins block with version catalog alias`() = runQuickFixTest(
    initialCode = """
      plugins {
          alias(libs.plugins.android.application)
          alias(libs.plugins.kotlin.android)
      }
    """,
    expectedCode = """
      plugins {
          alias(libs.plugins.android.application)
          alias(libs.plugins.kotlin.android)
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to file with only buildscript block`() = runQuickFixTest(
    initialCode = """
      buildscript {
          repositories {
              google()
          }
      }
    """,
    expectedCode = """
      plugins {
          id("org.jetbrains.kotlin.plugin.compose")
      }
      
      buildscript {
          repositories {
              google()
          }
      }
    """
  )

  fun `test add to plugins block with apply false`() = runQuickFixTest(
    initialCode = """
      plugins {
          id("com.android.application") apply false
          id("org.jetbrains.kotlin.android") apply false
      }
    """,
    expectedCode = """
      plugins {
          id("com.android.application") apply false
          id("org.jetbrains.kotlin.android") apply false
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to plugins block with version specification`() = runQuickFixTest(
    initialCode = """
      plugins {
          id("com.android.application") version "8.0.0"
          kotlin("android") version "1.9.0"
      }
    """,
    expectedCode = """
      plugins {
          id("com.android.application") version "8.0.0"
          kotlin("android") version "1.9.0"
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to plugins block with apply false infix`() = runQuickFixTest(
    initialCode = """
      plugins {
          id("com.android.application").apply(false)
          id("org.jetbrains.kotlin.android").apply(false)
      }
    """,
    expectedCode = """
      plugins {
          id("com.android.application").apply(false)
          id("org.jetbrains.kotlin.android").apply(false)
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to plugins block with version specification infix`() = runQuickFixTest(
    initialCode = """
      plugins {
          id("com.android.application").version("8.0.0")
          kotlin("android").version("1.9.0")
      }
    """,
    expectedCode = """
      plugins {
          id("com.android.application").version("8.0.0")
          kotlin("android").version("1.9.0")
          id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add new plugins block with 2-space indent`() = runQuickFixTestWithIndent(
    indentSize = 2,
    initialCode = """
      dependencies {
        implementation("androidx.core:core-ktx:1.9.0")
      }
    """,
    expectedCode = """
      plugins {
        id("org.jetbrains.kotlin.plugin.compose")
      }
      
      dependencies {
        implementation("androidx.core:core-ktx:1.9.0")
      }
    """
  )

  fun `test add to existing plugins block with 2-space indent`() = runQuickFixTestWithIndent(
    indentSize = 2,
    initialCode = """
      plugins {
        id("com.android.application")
        kotlin("android")
      }
    """,
    expectedCode = """
      plugins {
        id("com.android.application")
        kotlin("android")
        id("org.jetbrains.kotlin.plugin.compose")
      }
    """
  )

  fun `test add to file with multiple top-level blocks`() = runQuickFixTest(
    initialCode = """
      repositories {
          google()
          mavenCentral()
      }
      
      dependencies {
          implementation("androidx.core:core-ktx:1.9.0")
      }
      
      tasks.register("clean") {
          delete(rootProject.buildDir)
      }
    """,
    expectedCode = """
      plugins {
          id("org.jetbrains.kotlin.plugin.compose")
      }
      
      repositories {
          google()
          mavenCentral()
      }
      
      dependencies {
          implementation("androidx.core:core-ktx:1.9.0")
      }
      
      tasks.register("clean") {
          delete(rootProject.buildDir)
      }
    """
  )
}
