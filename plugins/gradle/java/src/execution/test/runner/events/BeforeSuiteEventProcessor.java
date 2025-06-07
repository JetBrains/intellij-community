// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 */
public class BeforeSuiteEventProcessor extends AbstractBeforeTestEventProcessor {
  public BeforeSuiteEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  protected boolean isSuite() {
    return true;
  }

  @Override
  public void process(final @NotNull TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    var testId = eventXml.getTestId();
    var parentTestId = eventXml.getTestParentId();
    var suiteName = ObjectUtils.coalesce(StringUtil.nullize(eventXml.getTestClassName()), eventXml.getTestDisplayName());
    var fqClassName = eventXml.getTestClassName();
    var displayName = eventXml.getTestDisplayName();

    doProcess(testId, parentTestId, suiteName, fqClassName, null, displayName);
  }

  @Override
  protected void doProcess(
    @NotNull String testId,
    @Nullable String parentTestId,
    @NotNull String suiteName,
    @NotNull String fqClassName,
    @Nullable String methodName,
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

    var testProxy = createTestProxy(parentTestId, suiteName, fqClassName, methodName, displayName);
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

  private static final Pattern GRADLE_PARTITION_SUITE_NAME = Pattern.compile("Partition \\d+ in session \\d+");
  private static boolean isHiddenTestNode(@Nullable String suiteName) {
    return suiteName == null
           || suiteName.startsWith("Gradle Test Executor")
           || suiteName.startsWith("Gradle Test Run")
           || GRADLE_PARTITION_SUITE_NAME.matcher(suiteName).lookingAt();
  }
}
