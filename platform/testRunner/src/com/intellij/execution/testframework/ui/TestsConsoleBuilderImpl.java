// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public class TestsConsoleBuilderImpl extends TextConsoleBuilderImpl {
  public TestsConsoleBuilderImpl(@NotNull Project project,
                                 @NotNull GlobalSearchScope scope,
                                 boolean isViewer,
                                 boolean usePredefinedMessageFilter) {
    super(project, scope);
    setViewer(isViewer);
    setUsePredefinedMessageFilter(usePredefinedMessageFilter);
  }

  @Override
  protected @NotNull ConsoleView createConsole() {
    return new TestsConsoleViewImpl(getProject(), getScope(), isViewer(), isUsePredefinedMessageFilter());
  }
}
