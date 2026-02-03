// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.plugins.gradle.action.GradleRerunFailedTestsAction
import org.jetbrains.plugins.gradle.testFramework.GradleTestExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.util.testConsole
import org.junit.jupiter.api.Assertions

abstract class GradleRerunFailedTestsTestCase : GradleTestExecutionTestCase() {

  fun rerunFailedTests() {
    invokeAndWaitIfNeeded {
      val testExecutionConsole = executionEnvironment.testConsole
      val rerunAction = GradleRerunFailedTestsAction(testExecutionConsole)
      rerunAction.setModelProvider { testExecutionConsole.resultsViewer }
      val actionEvent = TestActionEvent.createTestEvent(
        SimpleDataContext.builder()
          .add(ExecutionDataKeys.EXECUTION_ENVIRONMENT, executionEnvironment)
          .add(CommonDataKeys.PROJECT, project)
          .build())
      rerunAction.update(actionEvent)
      Assertions.assertTrue(actionEvent.presentation.isEnabled)
      waitForAnyGradleTaskExecution {
        rerunAction.actionPerformed(actionEvent)
      }
    }
  }
}