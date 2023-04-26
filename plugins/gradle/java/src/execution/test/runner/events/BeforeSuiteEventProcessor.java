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

import java.util.Objects;

/**
 * @author Vladislav.Soroka
 */
public class BeforeSuiteEventProcessor extends AbstractTestEventProcessor {
  public BeforeSuiteEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  protected boolean isSuite() {
    return true;
  }

  @Override
  public void process(@NotNull final TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    var testId = eventXml.getTestId();
    var parentTestId = eventXml.getTestParentId();
    var suiteName = ObjectUtils.coalesce(StringUtil.nullize(eventXml.getTestClassName()), eventXml.getTestDisplayName());
    var fqClassName = eventXml.getTestClassName();
    var displayName = eventXml.getTestDisplayName();

    doProcess(testId, parentTestId, suiteName, fqClassName, displayName);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    var testDescriptor = testEvent.getDescriptor();
    var testId = testEvent.getEventId();
    var parentTestId = testEvent.getParentEventId();
    var suiteName = StringUtil.notNullize(testDescriptor.getSuiteName());
    var fqClassName = StringUtil.notNullize(testDescriptor.getClassName());
    var displayName = testDescriptor.getDisplayName();

    doProcess(testId, parentTestId, suiteName, fqClassName, displayName);
  }

  private void doProcess(
    @NotNull String testId,
    @Nullable String parentTestId,
    @NotNull String suiteName,
    @NotNull String fqClassName,
    @NotNull String displayName
  ) {
    var isCombineSameTests = !showInternalTestNodes();

    if (isCombineSameTests && isHiddenTestNode(suiteName)) {
      var parentTestProxy = findParentTestProxy(parentTestId);
      registerTestProxy(testId, parentTestProxy);
      return;
    }

    if (isCombineSameTests) {
      var testProxy = findSuiteTestProxy(suiteName, parentTestId);
      if (testProxy != null) {
        registerTestProxy(testId, testProxy);
        return;
      }
    }

    var testProxy = createTestProxy(parentTestId, suiteName, fqClassName, null, displayName);
    registerTestProxy(testId, testProxy);

    if (isCombineSameTests) {
      registerTestProxy(fqClassName, testProxy);
    }
  }

  private @Nullable GradleSMTestProxy findSuiteTestProxy(@NotNull String suiteId, @Nullable String parentTestId) {
    var parentTestProxy = findParentTestProxy(parentTestId);
    var testProxy = findTestProxy(suiteId);
    if (testProxy instanceof GradleSMTestProxy) {
      if (Objects.equals(testProxy.getParent(), parentTestProxy)) {
        return (GradleSMTestProxy)testProxy;
      }
    }
    return null;
  }

  private static boolean isHiddenTestNode(@Nullable String suiteName) {
    return suiteName == null || suiteName.startsWith("Gradle Test Executor") || suiteName.startsWith("Gradle Test Run");
  }
}
