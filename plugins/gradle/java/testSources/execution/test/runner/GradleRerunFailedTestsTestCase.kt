// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.plugins.gradle.action.GradleRerunFailedTestsAction
import org.jetbrains.plugins.gradle.util.waitForTaskExecution

abstract class GradleRerunFailedTestsTestCase : GradleTestExecutionTestCase() {

  fun performRerunFailedTestsAction(): Boolean = invokeAndWaitIfNeeded {
    val rerunAction = GradleRerunFailedTestsAction(testExecutionConsole)
    rerunAction.setModelProvider { testExecutionConsole.resultsViewer }
    val actionEvent = TestActionEvent.createTestEvent(
      SimpleDataContext.builder()
        .add(ExecutionDataKeys.EXECUTION_ENVIRONMENT, testExecutionEnvironment)
        .add(CommonDataKeys.PROJECT, project)
        .build())
    rerunAction.update(actionEvent)
    if (actionEvent.presentation.isEnabled) {
      waitForTaskExecution {
        rerunAction.actionPerformed(actionEvent)
      }
    }
    actionEvent.presentation.isEnabled
  }
}