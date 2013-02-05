/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public class MvcRunTarget extends MvcActionBase {

  @Override
  protected void actionPerformed(@NotNull AnActionEvent e, @NotNull Module module, @NotNull MvcFramework framework) {
    MvcRunTargetDialog dialog = new MvcRunTargetDialog(module, framework);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    Module selectedModule = dialog.getSelectedModule();

    MvcCommand cmd = MvcCommand.parse(dialog.getTargetArguments());

    final GeneralCommandLine commandLine = framework.createCommandAndShowErrors(dialog.getVmOptions(), selectedModule, cmd);
    if (commandLine == null) return;

    MvcConsole.executeProcess(selectedModule, commandLine, null, false);
  }

}
