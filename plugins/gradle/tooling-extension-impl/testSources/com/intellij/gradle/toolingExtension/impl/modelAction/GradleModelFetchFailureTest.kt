// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import org.assertj.core.api.Assertions.assertThat
import org.gradle.tooling.Failure
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.internal.consumer.DefaultFailure
import org.junit.jupiter.api.Test

class GradleModelFetchFailureTest {

  @Test
  fun `own description is used for a failure built from own descriptions`() {
    val rootCause = DefaultFailure.fromOwnDescription("root message", "root description", emptyList(), emptyList())
    val failure = DefaultFailure.fromOwnDescription("message", "description", listOf(rootCause), emptyList())

    val modelFetchFailure = GradleModelFetchFailure(failure)

    assertThat(modelFetchFailure.description).isEqualTo("description")
    assertThat(modelFetchFailure.causes.single().description).isEqualTo("root description")
  }

  @Test
  fun `full description is used for a failure without a separate own description`() {
    val rootCause = DefaultFailure("root message", "root description", emptyList())
    val failure = DefaultFailure("message", "description", listOf(rootCause))

    val modelFetchFailure = GradleModelFetchFailure(failure)

    assertThat(modelFetchFailure.description).isEqualTo("description")
    assertThat(modelFetchFailure.causes.single().description).isEqualTo("root description")
  }

  @Test
  fun `description falls back to Failure getDescription for a non-DefaultFailure implementation`() {
    val rootCause = object : Failure {
      override fun getMessage() = "root message"
      override fun getDescription() = "root description"
      override fun getCauses() = emptyList<Failure>()
      override fun getProblems() = emptyList<Problem>()
    }
    val failure = object : Failure {
      override fun getMessage() = "message"
      override fun getDescription() = "description"
      override fun getCauses() = listOf(rootCause)
      override fun getProblems() = emptyList<Problem>()
    }

    val modelFetchFailure = GradleModelFetchFailure(failure)

    assertThat(modelFetchFailure.description).isEqualTo("description")
    assertThat(modelFetchFailure.causes.single().description).isEqualTo("root description")
  }
}
