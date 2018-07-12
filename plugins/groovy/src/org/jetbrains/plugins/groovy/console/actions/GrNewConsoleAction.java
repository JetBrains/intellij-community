// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console.actions;

import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;
import org.jetbrains.plugins.groovy.console.GroovyConsole;
import org.jetbrains.plugins.groovy.console.GroovyConsoleRootType;

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
    Project project = e.getProject();
    if (project == null) return null;

    Module module = e.getData(LangDataKeys.MODULE);
    if (!GroovyFacetUtil.isSuitableModule(module)) {
      module =  getAnyApplicableModule(project);
    }
    return module;
  }
}