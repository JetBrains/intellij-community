// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.testFramework.common.runAll
import org.jetbrains.plugins.gradle.execution.test.events.fixture.GradleOutputFixture

abstract class GradleTestEventTestCase : GradleExecutionTestCase() {

  private lateinit var outputFixture: GradleOutputFixture

  override fun setUp() {
    super.setUp()

    outputFixture = GradleOutputFixture(project)
    outputFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { outputFixture.tearDown() },
      { super.tearDown() }
    )
  }

  fun assertTestEventsContain(className: String, methodName: String? = null) {
    outputFixture.assertTestEventContain(className, methodName)
  }

  fun assertTestEventsDoesNotContain(className: String, methodName: String? = null) {
    outputFixture.assertTestEventDoesNotContain(className, methodName)
  }

  fun assertTestEventsWasNotReceived() {
    outputFixture.assertTestEventsWasNotReceived()
  }

  override fun <R> waitForAnyGradleTaskExecution(action: () -> R) {
    return outputFixture.assertExecutionOutputIsReady {
      super.waitForAnyGradleTaskExecution(action)
    }
  }
}
