// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.util.Objects;

/**
 * @author Vladislav.Soroka
 */
public class BeforeSuiteEvent extends AbstractTestEvent {
  public BeforeSuiteEvent(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull final TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    final String testId = eventXml.getTestId();
    final String parentTestId = eventXml.getTestParentId();
    final String name = eventXml.getTestDisplayName();
    final String fqClassName = eventXml.getTestClassName();

    doProcess(testId, parentTestId, name, fqClassName);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    TestOperationDescriptor testDescriptor = testEvent.getDescriptor();
    final String testId = testEvent.getEventId();
    final String parentTestId = testEvent.getParentEventId();
    final String name = ObjectUtils.coalesce(testDescriptor.getDisplayName(), testDescriptor.getMethodName(), testId);
    final String fqClassName = testDescriptor.getClassName();

    doProcess(testId, parentTestId, name, fqClassName);
  }


  private void doProcess(String testId, String parentTestId, String name, String fqClassName) {
    if (StringUtil.isEmpty(parentTestId)) {
      registerTestProxy(testId, getResultsViewer().getTestsRootNode());
    }
    else {
      SMTestProxy parentTest = findTestProxy(parentTestId);
      if (isHiddenTestNode(name, parentTest)) {
        registerTestProxy(testId, parentTest);
      }
      else {
        boolean combineTestsOfTheSameSuite = !showInternalTestNodes();
        String sameSuiteId = name + fqClassName;
        if (combineTestsOfTheSameSuite) {
          SMTestProxy testProxy = findTestProxy(sameSuiteId);
          if (testProxy instanceof GradleSMTestProxy && Objects.equals(testProxy.getParent(), parentTest)) {
            registerTestProxy(testId, testProxy);
            if (!testProxy.isInProgress()) {
              testProxy.setStarted();
            }
            return;
          }
        }

        String locationUrl = computeLocationUrl(parentTest, fqClassName, null, name);
        final GradleSMTestProxy testProxy = new GradleSMTestProxy(name, true, locationUrl, null);
        testProxy.setLocator(getExecutionConsole().getUrlProvider());
        testProxy.setParentId(parentTestId);
        testProxy.setStarted();
        registerTestProxy(testId, testProxy);
        if (combineTestsOfTheSameSuite) {
          registerTestProxy(sameSuiteId, testProxy);
        }
      }
    }
  }

  @Override
  @NotNull
  protected String findLocationUrl(@Nullable String name, @NotNull String fqClassName) {
    return findLocationUrl(JavaTestLocator.SUITE_PROTOCOL, name, fqClassName);
  }

  private boolean isHiddenTestNode(String name, SMTestProxy parentTest) {
    return parentTest != null &&
           !showInternalTestNodes() &&
           StringUtil.startsWith(name, "Gradle Test Executor");
  }
}
