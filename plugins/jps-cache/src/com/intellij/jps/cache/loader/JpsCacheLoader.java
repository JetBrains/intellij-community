package com.intellij.jps.cache.loader;

import com.intellij.compiler.server.BuildManager;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

class JpsCacheLoader implements JpsOutputLoader {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.loader.JpsCacheLoader");
  private static final int COMMITS_COUNT = 20;
  private static final String TIMESTAMPS_FOLDER_NAME = "timestamps";
  private final BuildManager myBuildManager;
  private final JpsServerClient myClient;
  private final Project myProject;

  JpsCacheLoader(JpsServerClient client, Project project) {
    myBuildManager = BuildManager.getInstance();
    myClient = client;
    myProject = project;
  }

  @Override
  public void load(@NotNull String commitId) {
    LOG.debug("Loading JPS caches for commit: " + commitId);
    File targetDir = myBuildManager.getBuildSystemDirectory().toFile();
    myClient.downloadCacheByIdAsynchronously(myProject, commitId, targetDir, this::renameTmpCacheFolder);
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
    File currentDirForBuildCache = myBuildManager.getProjectSystemDirectory(myProject);
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