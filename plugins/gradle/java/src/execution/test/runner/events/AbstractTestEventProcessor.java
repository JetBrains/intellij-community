// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleConsoleProperties;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationCustomizer;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractTestEventProcessor implements TestEventProcessor {

  private final GradleTestsExecutionConsole myExecutionConsole;

  public AbstractTestEventProcessor(GradleTestsExecutionConsole executionConsole) {
    this.myExecutionConsole = executionConsole;
  }

  public GradleTestsExecutionConsole getExecutionConsole() {
    return myExecutionConsole;
  }

  protected SMTestRunnerResultsForm getResultsViewer() {
    return myExecutionConsole.getResultsViewer();
  }

  protected Project getProject() {
    return myExecutionConsole.getProperties().getProject();
  }

  protected GradleConsoleProperties getProperties() {
    return (GradleConsoleProperties)getExecutionConsole().getProperties();
  }

  protected boolean isSuite() {
    return false;
  }

  protected @Nullable SMTestProxy findTestProxy(@Nullable String testId) {
    return getExecutionConsole().getTestsMap().get(testId);
  }

  protected @NotNull SMTestProxy findParentTestProxy(@Nullable String parentTestId) {
    var rootNode = getResultsViewer().getTestsRootNode();
    if (StringUtil.isEmpty(parentTestId)) {
      return rootNode;
    }
    var node = findTestProxy(parentTestId);
    if (node == null) {
      return rootNode;
    }
    return node;
  }

  protected void registerTestProxy(final String proxyId, SMTestProxy testProxy) {
    myExecutionConsole.getTestsMap().put(proxyId, testProxy);
  }

  protected String decode(String s) {
    return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
  }

  protected boolean showInternalTestNodes() {
    return GradleConsoleProperties.SHOW_INTERNAL_TEST_NODES.value(getProperties());
  }

  protected @NotNull GradleSMTestProxy createTestProxy(
    @Nullable String parentTestId,
    @NotNull String suiteName,
    @NotNull String fqClassName,
    @Nullable String methodName,
    @Nullable String displayName
  ) {
    var parentTestProxy = findParentTestProxy(parentTestId);
    var locationUrl = createLocationUrl(parentTestProxy, suiteName, fqClassName, methodName);
    var testProxy = new GradleSMTestProxy(displayName, methodName == null, locationUrl, fqClassName);
    testProxy.setLocator(getExecutionConsole().getUrlProvider());
    testProxy.setParentId(parentTestId);
    return testProxy;
  }

  private @NotNull String createLocationUrl(
    @NotNull SMTestProxy parentProxy,
    @NotNull String suiteName,
    @NotNull String fqClassName,
    @Nullable String methodName
  ) {
    var project = getProject();
    var isSuite = isSuite();
    var testLocationCustomizer = GradleTestLocationCustomizer.EP_NAME
      .findFirstSafe(it -> it.isApplicable(project, parentProxy, isSuite, suiteName, fqClassName, methodName));
    if (testLocationCustomizer != null) {
      return testLocationCustomizer.createLocationUrl(parentProxy, isSuite, suiteName, fqClassName, methodName);
    }
    var locationProtocol = isSuite ? JavaTestLocator.SUITE_PROTOCOL : JavaTestLocator.TEST_PROTOCOL;
    return JavaTestLocator.createLocationUrl(locationProtocol, fqClassName, methodName);
  }

  protected void setParentForAllNodesInTreePath(@NotNull GradleSMTestProxy node) {
    while (node != null) {
      var parentId = node.getParentId();
      var parentNode = findParentTestProxy(parentId);
      if (node.getParent() == null) {
        parentNode.addChild(node);
      }
      if (!node.isInProgress()) {
        node.setStarted();
        getResultsViewer().onTestStarted(node);
        getExecutionConsole().getEventPublisher().onTestStarted(node);
      }
      node = ObjectUtils.tryCast(parentNode, GradleSMTestProxy.class);
    }
  }

  protected void setStartedForAllNodesInTreePath(@NotNull GradleSMTestProxy node) {
    while (node != null) {
      if (!node.isInProgress()) {
        node.setStarted();
        getResultsViewer().onTestStarted(node);
        getExecutionConsole().getEventPublisher().onTestStarted(node);
      }
      node = ObjectUtils.tryCast(node.getParent(), GradleSMTestProxy.class);
    }
  }
}
