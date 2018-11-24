/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.console.GroovyConsole;
import org.jetbrains.plugins.groovy.console.GroovyConsoleRootType;
import org.jetbrains.plugins.groovy.statictics.GroovyStatisticsIds;

import static org.jetbrains.plugins.groovy.console.GroovyConsoleUtilKt.getAnyApplicableModule;

public class GrNewConsoleAction extends AnAction {

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getModule(e) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final Module module = getModule(e);
    if (project == null || module == null) return;

    UsageTrigger.trigger(GroovyStatisticsIds.GROOVY_NEW_CONSOLE);
    final VirtualFile contentFile = ConsoleHistoryController.getContentFile(
      GroovyConsoleRootType.getInstance(),
      GroovyConsoleRootType.CONTENT_ID,
      ScratchFileService.Option.create_new_always
    );
    assert contentFile != null;
    GroovyConsole.createConsole(project, contentFile, module);
    FileEditorManager.getInstance(project).openFile(contentFile, true);
  }

  @Nullable
  protected Module getModule(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return null;
    return getAnyApplicableModule(project);
  }
}
