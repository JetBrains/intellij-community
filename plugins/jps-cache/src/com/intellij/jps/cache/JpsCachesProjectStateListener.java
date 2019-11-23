package com.intellij.jps.cache;

import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.diagnostic.Logger;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

public class JpsCachesProjectStateListener implements GitRepositoryChangeListener {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachesProjectStateListener");
  private String previousCommitId = "";

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    LOG.debug("Catch repository git event");
    String currentRevision = repository.getInfo().getCurrentRevision();
    if (currentRevision == null || previousCommitId.equals(currentRevision)) return;
    previousCommitId = currentRevision;
    JpsOutputLoaderManager.getInstance(repository.getProject()).notifyAboutNearestCache();
  }
}
