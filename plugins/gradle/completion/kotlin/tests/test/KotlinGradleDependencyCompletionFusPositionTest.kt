// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.gradle.completion.GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexerTestImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

/**
 * Integration tests for [GradleScriptDependencyCompletionPosition] FUS data collection.
 * Verifies that completion lookup elements have the correct position key set for statistics tracking.
 */
class KotlinGradleDependencyCompletionFusPositionTest : K2GradleCodeInsightTestCase() {

  @TestDisposable
  private lateinit var disposable: Disposable

  private fun configureLocalIndex() {
    val dependencies = listOf("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    ApplicationManager.getApplication().replaceService(
      GradleLocalRepositoryIndexer::class.java,
      GradleLocalRepositoryIndexerTestImpl(LocalEelDescriptor, dependencies),
      disposable
    )
  }

  private fun withNoAutoCompletion(block: () -> Unit) {
    val prevSetting = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    try {
      block()
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = prevSetting
    }
  }

  private fun testCompletionPosition(expression: String, expectedPosition: GradleScriptDependencyCompletionPosition) =
    withNoAutoCompletion {
      testCompletion("build.gradle.kts", expression) { lookupElements ->
        val elements = lookupElements ?: emptyArray()
        assertTrue(elements.isNotEmpty()) { "Expected completion suggestions but got none" }

        val positionedElements = elements.filter {
          it.getUserData(GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY) != null
        }
        assertTrue(positionedElements.isNotEmpty()) {
          "Expected at least one completion element with position key set"
        }

        positionedElements.forEach { positionedElement ->
          val actualPosition = positionedElement.getUserData(GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY)
          assertEquals(expectedPosition, actualPosition) {
            "Expected position $expectedPosition for element '${positionedElement.lookupString}' but got $actualPosition"
          }
        }
      }
    }

  private fun runTest(
    gradleVersion: GradleVersion,
    expression: String,
    expectedPosition: GradleScriptDependencyCompletionPosition,
  ) {
    test(gradleVersion, GRADLE_KTS_JAVA_PLUGIN_FIXTURE) {
      configureLocalIndex()
      testCompletionPosition(expression, expectedPosition)
    }
  }

  companion object {
    private val GRADLE_KTS_JAVA_PLUGIN_FIXTURE = GradleTestFixtureBuilder
      .create("KotlinGradleDependencyCompletionFusPositionTest") { gradleVersion ->
        withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
          withJavaPlugin()
        }
      }
  }

  // GAV position tests

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GAV position - single string dependency`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GAV
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GAV position - empty string`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GAV
  )

  // GROUP position tests

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GROUP position - named argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(group = "org.jetbrains<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GROUP
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GROUP position - named argument with other args`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(group = "<caret>", name = "kotlin-stdlib", version = "2.1.0")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GROUP
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GROUP position - positional first argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("<caret>", "kotlin-stdlib", "2.1.0")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GROUP
  )

  // ARTIFACT position tests

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test ARTIFACT position - named argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(group = "org.jetbrains.kotlin", name = "kotlin-<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.ARTIFACT
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test ARTIFACT position - positional second argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("org.jetbrains.kotlin", "kotlin-<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.ARTIFACT
  )

  // VERSION position tests

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test VERSION position - named argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.VERSION
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test VERSION position - positional third argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("org.jetbrains.kotlin", "kotlin-stdlib", "<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.VERSION
  )

  // EXCLUDE position tests

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test EXCLUDE_GROUP position - named argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0") {
              exclude(group = "org.jetbrains<caret>")
          }
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EXCLUDE_GROUP
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test EXCLUDE_MODULE position - named argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0") {
              exclude(group = "org.jetbrains.kotlin", module = "kotlin-<caret>")
          }
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EXCLUDE_MODULE
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test EXCLUDE_GROUP position - positional first argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0") {
              exclude("org.jetbrains<caret>")
          }
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EXCLUDE_GROUP
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test EXCLUDE_MODULE position - positional second argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0") {
              exclude("org.jetbrains.kotlin", "kotlin-<caret>")
          }
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EXCLUDE_MODULE
  )

  // KOTLIN shortcut position tests

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test KOTLIN_MODULE position - positional first argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(kotlin("std<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.KOTLIN_MODULE
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test KOTLIN_MODULE position - named module argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(kotlin(module = "std<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.KOTLIN_MODULE
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test KOTLIN_VERSION position - positional second argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(kotlin("stdlib", "2.1<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.KOTLIN_VERSION
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test KOTLIN_VERSION position - named version argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(kotlin(module = "stdlib", version = "2.1<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.KOTLIN_VERSION
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test EMBEDDED_KOTLIN_MODULE position - positional first argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(embeddedKotlin("std<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EMBEDDED_KOTLIN_MODULE
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test EMBEDDED_KOTLIN_MODULE position - named module argument`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
      dependencies {
          implementation(embeddedKotlin(module = "std<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EMBEDDED_KOTLIN_MODULE
  )
}
