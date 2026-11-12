// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchFailure
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GradleSyncFailureHandlerTest {

  @Test
  fun `shared cause subtree is converted only once for equal failures`() {
    val sharedCause = GradleModelFetchFailure("shared message", "shared description", emptyList())
    val firstFailure = GradleModelFetchFailure("first message", "first description", listOf(sharedCause))
    val secondFailure = GradleModelFetchFailure("second message", "second description", listOf(sharedCause))

    val handler = GradleSyncFailureHandler()
    val firstIssueFailure = handler.createIssueFailure(firstFailure)
    val secondIssueFailure = handler.createIssueFailure(secondFailure)

    assertThat(firstIssueFailure)
      .isNotSameAs(secondIssueFailure)
    assertThat(firstIssueFailure.causes.single())
      .isSameAs(secondIssueFailure.causes.single())
  }

  @Test
  fun `equal failure trees are converted to the same GradleIssueFailure`() {
    val firstFailure = GradleModelFetchFailure("message", "description", emptyList())
    val secondFailure = GradleModelFetchFailure("message", "description", emptyList())

    val handler = GradleSyncFailureHandler()
    val firstIssueFailure = handler.createIssueFailure(firstFailure)
    val secondIssueFailure = handler.createIssueFailure(secondFailure)

    assertThat(firstIssueFailure).isSameAs(secondIssueFailure)
  }
}
