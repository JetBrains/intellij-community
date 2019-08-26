package com.intellij.jps.cache;

import com.intellij.compiler.server.BuildManager;
import com.intellij.jps.cache.client.ArtifactoryJpsCacheServerClient;
import com.intellij.jps.cache.client.JpsCacheServerClient;
import com.intellij.jps.cache.git.GitRepositoryUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

public class JpsCacheSimpleAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCacheSimpleAction");

  private JpsCacheServerClient myCacheServerClient = new ArtifactoryJpsCacheServerClient();
  private BuildManager myBuildManager = BuildManager.getInstance();
  private Project myProject;

  @Override
  public void actionPerformed(AnActionEvent actionEvent) {
    myProject = actionEvent.getProject();
    if (myProject == null) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Set<String> cacheKeys = myCacheServerClient.getAllCacheKeys();
      System.out.println(cacheKeys);
      GitRepositoryUtil.getLatestCommitHashes(myProject, 20).stream().filter(cacheKeys::contains)
        .findFirst().ifPresent(cacheId -> {
          System.out.println(cacheId);
          File targetDir = myBuildManager.getBuildSystemDirectory().toFile();
          myCacheServerClient.downloadCacheByIdAsynchronously(myProject, cacheId, targetDir, this::renameTmpCacheFolder);
        });
    });
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Set<String> binaryKeys = myCacheServerClient.getAllBinaryKeys();
      System.out.println(binaryKeys);
      CompilerProjectExtension projectExtension = CompilerProjectExtension.getInstance(myProject);
      if (projectExtension == null || projectExtension.getCompilerOutputUrl() == null) {
        LOG.warn("Compiler output setting not specified for the project ");
        return;
      }
      File targetDir = new File(VfsUtilCore.urlToPath(projectExtension.getCompilerOutputUrl()));
      Arrays.stream(ModuleManager.getInstance(myProject).getModules())
                                        .map(Module::getName).forEach(moduleName -> {
          myCacheServerClient.downloadCompiledModuleByNameAndHash(myProject, moduleName, "SomeHash", targetDir);
        });
    });
  }

  private void renameTmpCacheFolder(@Nullable File tmpCacheFolder) { //TODO:: Fix myProject may be null
    if (tmpCacheFolder == null) {
      //TODO:: Think about rollback
      LOG.warn("Couldn't download JPS portable caches");
      return;
    }
    File currentDirForBuildCache = BuildManager.getInstance().getProjectSystemDirectory(myProject);
    if (currentDirForBuildCache != null) {
      FileUtil.delete(currentDirForBuildCache);
      try {
        FileUtil.rename(tmpCacheFolder, currentDirForBuildCache);
      }
      catch (IOException e) {
        LOG.warn("Couldn't replace existing caches by downloaded portable", e);
      }
    }
  }
}