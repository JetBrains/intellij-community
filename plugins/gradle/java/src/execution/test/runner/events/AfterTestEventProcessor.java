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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.parseComparisonMessage;

/**
 * @author Vladislav.Soroka
 */
public class AfterTestEventProcessor extends AbstractTestEventProcessor {

  private static final Logger LOG = Logger.getInstance(AfterTestEventProcessor.class);

  public AfterTestEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    var finishEvent = (ExternalSystemFinishEvent<? extends TestOperationDescriptor>)testEvent;

    var testId = testEvent.getEventId();
    var result = finishEvent.getOperationResult();
    var startTime = result.getStartTime();
    var endTime = result.getEndTime();

    var testProxy = findTestProxy(testId);
    if (testProxy == null) {
      LOG.error("Cannot find test proxy for: " + testId);
      return;
    }

    testProxy.setDuration(endTime - startTime);

    if (result instanceof SuccessResult) {
      testProxy.setFinished();
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else if (result instanceof SkippedResult) {
      testProxy.setTestIgnored(null, null);
      getResultsViewer().onTestIgnored(testProxy);
      getExecutionConsole().getEventPublisher().onTestIgnored(testProxy);
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else if (result instanceof FailureResult failureResult) {
      var failure = ContainerUtil.getFirstItem(failureResult.getFailures());
      if (failure instanceof TestAssertionFailure assertionFailure) {
        var message = assertionFailure.getMessage();
        var description = ObjectUtils.doIfNotNull(failure, it -> it.getDescription());
        var actualText = assertionFailure.getActualText();
        var expectedText = assertionFailure.getExpectedText();
        var actualFile = assertionFailure.getActualFile();
        var expectedFile = assertionFailure.getExpectedFile();
        testProxy.setTestComparisonFailed(message, description, actualText, expectedText, actualFile, expectedFile, true);
      }
      else {
        var message = ObjectUtils.doIfNotNull(failure, it -> it.getMessage());
        var description = ObjectUtils.doIfNotNull(failure, it -> it.getDescription());
        testProxy.setTestFailed(message, description, false);
      }
      getResultsViewer().onTestFailed(testProxy);
      getExecutionConsole().getEventPublisher().onTestFailed(testProxy);
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else {
      LOG.error("Undefined test result: " + result.getClass().getName());
    }
  }

  @Override
  public void process(@NotNull final TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException, NumberFormatException {
    var testId = eventXml.getTestId();
    var startTime = Long.parseLong(eventXml.getEventTestResultStartTime());
    var endTime = Long.parseLong(eventXml.getEventTestResultEndTime());
    var resultType = TestEventResult.fromValue(eventXml.getTestEventResultType());

    var testProxy = findTestProxy(testId);
    if (testProxy == null) {
      LOG.error("Cannot find test proxy for: " + testId);
      return;
    }

    testProxy.setDuration(endTime - startTime);

    if (resultType == TestEventResult.SUCCESS) {
      testProxy.setFinished();
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else if (resultType == TestEventResult.FAILURE) {
      var failureType = eventXml.getEventTestResultFailureType();
      var message = decode(eventXml.getEventTestResultErrorMsg());
      var stackTrace = decode(eventXml.getEventTestResultStackTrace());
      if ("comparison".equals(failureType)) {
        var actualText = decode(eventXml.getEventTestResultActual());
        var expectedText = decode(eventXml.getEventTestResultExpected());
        var filePath = StringUtil.nullize(decode(eventXml.getEventTestResultFilePath()));
        var actualFilePath = StringUtil.nullize(decode(eventXml.getEventTestResultActualFilePath()));
        testProxy.setTestComparisonFailed(message, stackTrace, actualText, expectedText, filePath, actualFilePath, true);
      }
      else {
        var comparisonPair = parseComparisonMessage(message);
        if (comparisonPair != null) {
          testProxy.setTestComparisonFailed(message, stackTrace, comparisonPair.second, comparisonPair.first);
        }
        else {
          testProxy.setTestFailed(message, stackTrace, "error".equals(failureType));
        }
      }
      getResultsViewer().onTestFailed(testProxy);
      getExecutionConsole().getEventPublisher().onTestFailed(testProxy);
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else if (resultType == TestEventResult.SKIPPED) {
      testProxy.setTestIgnored(null, null);
      getResultsViewer().onTestIgnored(testProxy);
      getExecutionConsole().getEventPublisher().onTestIgnored(testProxy);
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
    else {
      LOG.error("Undefined test result: " + resultType);
    }
  }
}
