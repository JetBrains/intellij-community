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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.util.Collections;
import java.util.List;

public class AfterTestEventProcessor extends AbstractTestEventProcessor {

  private static final Logger LOG = Logger.getInstance(AfterTestEventProcessor.class);

  public AfterTestEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    var finishEvent = (ExternalSystemFinishEvent<? extends TestOperationDescriptor>)testEvent;
    var testId = finishEvent.getEventId();
    var testResult = finishEvent.getOperationResult();
    process(testId, testResult);
  }

  @Override
  public void process(@NotNull TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException, NumberFormatException {
    var testId = eventXml.getTestId();
    var testResult = convertTestResult(eventXml);
    process(testId, testResult);
  }

  private @NotNull OperationResult convertTestResult(
    @NotNull TestEventXmlView eventXml
  ) throws TestEventXmlView.XmlParserException, NumberFormatException {
    var startTime = Long.parseLong(eventXml.getEventTestResultStartTime());
    var endTime = Long.parseLong(eventXml.getEventTestResultEndTime());
    var resultType = TestEventResult.fromValue(eventXml.getTestEventResultType());

    if (resultType == TestEventResult.SUCCESS) {
      return new SuccessResultImpl(startTime, endTime, false);
    }
    if (resultType == TestEventResult.SKIPPED) {
      return new SkippedResultImpl(startTime, endTime);
    }
    if (resultType == TestEventResult.FAILURE) {
      var failureType = eventXml.getEventTestResultFailureType();
      var message = decode(eventXml.getEventTestResultErrorMsg()); //NON-NLS
      var stackTrace = decode(eventXml.getEventTestResultStackTrace()); //NON-NLS
      var description = decode(eventXml.getTestEventTestDescription()); //NON-NLS
      if ("comparison".equals(failureType)) {
        var actualText = decode(eventXml.getEventTestResultActual());
        var expectedText = decode(eventXml.getEventTestResultExpected());
        var expectedFilePath = StringUtil.nullize(decode(eventXml.getEventTestResultFilePath()));
        var actualFilePath = StringUtil.nullize(decode(eventXml.getEventTestResultActualFilePath()));
        var assertionFailure = new TestAssertionFailure(
          message, stackTrace, description, ContainerUtil.emptyList(), expectedText, actualText, expectedFilePath, actualFilePath
        );
        return new FailureResultImpl(startTime, endTime, List.of(assertionFailure));
      }
      if ("assertionFailed".equals(failureType)) {
        var failure = new TestFailure(message, stackTrace, description, Collections.emptyList(), false);
        return new FailureResultImpl(startTime, endTime, List.of(failure));
      }
      if ("error".equals(failureType)) {
        var failure = new TestFailure(message, stackTrace, description, Collections.emptyList(), true);
        return new FailureResultImpl(startTime, endTime, List.of(failure));
      }
      LOG.error("Undefined test failure type: " + failureType);
      var failure = new FailureImpl(message, stackTrace, Collections.emptyList());
      return new FailureResultImpl(startTime, endTime, List.of(failure));
    }
    LOG.error("Undefined test result type: " + resultType);
    return new DefaultOperationResult(startTime, endTime);
  }

  private void process(@NotNull String testId, @NotNull OperationResult testResult) {
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
      LOG.error("Undefined test result: " + testResult.getClass().getName());
      getResultsViewer().onTestFinished(testProxy);
      getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
    }
  }

  private static void processFailureResult(@NotNull SMTestProxy testProxy, @NotNull Failure failure) {
    if (failure instanceof TestFailure testFailure) {
      processTestFailureResult(testProxy, testFailure);
    }
    else {
      LOG.error("Undefined test failure type: " + failure.getClass().getName());
      var message = ObjectUtils.doIfNotNull(failure, it -> it.getMessage());
      var description = ObjectUtils.doIfNotNull(failure, it -> it.getDescription());
      testProxy.setTestFailed(message, description, true);
    }
    for (var cause : failure.getCauses()) {
      processFailureResult(testProxy, cause);
    }
  }

  private static void processTestFailureResult(@NotNull SMTestProxy testProxy, @NotNull TestFailure failure) {
    var message = ObjectUtils.doIfNotNull(failure, it -> it.getMessage());
    var stackTrace = failure.getStackTrace();
    var comparisonResult = ObjectUtils.doIfNotNull(message, it -> AssertionParser.parse(it));
    if (failure instanceof TestAssertionFailure assertionFailure) {
      var localizedMessage = comparisonResult == null ? message : comparisonResult.getMessage();
      var actualText = assertionFailure.getActualText();
      var expectedText = assertionFailure.getExpectedText();
      var actualFile = assertionFailure.getActualFile();
      var expectedFile = assertionFailure.getExpectedFile();
      testProxy.setTestComparisonFailed(localizedMessage, stackTrace, actualText, expectedText, actualFile, expectedFile, true);
    }
    else if (comparisonResult != null && failure.getCauses().isEmpty()) {
      var localizedMessage = comparisonResult.getMessage();
      var actualText = comparisonResult.getActual();
      var expectedText = comparisonResult.getExpected();
      testProxy.setTestComparisonFailed(localizedMessage, stackTrace, actualText, expectedText);
    }
    else if (message != null && stackTrace != null && StringUtil.contains(stackTrace, message)) {
      testProxy.setTestFailed(null, stackTrace, failure.isTestError());
    }
    else {
      testProxy.setTestFailed(message, stackTrace, failure.isTestError());
    }
  }
}
