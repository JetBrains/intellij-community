// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.testFramework.common.runAll
import org.jetbrains.plugins.gradle.execution.test.events.fixture.GradleOutputFixture
import org.jetbrains.plugins.gradle.execution.test.events.fixture.TestExecutionConsoleEventFixture

abstract class GradleTestEventTestCase : GradleExecutionTestCase() {


  private lateinit var outputFixture: GradleOutputFixture
  private lateinit var testExecutionEventFixture: TestExecutionConsoleEventFixture

  override fun setUp() {
    super.setUp()

    outputFixture = GradleOutputFixture(project)
    outputFixture.setUp()

    testExecutionEventFixture = TestExecutionConsoleEventFixture(project)
    testExecutionEventFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { testExecutionEventFixture.tearDown() },
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

  fun assertTestEventCount(
    name: String, suiteStart: Int, suiteFinish: Int, testStart: Int, testFinish: Int, testFailure: Int, testIgnore: Int
  ) {
    testExecutionEventFixture.assertTestEventCount(name, suiteStart, suiteFinish, testStart, testFinish, testFailure, testIgnore)
  }
}
