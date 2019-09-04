package com.intellij.jps.cache;

import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

public class JpsCachesProjectStateListener implements StartupActivity, ProjectManagerListener, GitRepositoryChangeListener {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachesProjectStateListener");

  @Override
  public void runActivity(@NotNull Project project) {
    JpsOutputLoaderManager.getInstance(project).initialize(project);
    project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);
    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, this);
  }

  @Override
  public void projectClosed(@NotNull Project project) {
    JpsOutputLoaderManager.getInstance(project).close();
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    LOG.debug("Catch repository git event");
    String currentRevision = repository.getInfo().getCurrentRevision();
    if (currentRevision == null) return;
    JpsOutputLoaderManager.getInstance(repository.getProject()).load(currentRevision);
  }
}
