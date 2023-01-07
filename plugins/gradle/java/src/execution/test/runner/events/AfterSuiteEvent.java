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

import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemFinishEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

/**
 * @author Vladislav.Soroka
 */
public class AfterSuiteEvent extends AbstractTestEvent {
  public AfterSuiteEvent(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }


  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    if (!(testEvent instanceof ExternalSystemFinishEvent)) {
      return;
    }
    final String testId = testEvent.getEventId();
    TestEventResult result = TestEventResult.fromOperationResult(((ExternalSystemFinishEvent<?>)testEvent).getOperationResult());

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
        case SUCCESS:
          testProxy.setFinished();
          break;
        case FAILURE:
          testProxy.setTestFailed("", null, false);
          break;
        case SKIPPED:
          testProxy.setTestIgnored(null, null);
          break;
        case UNKNOWN_RESULT:
          break;
      }
      getResultsViewer().onSuiteFinished(testProxy);
      getExecutionConsole().getEventPublisher().onSuiteFinished(testProxy);
    }
  }

  @Override
  @NotNull
  protected String findLocationUrl(@Nullable String name, @NotNull String fqClassName) {
    return findLocationUrl(JavaTestLocator.SUITE_PROTOCOL, name, fqClassName);
  }
}
