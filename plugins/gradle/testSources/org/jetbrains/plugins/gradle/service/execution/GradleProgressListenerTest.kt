// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gradle.tooling.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class GradleProgressListenerTest {

  @Test
  fun `message failure is converted to Gradle issue failure`() {
    val rootCause = Message.Failure("Root cause", "Root cause description")
    val failure = Message.Failure("Failure", "Failure description", listOf(rootCause))
    val message = Message("Title", "Text", "group", Message.Kind.ERROR, null, failure, false)

    val issueFailure = GradleProgressListener.createGradleIssueFailure(message)

    assertEquals("Failure", issueFailure.message)
    assertEquals("Failure description", issueFailure.description)
    assertEquals("Root cause", issueFailure.rootCause.message)
    assertEquals("Root cause description", issueFailure.rootCause.description)
  }

  @Test
  fun `message without failure is converted from title and text`() {
    val message = Message("Title", "Text", "group", Message.Kind.ERROR, null, null, false)

    val issueFailure = GradleProgressListener.createGradleIssueFailure(message)

    assertEquals("Title", issueFailure.message)
    assertEquals("Text", issueFailure.description)
  }

}
