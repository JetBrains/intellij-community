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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.util.function.Predicate;

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
    var testProxy = findTestProxy(testId);
    if (testProxy == null) {
      LOG.error("Cannot find test proxy for: " + testId);
      return;
    }

    var result = finishEvent.getOperationResult();

    var startTime = result.getStartTime();
    var endTime = result.getEndTime();

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
      var exceptionMessage = ObjectUtils.doIfNotNull(failure, it -> it.getMessage());
      var stackTrace = ObjectUtils.doIfNotNull(failure, it -> it.getDescription());
      testProxy.setTestFailed(exceptionMessage, stackTrace, false);
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
  public void process(@NotNull final TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {

    final String testId = eventXml.getTestId();

    final String startTime = eventXml.getEventTestResultStartTime();
    final String endTime = eventXml.getEventTestResultEndTime();
    final String exceptionMsg = decode(eventXml.getEventTestResultErrorMsg());
    final String stackTrace = decode(eventXml.getEventTestResultStackTrace());
    final TestEventResult result = TestEventResult.fromValue(eventXml.getTestEventResultType());

    final SMTestProxy testProxy = findTestProxy(testId);
    if (testProxy == null) return;

    try {
      testProxy.setDuration(Long.parseLong(endTime) - Long.parseLong(startTime));
    }
    catch (NumberFormatException ignored) {
    }

    switch (result) {
      case SUCCESS:
        testProxy.setFinished();
        break;
      case FAILURE:
        final String failureType = eventXml.getEventTestResultFailureType();
        if ("comparison".equals(failureType)) {
          String actualText = decode(eventXml.getEventTestResultActual());
          String expectedText = decode(eventXml.getEventTestResultExpected());
          final Predicate<String> emptyString = StringUtil::isEmpty;
          String filePath = ObjectUtils.nullizeByCondition(decode(eventXml.getEventTestResultFilePath()), emptyString);
          String actualFilePath = ObjectUtils.nullizeByCondition(
            decode(eventXml.getEventTestResultActualFilePath()), emptyString);
          testProxy.setTestComparisonFailed(exceptionMsg, stackTrace, actualText, expectedText, filePath, actualFilePath, true);
        }
        else {
          Couple<String> comparisonPair = parseComparisonMessage(exceptionMsg);
          if (comparisonPair != null) {
            testProxy.setTestComparisonFailed(exceptionMsg, stackTrace, comparisonPair.second, comparisonPair.first);
          }
          else {
            testProxy.setTestFailed(exceptionMsg, stackTrace, "error".equals(failureType));
          }
        }
        getResultsViewer().onTestFailed(testProxy);
        getExecutionConsole().getEventPublisher().onTestFailed(testProxy);
        break;
      case SKIPPED:
        testProxy.setTestIgnored(null, null);
        getResultsViewer().onTestIgnored(testProxy);
        getExecutionConsole().getEventPublisher().onTestIgnored(testProxy);
        break;
      case UNKNOWN_RESULT:
        break;
    }

    getResultsViewer().onTestFinished(testProxy);
    getExecutionConsole().getEventPublisher().onTestFinished(testProxy);
  }
}
