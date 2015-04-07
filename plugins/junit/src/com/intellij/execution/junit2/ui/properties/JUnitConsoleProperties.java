/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JUnitConsoleProperties extends JavaAwareTestConsoleProperties {

  private final JUnitConfiguration myConfiguration;

  public JUnitConsoleProperties(@NotNull JUnitConfiguration configuration, Executor executor) {
    super("JUnit", configuration, executor);
    myConfiguration = configuration;
  }

  @Override
  protected GlobalSearchScope initScope() {
    final SourceScope sourceScope = myConfiguration.getPersistentData().getScope().getSourceScope(myConfiguration);
    return sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.allScope(getProject());
  }

  @Override
  protected void appendAdditionalActions(DefaultActionGroup actionGroup,
                                         ExecutionEnvironment environment, JComponent parent) {
    super.appendAdditionalActions(actionGroup, environment, parent);
    actionGroup.addAction(createIncludeNonStartedInRerun()).setAsSecondary(true);
  }
}
