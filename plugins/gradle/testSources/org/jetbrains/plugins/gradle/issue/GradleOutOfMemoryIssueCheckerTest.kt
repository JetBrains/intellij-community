// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.util.buildEnvironment
import kotlin.io.path.writeText

@TestApplication
@ParameterizedClass
@BaseGradleVersionSource
class GradleOutOfMemoryIssueCheckerTest(gradleVersion: GradleVersion) {

  private val projectRoot by tempPathFixture()
  private val gradle by gradleFixture(gradleVersion)

  @Test
  fun `creates build issue from nested Failure`() {
    val gradlePropertiesPath = projectRoot.resolve("gradle.properties")
      .apply { writeText("org.gradle.jvmargs=-Xmx64m") }
    val buildEnvironment = gradle.buildEnvironment
      .withGradleUserHome(projectRoot.resolve("gradleUserHome"))
    val failure = GradleIssueFailure.createIssueFailure("Java heap space", OutOfMemoryError::class.java.name + ": Java heap space")
    val nestedFailure = GradleIssueFailure.createIssueFailure("Gradle model fetch failed", null, listOf(failure))
    val issueData = GradleIssueData.createIssueData(projectRoot, nestedFailure, buildEnvironment, null)
    val buildIssue = GradleOutOfMemoryIssueChecker().check(issueData)

    assertEquals("Java heap space", buildIssue?.title)
    assertThat(buildIssue?.description).contains("Out of memory. Java heap space")
    assertThat(buildIssue?.quickFixes).anyMatch { it.isOpenFileQuickFix(gradlePropertiesPath) }
  }
}
