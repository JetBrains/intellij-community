package com.intellij.jps.cache;

import com.intellij.jps.cache.hashing.JpsCacheUtils;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JpsCacheStartupActivity implements StartupActivity, GitRepositoryChangeListener {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCacheStartupActivity");
  private PersistentCachingModuleHashingService myModuleHashingService;

  @Override
  public void runActivity(@NotNull Project project) {
    try {
      this.myModuleHashingService = new PersistentCachingModuleHashingService(new File(JpsCacheUtils.getPluginStorageDir(project)), project);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);
  }

  public PersistentCachingModuleHashingService getModuleHashingService() {
    return myModuleHashingService;
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    String currentRevision = repository.getInfo().getCurrentRevision();
    if (currentRevision == null) return;
    JpsOutputLoaderManager.getInstance(repository.getProject()).load(currentRevision);
  }
}
