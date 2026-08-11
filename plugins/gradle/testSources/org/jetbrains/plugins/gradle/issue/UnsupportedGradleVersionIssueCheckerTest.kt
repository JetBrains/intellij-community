// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.mock.GradleTestBuildEnvironment
import org.jetbrains.plugins.gradle.testFramework.util.createGradleWrapper
import org.junit.jupiter.api.Test

@TestApplication
class UnsupportedGradleVersionIssueCheckerTest() {

  private val projectRoot by tempPathFixture()

  @Test
  fun `creates build issue from Failure`() {
    projectRoot.createGradleWrapper(GradleVersion.version("2.0"))
    val failureMessage = "Support for builds using Gradle versions older than 2.0 is not available."
    val failure = GradleIssueFailure.createIssueFailure(failureMessage, UnsupportedVersionException::class.java.name + ": $failureMessage")
    val issueData = GradleIssueData.createIssueData(projectRoot, failure, null, null)
    val buildIssue = UnsupportedGradleVersionIssueChecker().check(issueData)

    assertThat(buildIssue?.quickFixIds()).contains("fix_gradle_version_in_wrapper")
  }

  @Test
  fun `creates build issue from Failure with resolved Gradle version`() {
    projectRoot.createGradleWrapper(GradleVersion.version("2.0"))
    val buildEnvironment = GradleTestBuildEnvironment.createBuildEnvironment()
      .withGradleVersion("2.0")

    val failureMessage = "Support for builds using Gradle versions older than 2.0 is not available."
    val failure = GradleIssueFailure.createIssueFailure(failureMessage, UnsupportedVersionException::class.java.name + ": $failureMessage")
    val issueData = GradleIssueData.createIssueData(projectRoot, failure, buildEnvironment, null)
    val buildIssue = UnsupportedGradleVersionIssueChecker().check(issueData)

    assertThat(buildIssue?.quickFixIds()).contains("fix_gradle_version_in_wrapper")
  }

  @Test
  fun `does not create build issue from Failure if gradle wrapper properties file does not exist`() {
    val failureMessage = "Support for builds using Gradle versions older than 2.0 is not available."
    val failure = GradleIssueFailure.createIssueFailure(failureMessage, UnsupportedVersionException::class.java.name + ": $failureMessage")
    val issueData = GradleIssueData.createIssueData(projectRoot, failure, null, null)
    val buildIssue = UnsupportedGradleVersionIssueChecker().check(issueData)

    assertThat(buildIssue?.quickFixIds()).doesNotContain("fix_gradle_version_in_wrapper")
  }
}
