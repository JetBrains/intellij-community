package com.intellij.jps.cache;

import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

public class JpsCachesProjectStateListener implements StartupActivity, GitRepositoryChangeListener {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachesProjectStateListener");

  @Override
  public void runActivity(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    LOG.debug("Catch repository git event");
    String currentRevision = repository.getInfo().getCurrentRevision();
    if (currentRevision == null) return;
    JpsOutputLoaderManager.getInstance(repository.getProject()).notifyAboutNearestCache();
  }
}
