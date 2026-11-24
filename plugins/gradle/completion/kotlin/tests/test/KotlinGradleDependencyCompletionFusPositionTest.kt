// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin.tests.integration

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.gradle.codeInsight.kotlin.testFramework.completeGradleScript
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.customConfigurationsGradleCodeInsightFixture
import com.intellij.gradle.completion.GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexerTestImpl
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

/**
 * Integration tests for [GradleScriptDependencyCompletionPosition] FUS data collection.
 * Verifies that completion lookup elements have the correct position key set for statistics tracking.
 */
@GradleImmutableProjectTestApplication
@ParameterizedClass
@BaseGradleVersionSource
class KotlinGradleDependencyCompletionFusPositionTest(gradleVersion: GradleVersion) {

  private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

  @BeforeEach
  fun setUp() {
    val dependencies = listOf("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    application.replaceService(
      GradleLocalRepositoryIndexer::class.java,
      GradleLocalRepositoryIndexerTestImpl(LocalEelDescriptor, dependencies),
      codeInsightFixture.testRootDisposable
    )
    val settings = CodeInsightSettings.getInstance()
    val autocompleteOnCodeCompletion = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
    Disposer.register(codeInsightFixture.testRootDisposable) {
      settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autocompleteOnCodeCompletion
    }
  }

  private fun runTest(expression: String, expectedPosition: GradleScriptDependencyCompletionPosition) {
    codeInsightFixture.completeGradleScript(expression) { lookupElements ->
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

  // GAV position tests

  @Test
  fun `test GAV position - single string dependency`() = runTest(
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GAV
  )

  @Test
  fun `test GAV position - empty string`() = runTest(
    """
      dependencies {
          implementation("<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GAV
  )

  // GROUP position tests

  @Test
  fun `test GROUP position - named argument`() = runTest(
    """
      dependencies {
          implementation(group = "org.jetbrains<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GROUP
  )

  @Test
  fun `test GROUP position - named argument with other args`() = runTest(
    """
      dependencies {
          implementation(group = "<caret>", name = "kotlin-stdlib", version = "2.1.0")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GROUP
  )

  @Test
  fun `test GROUP position - positional first argument`() = runTest(
    """
      dependencies {
          implementation("<caret>", "kotlin-stdlib", "2.1.0")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.GROUP
  )

  // ARTIFACT position tests

  @Test
  fun `test ARTIFACT position - named argument`() = runTest(
    """
      dependencies {
          implementation(group = "org.jetbrains.kotlin", name = "kotlin-<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.ARTIFACT
  )

  @Test
  fun `test ARTIFACT position - positional second argument`() = runTest(
    """
      dependencies {
          implementation("org.jetbrains.kotlin", "kotlin-<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.ARTIFACT
  )

  // VERSION position tests

  @Test
  fun `test VERSION position - named argument`() = runTest(
    """
      dependencies {
          implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.VERSION
  )

  @Test
  fun `test VERSION position - positional third argument`() = runTest(
    """
      dependencies {
          implementation("org.jetbrains.kotlin", "kotlin-stdlib", "<caret>")
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.VERSION
  )

  // EXCLUDE position tests

  @Test
  fun `test EXCLUDE_GROUP position - named argument`() = runTest(
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0") {
              exclude(group = "org.jetbrains<caret>")
          }
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EXCLUDE_GROUP
  )

  @Test
  fun `test EXCLUDE_MODULE position - named argument`() = runTest(
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0") {
              exclude(group = "org.jetbrains.kotlin", module = "kotlin-<caret>")
          }
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EXCLUDE_MODULE
  )

  @Test
  fun `test EXCLUDE_GROUP position - positional first argument`() = runTest(
    """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0") {
              exclude("org.jetbrains<caret>")
          }
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EXCLUDE_GROUP
  )

  @Test
  fun `test EXCLUDE_MODULE position - positional second argument`() = runTest(
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

  @Test
  fun `test KOTLIN_MODULE position - positional first argument`() = runTest(
    """
      dependencies {
          implementation(kotlin("std<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.KOTLIN_MODULE
  )

  @Test
  fun `test KOTLIN_MODULE position - named module argument`() = runTest(
    """
      dependencies {
          implementation(kotlin(module = "std<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.KOTLIN_MODULE
  )

  @Test
  fun `test KOTLIN_VERSION position - positional second argument`() = runTest(
    """
      dependencies {
          implementation(kotlin("stdlib", "2.1<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.KOTLIN_VERSION
  )

  @Test
  fun `test KOTLIN_VERSION position - named version argument`() = runTest(
    """
      dependencies {
          implementation(kotlin(module = "stdlib", version = "2.1<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.KOTLIN_VERSION
  )

  @Test
  fun `test EMBEDDED_KOTLIN_MODULE position - positional first argument`() = runTest(
    """
      dependencies {
          implementation(embeddedKotlin("std<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EMBEDDED_KOTLIN_MODULE
  )

  @Test
  fun `test EMBEDDED_KOTLIN_MODULE position - named module argument`() = runTest(
    """
      dependencies {
          implementation(embeddedKotlin(module = "std<caret>"))
      }
    """.trimIndent(),
    GradleScriptDependencyCompletionPosition.EMBEDDED_KOTLIN_MODULE
  )
}
