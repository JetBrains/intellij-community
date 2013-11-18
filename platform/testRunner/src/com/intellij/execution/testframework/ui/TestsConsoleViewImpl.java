/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.impl.ConsoleState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ConsoleViewRunningState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author Roman.Chernyatchik
 */
public class TestsConsoleViewImpl extends ConsoleViewImpl {

  public TestsConsoleViewImpl(final Project project,
                              final GlobalSearchScope searchScope,
                              final boolean viewer,
                              boolean usePredefinedMessageFilter) {
    super(project, searchScope, viewer,
          new ConsoleState.NotStartedStated() {
            @Override
            public ConsoleState attachTo(ConsoleViewImpl console, ProcessHandler processHandler) {
              return new ConsoleViewRunningState(console, processHandler, this, false, !viewer);
            }
          },
          usePredefinedMessageFilter);
  }
}
