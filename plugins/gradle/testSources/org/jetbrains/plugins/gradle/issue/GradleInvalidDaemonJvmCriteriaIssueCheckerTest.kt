// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.util.buildEnvironment

@TestApplication
@ParameterizedClass
@BaseGradleVersionSource
class GradleInvalidDaemonJvmCriteriaIssueCheckerTest(gradleVersion: GradleVersion) {

  private val projectRoot by tempPathFixture()
  private val gradle by gradleFixture(gradleVersion)

  @Test
  fun `creates build issue from Failure`() {
    val failureMessage = "Value 'invalid version' given for toolchainVersion is an invalid Java version"
    val failure = GradleIssueFailure.createIssueFailure(failureMessage, null)
    val nestedFailure = GradleIssueFailure.createIssueFailure("Gradle model fetch failed", null, listOf(failure))
    val issueData = GradleIssueData.createIssueData(projectRoot, nestedFailure, gradle.buildEnvironment, null)
    val buildIssue = GradleInvalidDaemonJvmCriteriaIssueChecker().check(issueData)

    assertThat(buildIssue?.description).contains(failureMessage)
    assertThat(buildIssue?.quickFixIds()).containsExactly("open_daemon_jvm_criteria_settings")
  }
}
