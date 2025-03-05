// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.impl.ConsoleState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ConsoleViewRunningState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
class TestsConsoleViewImpl extends ConsoleViewImpl {

  TestsConsoleViewImpl(final Project project,
                       final GlobalSearchScope searchScope,
                       final boolean viewer,
                       boolean usePredefinedMessageFilter) {
    super(project, searchScope, viewer,
          new ConsoleState.NotStartedStated() {
            @Override
            public @NotNull ConsoleState attachTo(@NotNull ConsoleViewImpl console, @NotNull ProcessHandler processHandler) {
              return new ConsoleViewRunningState(console, processHandler, this, false, !viewer);
            }
          },
          usePredefinedMessageFilter);
  }
}
