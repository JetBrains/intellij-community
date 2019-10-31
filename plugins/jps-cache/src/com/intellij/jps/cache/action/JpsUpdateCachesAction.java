package com.intellij.jps.cache.action;

import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;

public class JpsUpdateCachesAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent actionEvent) {
    Project project = actionEvent.getProject();
    if (project == null) return;
    JpsOutputLoaderManager.getInstance(project).load(true);
  }
}