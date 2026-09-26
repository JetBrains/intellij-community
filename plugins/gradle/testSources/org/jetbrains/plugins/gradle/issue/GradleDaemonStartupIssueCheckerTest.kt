// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.GradleException
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.util.buildEnvironment
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import kotlin.io.path.writeText

@TestApplication
@ParameterizedClass
@BaseGradleVersionSource
class GradleDaemonStartupIssueCheckerTest(gradleVersion: GradleVersion) {

  private val projectRoot by tempPathFixture()
  private val gradle by gradleFixture(gradleVersion)

  @Test
  fun `creates build issue from nested Failure`() {
    val gradlePropertiesPath = projectRoot.resolve("gradle.properties")
      .apply { writeText("org.gradle.jvmargs=-Xmx64m") }
    val buildEnvironment = gradle.buildEnvironment
      .withGradleUserHome(projectRoot.resolve("gradleUserHome"))
    val failureMessage = "Unable to start the daemon process. This problem might be caused by incorrect configuration of the daemon."
    val failure = GradleIssueFailure.createIssueFailure(failureMessage, GradleException::class.java.name + ": $failureMessage")
    val nestedFailure = GradleIssueFailure.createIssueFailure("Gradle model fetch failed", null, listOf(failure))
    val issueData = GradleIssueData.createIssueData(projectRoot, nestedFailure, buildEnvironment, null)
    val buildIssue = GradleDaemonStartupIssueChecker().check(issueData)

    assertThat(buildIssue?.description).contains("Unable to start the daemon process")
    assertThat(buildIssue?.quickFixes).anyMatch { it.isOpenFileQuickFix(gradlePropertiesPath) }
  }
}
