// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.util.function.Consumer;

/**
 * @author Vladislav.Soroka
 */
public class BeforeTestEventProcessor extends AbstractTestEventProcessor {

  public BeforeTestEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull final TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    var testId = eventXml.getTestId();
    var parentTestId = eventXml.getTestParentId();
    var suiteName = eventXml.getTestClassName();
    var fqClassName = eventXml.getTestClassName();
    var methodName = eventXml.getTestName();
    var displayName = eventXml.getTestDisplayName();

    doProcess(testId, parentTestId, suiteName, fqClassName, methodName, displayName);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    var testDescriptor = testEvent.getDescriptor();
    var testId = testEvent.getEventId();
    var parentTestId = testEvent.getParentEventId();
    var suiteName = StringUtil.notNullize(testDescriptor.getSuiteName());
    var fqClassName = StringUtil.notNullize(testDescriptor.getClassName());
    var methodName = testDescriptor.getMethodName();
    var displayName = testDescriptor.getDisplayName();

    doProcess(testId, parentTestId, suiteName, fqClassName, methodName, displayName);
  }

  private void doProcess(
    @NotNull String testId,
    @Nullable String parentTestId,
    @NotNull String suiteName,
    @NotNull String fqClassName,
    @Nullable String methodName,
    @NotNull String displayName
  ) {
    var testProxy = createTestProxy(parentTestId, suiteName, fqClassName, methodName, displayName);
    registerTestProxy(testId, testProxy);
    // BeforeTestSuiteProcessor doesn't initialize suites (start and register in tree),
    // because Gradle generates empty events for suites with filtered tests.
    // But we can identify it only when we won't get any test events.
    // So we register and start test suites in tree only here.
    traverseTestNodesFromChildToParent(testProxy, node -> {
      setParentIfNeeded(node);
      setStartedIfNeeded(node);
    });
  }

  private void setParentIfNeeded(@NotNull GradleSMTestProxy testProxy) {
    if (testProxy.getParent() == null) {
      var parentId = testProxy.getParentId();
      var parentNode = findParentTestProxy(parentId);
      parentNode.addChild(testProxy);
    }
  }

  private void setStartedIfNeeded(@NotNull GradleSMTestProxy testProxy) {
    if (!testProxy.isInProgress()) {
      if (testProxy.isSuite()) {
        testProxy.setStarted();
        getResultsViewer().onSuiteStarted(testProxy);
        getExecutionConsole().getEventPublisher().onSuiteStarted(testProxy);
      }
      else {
        testProxy.setStarted();
        getResultsViewer().onTestStarted(testProxy);
        getExecutionConsole().getEventPublisher().onTestStarted(testProxy);
      }
    }
  }

  private static void traverseTestNodesFromChildToParent(
    @NotNull GradleSMTestProxy node,
    @NotNull Consumer<GradleSMTestProxy> process
  ) {
    while (node != null) {
      process.accept(node);
      node = ObjectUtils.tryCast(node.getParent(), GradleSMTestProxy.class);
    }
  }
}
