// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.openapi.progress.ProcessCanceledException
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

@TestApplication
@ParameterizedClass
@BaseGradleVersionSource
class GradleBuildCancelledIssueCheckerTest(gradleVersion: GradleVersion) {

  private val projectRoot by tempPathFixture()
  private val gradle by gradleFixture(gradleVersion)

  @Test
  fun `recognizes Failure class name`() {
    val failure = GradleIssueFailure.createIssueFailure("Build cancelled.", ProcessCanceledException::class.java.name + ": Build cancelled.")
    val issueData = GradleIssueData.createIssueData(projectRoot, failure, gradle.buildEnvironment, null)
    val buildIssue = GradleBuildCancelledIssueChecker().check(issueData)

    assertEquals("Build cancelled", buildIssue?.title)
    assertThat(buildIssue?.quickFixes).isEmpty()
  }
}
