// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.ui.properties;

import com.intellij.execution.Executor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.junit.RepeatCount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JUnitConsoleProperties extends JavaAwareTestConsoleProperties<JUnitConfiguration> {


  public JUnitConsoleProperties(@NotNull JUnitConfiguration configuration, Executor executor) {
    super("JUnit", configuration, executor);
  }

  @Override
  protected @NotNull GlobalSearchScope initScope() {
    final JUnitConfiguration.Data persistentData = getConfiguration().getPersistentData();
    final String testObject = persistentData.TEST_OBJECT;
    //ignore invisible setting
    if (JUnitConfiguration.TEST_CATEGORY.equals(testObject) ||
        JUnitConfiguration.TEST_PATTERN.equals(testObject) ||
        JUnitConfiguration.TEST_PACKAGE.equals(testObject)) {
      final SourceScope sourceScope = persistentData.getScope().getSourceScope(getConfiguration());
      return sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.allScope(getProject());
    }
    else {
      return super.initScope();
    }
  }

  @Override
  public void appendAdditionalActions(DefaultActionGroup actionGroup,
                                      JComponent parent, TestConsoleProperties target) {
    super.appendAdditionalActions(actionGroup, parent, target);
    actionGroup.addSeparator();
    actionGroup.add(createIncludeNonStartedInRerun(target));
  }

  @Override
  public SMTestLocator getTestLocator() {
    return JavaTestLocator.INSTANCE;
  }

  @Override
  public @Nullable AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return new RerunFailedTestsAction(consoleView, this);
  }

  @Override
  public boolean isUndefined() {
    final String mode = getConfiguration().getRepeatMode();
    return RepeatCount.UNLIMITED.equals(mode) || RepeatCount.UNTIL_FAILURE.equals(mode) || RepeatCount.UNTIL_SUCCESS.equals(mode);
  }
}
