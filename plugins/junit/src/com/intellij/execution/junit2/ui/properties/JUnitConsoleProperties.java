/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit2.ui.properties;

import com.intellij.execution.Executor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JUnitConsoleProperties extends JavaAwareTestConsoleProperties<JUnitConfiguration> {

  private final JUnitConfiguration myConfiguration;

  public JUnitConsoleProperties(@NotNull JUnitConfiguration configuration, Executor executor) {
    super("JUnit", configuration, executor);
    myConfiguration = configuration;
  }

  @NotNull
  @Override
  protected GlobalSearchScope initScope() {
    final JUnitConfiguration.Data persistentData = myConfiguration.getPersistentData();
    final String testObject = persistentData.TEST_OBJECT;
    //ignore invisible setting
    if (JUnitConfiguration.TEST_CATEGORY.equals(testObject) ||
        JUnitConfiguration.TEST_PATTERN.equals(testObject) ||
        JUnitConfiguration.TEST_PACKAGE.equals(testObject)) {
      final SourceScope sourceScope = persistentData.getScope().getSourceScope(myConfiguration);
      return sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.allScope(getProject());
    }
    else {
      return super.initScope();
    }
  }

  @Override
  protected void appendAdditionalActions(DefaultActionGroup actionGroup,
                                         JComponent parent) {
    super.appendAdditionalActions(actionGroup, parent);
    actionGroup.add(createIncludeNonStartedInRerun());
  }

  @Override
  public SMTestLocator getTestLocator() {
    return JavaTestLocator.INSTANCE;
  }

  @Nullable
  @Override
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return new RerunFailedTestsAction(consoleView, this);
  }
}
