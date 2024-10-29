/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

public class AfterTestEventProcessor extends AbstractTestEventProcessor {

  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.externalSystem.event-processing");

  public AfterTestEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    var event = (ExternalSystemFinishEvent<? extends TestOperationDescriptor>)testEvent;
    process(event, false);
  }

  @Override
  public void process(@NotNull TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException, NumberFormatException {
    var testId = eventXml.getTestId();
    var testParentId = eventXml.getTestParentId();
    var eventTime = Long.parseLong(eventXml.getEventTestResultEndTime());
    var testDescriptor = GradleXmlTestEventConverter.convertTestDescriptor(eventTime, eventXml);
    var testResult = GradleXmlTestEventConverter.convertOperationResult(eventXml);
    var event = new ExternalSystemFinishEvent<>(testId, testParentId, testDescriptor, testResult);
    process(event, true);
  }

  private void process(@NotNull ExternalSystemFinishEvent<? extends TestOperationDescriptor> event, boolean isXml) {
    var patcher = getExecutionConsole().getFileComparisonEventPatcher();
    var patchedEvent = patcher.patchTestFinishEvent(event, isXml);
    if (patchedEvent == null) {
      LOG.info("Skipped event because it is incomplete: " + event);
      return;
    }
    process(patchedEvent);
  }

  private void process(@NotNull ExternalSystemFinishEvent<? extends TestOperationDescriptor> event) {
    var testId = event.getEventId();
    var testResult = event.getOperationResult();
    var startTime = testResult.getStartTime();
    var endTime = testResult.getEndTime();

    var testProxy = findTestProxy(testId);
    if (testProxy == null) {
      LOG.error("Cannot find test proxy for: " + testId);
      return;
    }

    testProxy.setDuration(endTime - startTime);

    if (testResult instanceof SuccessResult) {
      testProxy.setFinished();
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else if (testResult instanceof SkippedResult) {
      testProxy.setTestIgnored(null, null);
      getResultsViewer().onTestIgnored(testProxy);
      getExecutionConsole().getEventPublisher().onTestIgnored(testProxy);
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else if (testResult instanceof FailureResult failureResult) {
      for (var failure : failureResult.getFailures()) {
        processFailureResult(testProxy, failure);
      }
      getResultsViewer().onTestFailed(testProxy);
      getExecutionConsole().getEventPublisher().onTestFailed(testProxy);
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else {
      LOG.warn("Undefined test result: " + testResult.getClass().getName());
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
  }

  private static void processFailureResult(@NotNull SMTestProxy testProxy, @NotNull Failure failure) {
    if (failure instanceof TestFailure testFailure) {
      processTestFailureResult(testProxy, testFailure);
    }
    else {
      LOG.warn("Undefined test failure type: " + failure.getClass().getName());
      var message = ObjectUtils.doIfNotNull(failure, it -> it.getMessage());
      var description = ObjectUtils.doIfNotNull(failure, it -> it.getDescription());
      testProxy.setTestFailed(message, description, true);
    }
    for (var cause : failure.getCauses()) {
      processFailureResult(testProxy, cause);
    }
  }

  private static void processTestFailureResult(@NotNull SMTestProxy testProxy, @NotNull TestFailure failure) {
    var convertedFailure = GradleAssertionTestEventConverter.convertTestFailure(failure);
    var message = convertedFailure.getMessage();
    var stackTrace = convertedFailure.getStackTrace();
    if (convertedFailure instanceof TestAssertionFailure assertionFailure) {
      var actualText = assertionFailure.getActualText();
      var expectedText = assertionFailure.getExpectedText();
      var actualFile = assertionFailure.getActualFile();
      var expectedFile = assertionFailure.getExpectedFile();
      testProxy.setTestComparisonFailed(message, stackTrace, actualText, expectedText, actualFile, expectedFile, true);
    }
    else {
      testProxy.setTestFailed(message, stackTrace, failure.isTestError());
    }
  }
}
