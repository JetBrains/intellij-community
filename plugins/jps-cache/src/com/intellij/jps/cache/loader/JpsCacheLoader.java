package com.intellij.jps.cache.loader;

import com.intellij.compiler.server.BuildManager;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.model.JpsLoaderContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

class JpsCacheLoader implements JpsOutputLoader {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.loader.JpsCacheLoader");
  private static final String TIMESTAMPS_FOLDER_NAME = "timestamps";
  private static final String FS_STATE_FILE = "fs_state.dat";
  private final BuildManager myBuildManager;
  private final JpsServerClient myClient;
  private final Project myProject;
  private File myTmpCacheFolder;

  JpsCacheLoader(JpsServerClient client, @NotNull Project project) {
    myBuildManager = BuildManager.getInstance();
    myClient = client;
    myProject = project;
  }

  @Override
  public LoaderStatus load(@NotNull JpsLoaderContext context) {
    LOG.info("Loading JPS caches for commit: " + context.getCommitId());
    myTmpCacheFolder = null;

    File targetDir = myBuildManager.getBuildSystemDirectory().toFile();
    long start = System.currentTimeMillis();
    Pair<Boolean, File> downloadResultPair = myClient.downloadCacheById(context.getIndicatorManager(), context.getCommitId(), targetDir);
    LOG.info("Download of jps caches took: " + (System.currentTimeMillis() - start));
    myTmpCacheFolder = downloadResultPair.second;
    if (!downloadResultPair.first) return LoaderStatus.FAILED;
    return LoaderStatus.COMPLETE;
  }

  @Override
  public void rollback() {
    if (myTmpCacheFolder != null && myTmpCacheFolder.exists()) {
      FileUtil.delete(myTmpCacheFolder);
      LOG.debug("JPS cache loader rolled back");
    }
  }

  @Override
  public void apply() {
    if (myTmpCacheFolder == null) {
      LOG.warn("Nothing to apply, download results are empty");
      return;
    }
    File currentDirForBuildCache = myBuildManager.getProjectSystemDirectory(myProject);
    if (currentDirForBuildCache != null) {
      File newTimestampFolder = new File(myTmpCacheFolder, TIMESTAMPS_FOLDER_NAME);
      if (newTimestampFolder.exists()) FileUtil.delete(newTimestampFolder);

      // Copy timestamp old folder to new cache dir
      File timestamps = new File(currentDirForBuildCache, TIMESTAMPS_FOLDER_NAME);
      if (timestamps.exists()) {
        try {
          newTimestampFolder.mkdirs();
          FileUtil.copyDir(timestamps, newTimestampFolder);
        }
        catch (IOException e) {
          LOG.warn("Couldn't copy timestamps from old JPS cache", e);
        }
      }

      // Create new empty fsStateFile
      File fsStateFile = new File(myTmpCacheFolder, FS_STATE_FILE);
      fsStateFile.delete();
      try {
        fsStateFile.createNewFile();
      }
      catch (IOException e) {
        LOG.warn("Couldn't create new empty FsState file", e);
      }

      // Remove old cache dir
      FileUtil.delete(currentDirForBuildCache);
      myTmpCacheFolder.renameTo(currentDirForBuildCache);
      LOG.debug("JPS cache downloads finished");
    }
  }
}