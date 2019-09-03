package com.intellij.jps.cache;

import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JpsCachesProjectStateListener implements StartupActivity, ProjectManagerListener, GitRepositoryChangeListener {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCacheStartupActivity");
  private PersistentCachingModuleHashingService myModuleHashingService;

  @Override
  public void runActivity(@NotNull Project project) {
    try {
      myModuleHashingService = new PersistentCachingModuleHashingService(new File(JpsCachesUtils.getPluginStorageDir(project)), project);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);
    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, this);
  }

  @Override
  public void projectClosed(@NotNull Project project) {
    myModuleHashingService.close();
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    String currentRevision = repository.getInfo().getCurrentRevision();
    if (currentRevision == null) return;
    JpsOutputLoaderManager.getInstance(repository.getProject()).load(currentRevision);
  }

  public PersistentCachingModuleHashingService getModuleHashingService() {
    return myModuleHashingService;
  }
}
