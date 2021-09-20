package com.intellij.jps.cache.action;

import com.intellij.compiler.server.BuildManager;
import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class JpsUpdateCachesAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent actionEvent) {
    Project project = actionEvent.getProject();
    if (project == null) return;
    Path directory = BuildManager.getInstance().getBuildSystemDirectory(project);
    String dir = JpsOutputLoaderManager.getBuildOutDir(project);
    JpsOutputLoaderManager outputLoaderManager = JpsOutputLoaderManager.getInstance(project);
    //OneTwo.checkAuthenticatedInBackgroundThread(outputLoaderManager, project, () -> outputLoaderManager.load(false, true));
  }
}