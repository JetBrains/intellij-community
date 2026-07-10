// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.gradle.internal.exceptions.LocationAwareException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path

@TestApplication
class GradleIssueFailureTest {

  private val projectRoot by tempPathFixture()

  @ParameterizedTest
  @EnumSource(LocationCase::class)
  fun `GradleIssueFailure created from message exposes file position`(locationCase: LocationCase) {
    val file = projectRoot.resolve(locationCase.fileName)
    val failure = GradleIssueFailure.createIssueFailure(locationCase.sourceName + " '$file' line: ${21}", null)

    assertFilePosition(file, 21, failure)
  }

  @ParameterizedTest
  @EnumSource(LocationCase::class)
  fun `GradleIssueFailure exposes file position from description`(locationCase: LocationCase) {
    val file = projectRoot.resolve(locationCase.fileName)
    val failure = GradleIssueFailure.createIssueFailure(null, locationCase.sourceName + " '$file' line: ${42}")

    assertFilePosition(file, 42, failure)
  }

  @ParameterizedTest
  @EnumSource(LocationCase::class)
  fun `GradleIssueFailure exposes file position from cause`(locationCase: LocationCase) {
    val file = projectRoot.resolve(locationCase.fileName)
    val cause = GradleIssueFailure.createIssueFailure(locationCase.sourceName + " '$file' line: ${42}", null)
    val failure = GradleIssueFailure.createIssueFailure("Gradle model fetch failed", null, listOf(cause))

    assertFilePosition(file, 42, failure)
  }

  @ParameterizedTest
  @EnumSource(LocationCase::class)
  fun `GradleIssueFailure created from Throwable exposes location aware file position`(locationCase: LocationCase) {
    val file = projectRoot.resolve(locationCase.fileName)
    val rootCause = IllegalStateException("failed to find target current")
    val locationError = LocationAwareException(rootCause, locationCase.sourceName + " '$file'", 42)

    val failure = GradleIssueFailure.createIssueFailure(Throwable(locationError))
    val filePosition = failure.filePosition

    assertEquals(rootCause.message, failure.rootCause.message)
    assertEquals(file, filePosition?.path)
    assertEquals(42, filePosition?.startLine)
    assertEquals(0, filePosition?.startColumn)
  }

  @Test
  fun `GradleIssueFailure resolves root cause through nested non-empty causes`() {
    val rootCause = GradleIssueFailure.createIssueFailure("failed to find target current", null)
    val intermediateCause = GradleIssueFailure.createIssueFailure("Gradle task failed", null, listOf(rootCause))
    val failure = GradleIssueFailure.createIssueFailure("Gradle model fetch failed", null, listOf(intermediateCause))

    assertSame(rootCause, failure.rootCause)
  }

  @Test
  fun `GradleIssueFailure ignores empty causes when resolving root cause`() {
    val emptyCause = GradleIssueFailure.createIssueFailure(null, null)
    val rootCause = GradleIssueFailure.createIssueFailure("failed to find target current", null, listOf(emptyCause))
    val failure = GradleIssueFailure.createIssueFailure("Gradle model fetch failed", null, listOf(rootCause))

    assertSame(rootCause, failure.rootCause)
  }

  @Test
  fun `GradleIssueFailure ignores empty sibling causes when resolving root cause`() {
    val emptyCause = GradleIssueFailure.createIssueFailure(null, null)
    val rootCause = GradleIssueFailure.createIssueFailure("failed to find target current", null)
    val failure = GradleIssueFailure.createIssueFailure("Gradle model fetch failed", null, listOf(emptyCause, rootCause))

    assertSame(rootCause, failure.rootCause)
  }

  @Test
  fun `GradleIssueFailure resolves class name from description`() {
    val failureMessage = "failed to find target current"
    val failure = GradleIssueFailure.createIssueFailure(failureMessage, LocationAwareException::class.java.name + ": $failureMessage")

    assertEquals(LocationAwareException::class.java.name, failure.className)
    assertEquals(LocationAwareException::class.java.name + ": failed to find target current", failure.text)
  }

  @Test
  fun `GradleIssueFailure uses description as text when message is missing`() {
    val failure = GradleIssueFailure.createIssueFailure(null, "failed to find target current")

    assertEquals("failed to find target current", failure.text)
  }

  private fun assertFilePosition(expectedPath: Path, expectedLine: Int, failure: GradleIssueFailure) {
    assertEquals(expectedPath, failure.filePosition?.path)
    assertEquals(expectedLine, failure.filePosition?.startLine)
    assertEquals(0, failure.filePosition?.startColumn)
  }

  enum class LocationCase(
    val sourceName: String,
    val fileName: String,
  ) {
    BUILD_FILE("Build file", "build.gradle"),
    SETTINGS_FILE("Settings file", "settings.gradle.kts"),
  }
}
