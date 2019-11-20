package com.intellij.jps.cache;

import com.intellij.compiler.server.BuildManager;
import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

public class JpsCachesProjectStateListener implements StartupActivity.Background, GitRepositoryChangeListener, RegistryValueListener {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachesProjectStateListener");
  private static final String PORTABLE_CACHES_KEY = "compiler.build.portable.caches";
  private String previousCommitId = "";

  @Override
  public void runActivity(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);
    Registry.get(PORTABLE_CACHES_KEY).addListener(this, ApplicationManager.getApplication());
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    LOG.debug("Catch repository git event");
    String currentRevision = repository.getInfo().getCurrentRevision();
    if (currentRevision == null || previousCommitId.equals(currentRevision)) return;
    previousCommitId = currentRevision;
    JpsOutputLoaderManager.getInstance(repository.getProject()).notifyAboutNearestCache();
  }

  @Override
  public void beforeValueChanged(@NotNull RegistryValue value) { }

  @Override
  public void afterValueChanged(@NotNull RegistryValue value) {
    BuildManager.getInstance().clearState();
  }
}
