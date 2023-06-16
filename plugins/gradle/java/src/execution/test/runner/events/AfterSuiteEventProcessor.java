// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemFinishEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

/**
 * @author Vladislav.Soroka
 */
public class AfterSuiteEventProcessor extends AbstractTestEventProcessor {

  public AfterSuiteEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  protected boolean isSuite() {
    return true;
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    var finishEvent = (ExternalSystemFinishEvent<? extends TestOperationDescriptor>)testEvent;
    var testId = testEvent.getEventId();
    var result = TestEventResult.fromOperationResult(finishEvent.getOperationResult());

    doProcess(testId, result);
  }

  @Override
  public void process(@NotNull TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    final String testId = eventXml.getTestId();
    TestEventResult result = TestEventResult.fromValue(eventXml.getTestEventResultType());

    doProcess(testId, result);
  }

  private void doProcess(String testId, TestEventResult result) {
    final SMTestProxy testProxy = findTestProxy(testId);
    if (testProxy == null) return;

    if (testProxy != getResultsViewer().getTestsRootNode()) {
      if (testProxy instanceof GradleSMTestProxy) {
        TestEventResult lastResult = ((GradleSMTestProxy)testProxy).getLastResult();
        if (lastResult == TestEventResult.FAILURE) {
          result = TestEventResult.FAILURE;
        }
        ((GradleSMTestProxy)testProxy).setLastResult(result);
      }
      switch (result) {
        case SUCCESS -> testProxy.setFinished();
        case FAILURE -> testProxy.setTestFailed("", null, false);
        case SKIPPED -> testProxy.setTestIgnored(null, null);
        case UNKNOWN_RESULT -> {}
      }
      getResultsViewer().onSuiteFinished(testProxy);
      getExecutionConsole().getEventPublisher().onSuiteFinished(testProxy);
    }
  }
}
