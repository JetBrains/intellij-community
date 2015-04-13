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
package org.jetbrains.plugins.groovy.shell;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;

public abstract class GroovyShellHandler {

  private static final Logger LOG = Logger.getInstance(GroovyShellHandler.class);

  public void doRunShell(final Module module) {
    final GroovyShellRunner shellRunner = getRunner(module);
    if (shellRunner == null) return;

    try {
      new GroovyConsoleRunnerImpl(getTitle(), this, shellRunner, module).initAndRun();
    }
    catch (ExecutionException e) {
      LOG.info(e);
      Messages.showErrorDialog(module.getProject(), e.getMessage(), "Cannot Run " + getTitle());
    }
  }

  public boolean isSuitableModule(Module module) {
    return GroovyFacetUtil.isSuitableModule(module);
  }

  public abstract GroovyShellRunner getRunner(Module module);

  public abstract String getTitle();

  protected abstract LanguageConsoleView createConsole(Project project, String title);
}
