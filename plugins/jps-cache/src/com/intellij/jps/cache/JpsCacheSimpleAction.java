package com.intellij.jps.cache;

import com.intellij.compiler.server.BuildManager;
import com.intellij.jps.cache.client.ArtifactoryJpsServerClient;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.loader.JpsCacheLoader;
import com.intellij.jps.cache.loader.JpsCompilationOutputLoader;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.stream.Stream;

public class JpsCacheSimpleAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCacheSimpleAction");
  private final JpsServerClient myClient = new ArtifactoryJpsServerClient();
  private final BuildManager myBuildManager = BuildManager.getInstance();

  @Override
  public void actionPerformed(AnActionEvent actionEvent) {
    Project project = actionEvent.getProject();
    if (project == null) return;
    Stream.of(new JpsCacheLoader(myBuildManager, myClient, project), new JpsCompilationOutputLoader(myClient,project))
      .forEach(loader -> ApplicationManager.getApplication().executeOnPooledThread(() -> loader.load()));
  }
}