// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.toml.tests

import com.intellij.openapi.Disposable
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.LOCAL
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightBaseTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest

@GradleProjectTestApplication
internal class GradleTomlDependenciesAutoPopupHandlerTest : GradleCodeInsightBaseTestCase() {

  private val testCompletionService = object : DependencyCompletionService {
    override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyCompletionResult("myGroup", "myArtifact", "1.0", source = LOCAL)))

    override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("myGroup", LOCAL)))

    override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("myArtifact", LOCAL)))

    override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("1.0", LOCAL)))
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private var _autoPopupTester: CompletionAutoPopupTester? = null
  private val autoPopupTester: CompletionAutoPopupTester
    get() = _autoPopupTester ?: error("autoPopupTester is not initialized")

  override fun setUp() {
    super.setUp()
    _autoPopupTester = CompletionAutoPopupTester(codeInsightFixture)
    application.replaceService(DependencyCompletionService::class.java, testCompletionService, disposable)
  }

  private fun runTest(gradleVersion: GradleVersion, test: () -> Unit) {
    testEmptyProject(gradleVersion) { autoPopupTester.runWithAutoPopupEnabled { test() } }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupAfterThreeCharsDirectlyInLibrariesTable(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    codeInsightFixture.configureByText("gradle/libs.versions.toml", """
      [libraries]
      my-lib = <caret>
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed (IDEA-390474)" }
    autoPopupTester.typeWithPauses("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters for a direct library value in [libraries] table" }
    assertEquals("myGroup:myArtifact:1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupAfterThreeCharsInModuleKey(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    codeInsightFixture.configureByText("gradle/libs.versions.toml", """
      [libraries]
      my-lib = { module = <caret> }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"my")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered until 3 characters are typed (IDEA-390474)" }
    autoPopupTester.typeWithPauses("G")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered after 3 characters inside of library's module key" }
    assertEquals("myGroup:myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInGroupKey(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    codeInsightFixture.configureByText("gradle/libs.versions.toml", """
      [libraries]
      my-lib = { group = <caret>, name = "myArtifact" }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of library's group key" }
    assertEquals("myGroup", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInNameKey(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    codeInsightFixture.configureByText("gradle/libs.versions.toml", """
      [libraries]
      my-lib = { group = "myGroup", name = <caret> }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of library's name key" }
    assertEquals("myArtifact", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInVersionKeyWithModule(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    codeInsightFixture.configureByText("gradle/libs.versions.toml", """
      [libraries]
      my-lib = { module = "myGroup:myArtifact", version = <caret> }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of library's version key" }
    assertEquals("1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAutoPopupOnQuoteInVersionKeyWithGroupAndName(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    codeInsightFixture.configureByText("gradle/libs.versions.toml", """
      [libraries]
      my-lib = { group = "myGroup", name = "myArtifact", version = <caret> }
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"")
    assertNotNull(autoPopupTester.lookup) { "Auto popup should be triggered inside of library's version key" }
    assertEquals("1.0", autoPopupTester.lookup?.currentItem?.lookupString)
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoAutoPopupOnClosingQuoteInLibrariesTable(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    codeInsightFixture.configureByText("gradle/libs.versions.toml", """
      [libraries]
      my-lib = "myGroup:myArtifact<caret>"
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered when typing the closing quote" }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoAutoPopupOnQuoteOutsideLibrariesTable(gradleVersion: GradleVersion) = runTest(gradleVersion) {
    codeInsightFixture.configureByText("gradle/libs.versions.toml", """
      [versions]
      my-version = <caret>
    """.trimIndent())
    autoPopupTester.typeWithPauses("\"")
    assertNull(autoPopupTester.lookup) { "Auto popup should not be triggered outside of [libraries] table" }
  }
}
