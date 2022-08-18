// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class BeforeTestEvent extends AbstractTestEvent {

  public BeforeTestEvent(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull final TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    final String testId = eventXml.getTestId();
    final String parentTestId = eventXml.getTestParentId();
    final String name = eventXml.getTestName();
    final String displayName = eventXml.getTestDisplayName();
    final String fqClassName = eventXml.getTestClassName();

    doProcess(testId, parentTestId, name, displayName, fqClassName);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    TestOperationDescriptor testDescriptor = testEvent.getDescriptor();
    final String testId = testEvent.getEventId();
    final String parentTestId = testEvent.getParentEventId();
    final String fqClassName = testDescriptor.getClassName();

    doProcess(testId, parentTestId, testDescriptor.getMethodName(), testDescriptor.getDisplayName(), fqClassName);
  }

  private void doProcess(String testId, String parentTestId, String name, String displayName, String fqClassName) {
    String locationUrl = computeLocationUrl(findTestProxy(parentTestId), fqClassName, name, displayName);
    final GradleSMTestProxy testProxy = new GradleSMTestProxy(displayName, false, locationUrl, fqClassName);

    testProxy.setStarted();
    testProxy.setLocator(getExecutionConsole().getUrlProvider());
    registerTestProxy(testId, testProxy);

    if (StringUtil.isEmpty(parentTestId)) {
      getResultsViewer().getTestsRootNode().addChild(testProxy);
    }
    else {
      final SMTestProxy parentTestProxy = findTestProxy(parentTestId);
      if (parentTestProxy != null) {
        final List<GradleSMTestProxy> notYetAddedParents = new SmartList<>();
        SMTestProxy currentParentTestProxy = parentTestProxy;
        while (currentParentTestProxy instanceof GradleSMTestProxy) {
          final String parentId = ((GradleSMTestProxy)currentParentTestProxy).getParentId();
          if (currentParentTestProxy.getParent() == null && parentId != null) {
            notYetAddedParents.add((GradleSMTestProxy)currentParentTestProxy);
          }
          currentParentTestProxy = findTestProxy(parentId);
        }

        for (GradleSMTestProxy gradleSMTestProxy : ContainerUtil.reverse(notYetAddedParents)) {
          final SMTestProxy parentTestProxy1 = findTestProxy(gradleSMTestProxy.getParentId());
          if (parentTestProxy1 != null) {
            parentTestProxy1.addChild(gradleSMTestProxy);
            getResultsViewer().onSuiteStarted(gradleSMTestProxy);
            getExecutionConsole().getEventPublisher().onSuiteStarted(gradleSMTestProxy);
          }
        }
        parentTestProxy.addChild(testProxy);
      }
    }

    getResultsViewer().onTestStarted(testProxy);
    getExecutionConsole().getEventPublisher().onTestStarted(testProxy);
  }
}
