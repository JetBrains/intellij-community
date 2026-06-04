// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
class GradleIssueDataTest {

  private val projectRoot by tempPathFixture()

  @Test
  fun `GradleIssueData created from Failure exposes synthetic deprecated error`() {
    val cause = GradleIssueFailure.createIssueFailure("cause failure", null)
    val modelFailure = GradleIssueFailure.createIssueFailure("model failure", null, listOf(cause))

    val issueData = GradleIssueData.createIssueData(projectRoot, modelFailure, null, null)

    assertSame(modelFailure, issueData.failure)
    assertEquals("model failure", @Suppress("DEPRECATION") issueData.error.message)
    assertEquals("cause failure", @Suppress("DEPRECATION") issueData.error.cause?.message)
  }
}
