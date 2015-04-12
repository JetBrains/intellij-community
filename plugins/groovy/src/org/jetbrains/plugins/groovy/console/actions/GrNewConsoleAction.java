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
package org.jetbrains.plugins.groovy.console.actions;

import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.plugins.groovy.console.GroovyConsole;
import org.jetbrains.plugins.groovy.console.GroovyConsoleRootType;
import org.jetbrains.plugins.groovy.console.GroovyConsoleUtil;
import org.jetbrains.plugins.groovy.console.GroovyProjectConsole;

public class GrNewConsoleAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    GroovyConsoleUtil.selectModuleAndRun(project, new Consumer<Module>() {
      @Override
      public void consume(Module module) {
        final VirtualFile contentFile = ConsoleHistoryController.getContentFile(
          GroovyConsoleRootType.getInstance(),
          "groovy-console",
          ScratchFileService.Option.create_new_always
        );
        assert contentFile != null;
        GroovyProjectConsole.getInstance(project).setFileModule(contentFile, module);
        GroovyConsole.createConsole(project, contentFile, module);
        FileEditorManager.getInstance(project).openFile(contentFile, true);
      }
    });
  }
}
