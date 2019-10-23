package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class JpsCompilationOutputLoader implements JpsOutputLoader {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.loader.JpsCompilationOutputLoader");
  protected final JpsServerClient myClient;
  protected final Project myProject;
  protected Map<File, String> myTmpFolderToModuleName;

  JpsCompilationOutputLoader(JpsServerClient client, Project project) {
    myClient = client;
    myProject = project;
  }

  @Override
  public LoaderStatus load(@NotNull JpsLoaderContext context) {
    myTmpFolderToModuleName = null;
    ProgressIndicator indicator = context.getIndicatorManager().getProgressIndicator();
    CompilerProjectExtension projectExtension = CompilerProjectExtension.getInstance(myProject);
    if (projectExtension == null || projectExtension.getCompilerOutputUrl() == null) {
      LOG.warn("Compiler output setting not specified for the project ");
      return LoaderStatus.FAILED;
    }
    File compilerOutputDir = new File(VfsUtilCore.urlToPath(projectExtension.getCompilerOutputUrl()));
    return load(compilerOutputDir, context);
  }

  abstract LoaderStatus load(@NotNull File compilerOutputDir, @NotNull JpsLoaderContext context);

  @Override
  public void rollback() {
    if (myTmpFolderToModuleName == null) return;
    myTmpFolderToModuleName.forEach((tmpFolder, __) -> {if (tmpFolder.isDirectory() && tmpFolder.exists()) FileUtil.delete(tmpFolder);});
    LOG.debug("JPS cache loader rolled back");
  }

  @Override
  public void apply() {
    if (myTmpFolderToModuleName == null) {
      LOG.debug("Nothing to apply, download results are empty");
      return;
    }

    myTmpFolderToModuleName.forEach((tmpModuleFolder, moduleName) -> {
      File currentModuleBuildDir = new File(tmpModuleFolder.getParentFile(), moduleName);
      FileUtil.delete(currentModuleBuildDir);
      try {
        FileUtil.rename(tmpModuleFolder, currentModuleBuildDir);
        LOG.debug("Module: " + moduleName + " was replaced successfully");
      }
      catch (IOException e) {
        LOG.warn("Couldn't replace compilation output for module: " + moduleName, e);
      }
    });
  }

  protected static Map<String, String> getAffectedModules(@NotNull File outDir, @Nullable Map<String, String> currentModulesState,
                                                        @NotNull Map<String, String> commitModulesState) {
    long start = System.currentTimeMillis();

    if (!outDir.exists() || currentModulesState == null) {
      LOG.warn("Compilation output doesn't exist or doesn't contain metadata, force to download " + commitModulesState.size() +" modules. " +
               "Computation took " +  (System.currentTimeMillis() - start) + "ms");
      return commitModulesState;
    }

    File[] listFiles = outDir.listFiles();
    if (listFiles == null) return commitModulesState;

    // Finding changed modules
    Map<String, String> affectedModulesMap = new HashMap<>();
    commitModulesState.forEach((moduleName, commitModuleHash) -> {
      String currentModuleHash = currentModulesState.get(moduleName);
      if (currentModuleHash == null || !currentModuleHash.equals(commitModuleHash)) {
        affectedModulesMap.put(moduleName, commitModuleHash);
      }
    });

    // Create map for currently exists module compilation outputs
    Map<String, File> currentModulesFolderMap = Arrays.stream(listFiles).filter(File::isDirectory)
      .collect(Collectors.toMap(folder -> folder.getName(), Function.identity()));

    // Detect modules which compilation outputs were not found but should be
    Set<String> modulesWithRemovedOutDir = new HashSet<>(commitModulesState.keySet());
    modulesWithRemovedOutDir.removeAll(currentModulesFolderMap.keySet());
    modulesWithRemovedOutDir.forEach(moduleName -> {
      affectedModulesMap.put(moduleName, commitModulesState.get(moduleName));
    });

    // Delete compilation outputs for currently not existing modules
    Set<String> oldModulesOutDir = new HashSet<>(currentModulesFolderMap.keySet());
    oldModulesOutDir.removeAll(commitModulesState.keySet());
    oldModulesOutDir.stream().map(currentModulesFolderMap::get).forEach(FileUtil::delete);

    LOG.debug("Compilation output affected for the " + affectedModulesMap.size() + " modules. Computation took " + (System.currentTimeMillis() - start) + "ms");
    return affectedModulesMap;
  }

  protected static void displaySkippedStepOnProgressBar(@NotNull SegmentedProgressIndicatorManager progressIndicatorManager) {
    progressIndicatorManager.setTasksCount(1);
    progressIndicatorManager.updateFraction(1.0);
  }
}