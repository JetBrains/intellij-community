// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.toml.tests

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexerTestImpl
import com.intellij.gradle.completion.toml.GRADLE_TOML_LIBRARY_COMPLETION_POSITION_KEY
import com.intellij.gradle.completion.toml.GradleTomlLibraryCompletionPosition
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

internal class GradleTomlCompletionFusPositionTest : GradleCodeInsightTestCase() {
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

  private fun testCompletionPosition(expression: String, expectedPosition: GradleTomlLibraryCompletionPosition) = withNoAutoCompletion {
    testCompletion("gradle/libs.versions.toml", expression) { lookupElements ->
      assertTrue(lookupElements.isNotEmpty()) { "Expected completion suggestions but got none" }

      val positionedElements = lookupElements.filter {
        it.getUserData(GRADLE_TOML_LIBRARY_COMPLETION_POSITION_KEY) != null
      }
      assertTrue(positionedElements.isNotEmpty()) {
        "Expected at least one completion element with position key set"
      }

      positionedElements.forEach { positionedElement ->
        val actualPosition = positionedElement.getUserData(GRADLE_TOML_LIBRARY_COMPLETION_POSITION_KEY)
        assertEquals(expectedPosition, actualPosition) {
          "Expected position $expectedPosition for element '${positionedElement.lookupString}' but got $actualPosition"
        }
      }
    }
  }

  private fun runTest(
    gradleVersion: GradleVersion,
    expression: String,
    expectedPosition: GradleTomlLibraryCompletionPosition,
  ) {
    testEmptyProject(gradleVersion) {
      configureLocalIndex()
      testCompletionPosition(expression, expectedPosition)
    }
  }

  // GAV position tests - single string directly in libraries table

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GAV position - single GAV string`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib = "org.jetbrains.kotlin:kotlin-<caret>"
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.GAV
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GAV position - empty string`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib = "<caret>"
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.GAV
  )

  // MODULE position tests - module key with group:artifact format

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test MODULE position - module key`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib.module = "<caret>"
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.MODULE
  )

  // GROUP position tests - inline table with group key

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GROUP position - group in inline table`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib = { group = "<caret>", name = "kotlin-stdlib", version = "2.1.0" }
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.GROUP
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GROUP position - group only in inline table`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib = { group = "<caret>" }
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.GROUP
  )

  // ARTIFACT position tests - inline table with name key

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test ARTIFACT position - name in inline table`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "<caret>", version = "2.1.0" }
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.ARTIFACT
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test ARTIFACT position - name without version`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "<caret>" }
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.ARTIFACT
  )

  // VERSION position tests - inline table with version key

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test VERSION position - inline table version key`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "<caret>" }
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.VERSION
  )

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test VERSION position - version with module key`(gradleVersion: GradleVersion) = runTest(
    gradleVersion,
    """
    [libraries]
    kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version = "<caret>" }
    """.trimIndent(),
    GradleTomlLibraryCompletionPosition.VERSION
  )
}
