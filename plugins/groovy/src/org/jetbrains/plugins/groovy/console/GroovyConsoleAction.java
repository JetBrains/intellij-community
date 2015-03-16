/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Max Medvedev on 9/20/13
 */
public class GroovyConsoleAction extends GroovyShellActionBase implements DumbAware {

  @Override
  protected GroovyShellRunner getRunner(Module module) {
    return GroovyShellRunner.getAppropriateRunner(module);
  }

  @Override
  public String getTitle() {
    return "Groovy Console";
  }

  @Override
  protected LanguageConsoleView createConsole(Project project, String title) {
    return new GroovyConsoleImpl(project, title) {

      @Override
      protected boolean isShell() {
        return false;
      }

      @NotNull
      @Override
      protected String addToHistoryInner(@NotNull TextRange textRange,
                                         @NotNull EditorEx editor,
                                         boolean erase,
                                         boolean preserveMarkup) {
        final String result = super.addToHistoryInner(textRange, editor, erase, preserveMarkup);
        processCode();
        return result;
      }
    };
  }
}
