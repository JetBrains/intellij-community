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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public class GroovyConsoleAction extends GroovyShellActionBase implements DumbAware {

  public GroovyConsoleAction() {
    super(new MyHandler());
  }

  private static class MyHandler extends GroovyShellHandler {

    @Override
    public GroovyShellRunner getRunner(Module module) {
      return GroovyShellRunner.getAppropriateRunner(module);
    }

    @Override
    public String getTitle() {
      return "Groovy Console";
    }

    @Override
    protected LanguageConsoleView createConsole(Project project, String title) {
      return new GroovyLanguageConsoleView.Console(project, title);
    }
  }
}
