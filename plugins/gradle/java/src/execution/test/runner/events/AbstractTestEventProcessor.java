// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleConsoleProperties;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

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
    return getExecutionConsole().getResultsViewer();
  }

  protected Project getProject() {
    return getProperties().getProject();
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
    getExecutionConsole().getTestsMap().put(proxyId, testProxy);
  }

  protected boolean showInternalTestNodes() {
    return GradleConsoleProperties.SHOW_INTERNAL_TEST_NODES.value(getProperties());
  }

  protected @NotNull GradleSMTestProxy createTestProxy(
    @Nullable String parentTestId,
    @NotNull String suiteName,
    @NotNull String className,
    @Nullable String methodName,
    @NotNull String displayName
  ) {
    var project = getProject();
    var isSuite = isSuite();
    var parentTestProxy = findParentTestProxy(parentTestId);
    var eventConverter = new GradleTestEventConverter(project, parentTestProxy, isSuite, suiteName, className, methodName, displayName);
    var aClassName = eventConverter.getConvertedClassName();
    var aMethodName = eventConverter.getConvertedMethodName();
    var aParamName = eventConverter.getConvertedParameterName();
    var aDisplayName = eventConverter.getConvertedDisplayName();
    var locationProtocol = isSuite ? JavaTestLocator.SUITE_PROTOCOL : JavaTestLocator.TEST_PROTOCOL;
    var locationUrl = JavaTestLocator.createLocationUrl(locationProtocol, aClassName, aMethodName, aParamName);
    var testProxy = new GradleSMTestProxy(aDisplayName, isSuite, locationUrl);
    testProxy.setLocator(getExecutionConsole().getUrlProvider());
    testProxy.setParentId(parentTestId);
    return testProxy;
  }
}
