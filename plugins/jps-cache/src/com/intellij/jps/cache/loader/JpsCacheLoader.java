package com.intellij.jps.cache.loader;

import com.intellij.compiler.server.BuildManager;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.git.GitRepositoryUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class JpsCacheLoader implements JpsOutputLoader{
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.loader.JpsCacheLoader");
  private static final int COMMITS_COUNT = 20;
  private static final String TIMESTAMPS_FOLDER_NAME = "timestamps";
  private final BuildManager myBuildManager;
  private final JpsServerClient myClient;
  private final Project myProject;

  public JpsCacheLoader(BuildManager buildManager, JpsServerClient client, Project project) {
    myBuildManager = buildManager;
    myClient = client;
    myProject = project;
  }

  @Override
  public void load() {
    Set<String> cacheKeys = myClient.getAllCacheKeys();
    GitRepositoryUtil.getLatestCommitHashes(myProject, COMMITS_COUNT).stream().filter(cacheKeys::contains)
      .findFirst().ifPresent(cacheId -> {
      LOG.debug("Loading JPS caches for commit: " + cacheId);
      File targetDir = myBuildManager.getBuildSystemDirectory().toFile();
      myClient.downloadCacheByIdAsynchronously(myProject, cacheId, targetDir, this::renameTmpCacheFolder);
    });
  }

  @Override
  public void rollback() {

  }

  @Override
  public void apply() {

  }

  private void renameTmpCacheFolder(@Nullable File tmpCacheFolder) { //TODO:: Fix myProject may be null
    if (tmpCacheFolder == null) {
      //TODO:: Think about rollback
      LOG.warn("Couldn't download JPS portable caches");
      return;
    }
    File currentDirForBuildCache = BuildManager.getInstance().getProjectSystemDirectory(myProject);
    if (currentDirForBuildCache != null) {
      File timestamps = new File(currentDirForBuildCache, TIMESTAMPS_FOLDER_NAME);
      if (timestamps.exists()) {
        try {
          File newTimestampFolder = new File(tmpCacheFolder, TIMESTAMPS_FOLDER_NAME);
          newTimestampFolder.mkdirs();
          FileUtil.copyDir(timestamps, newTimestampFolder);
        }
        catch (IOException e) {
          LOG.warn("Couldn't copy timestamps from old JPS caches", e);
        }
      }
      FileUtil.delete(currentDirForBuildCache);
      try {
        FileUtil.rename(tmpCacheFolder, currentDirForBuildCache);
        LOG.warn("Cache downloads DONE");
      }
      catch (IOException e) {
        LOG.warn("Couldn't replace existing caches by downloaded portable", e);
      }
    }
  }
}