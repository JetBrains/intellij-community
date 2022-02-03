package com.intellij.jps.cache.action;

import com.intellij.jps.cache.client.JpsServerAuthUtil;
import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class JpsForceUpdateCachesAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent actionEvent) {
    Project project = actionEvent.getProject();
    if (project == null) return;
    JpsOutputLoaderManager outputLoaderManager = JpsOutputLoaderManager.getInstance(project);
    JpsServerAuthUtil.checkAuthenticatedInBackgroundThread(outputLoaderManager, project, () -> outputLoaderManager.load(true, true));
  }
}
