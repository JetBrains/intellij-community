// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.util.buildEnvironment
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@TestApplication
@ParameterizedClass
@BaseGradleVersionSource
class GradleToolchainDownloadingErrorIssueCheckerTest(gradleVersion: GradleVersion) {

  private val projectRoot by tempPathFixture()
  private val gradle by gradleFixture(gradleVersion)

  @Test
  fun `creates build issue from Failure`() {
    val failureMessage = "Unable to download toolchain matching the requirements ({languageVersion=11}) from 'https://example.com/jdk.tar.gz'"
    val failure = GradleIssueFailure.createIssueFailure(failureMessage, null)
    val nestedFailure = GradleIssueFailure.createIssueFailure("Gradle model fetch failed", null, listOf(failure))
    val issueData = GradleIssueData.createIssueData(projectRoot, nestedFailure, gradle.buildEnvironment, null)
    val buildIssue = GradleToolchainDownloadingErrorIssueChecker().check(issueData)

    assertThat(buildIssue?.description).contains(failureMessage)
    assertThat(buildIssue?.quickFixIds()).containsExactly("download_toolchain", "open_daemon_jvm_criteria_settings")
  }
}
