package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class JpsCompilationOutputLoader implements JpsOutputLoader {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.loader.JpsCompilationOutputLoader");
  private final PersistentCachingModuleHashingService myHashingService;
  private static final int myStepsCount = 2;
  private final JpsServerClient myClient;
  private final Project myProject;
  private Map<File, String> myTmpFolderToModuleName;

  JpsCompilationOutputLoader(JpsServerClient client, Project project, PersistentCachingModuleHashingService hashingService) {
    myClient = client;
    myProject = project;
    myHashingService = hashingService;
  }

  @Override
  public LoaderStatus load(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager progressIndicatorManager) {
    myTmpFolderToModuleName = null;
    ProgressIndicator indicator = progressIndicatorManager.getProgressIndicator();
    CompilerProjectExtension projectExtension = CompilerProjectExtension.getInstance(myProject);
    if (projectExtension == null || projectExtension.getCompilerOutputUrl() == null) {
      LOG.warn("Compiler output setting not specified for the project ");
      return LoaderStatus.FAILED;
    }
    File compilerOutputDir = new File(VfsUtilCore.urlToPath(projectExtension.getCompilerOutputUrl()));

    File productionDir = new File(compilerOutputDir, CompilerModuleExtension.PRODUCTION);
    progressIndicatorManager.setText(this, "Calculating affected production modules");
    Map<String, String> affectedProductionModules = getAffectedModules(productionDir, myHashingService::getAffectedProduction,
                                                                       myHashingService::computeProductionHashesForProject);
    progressIndicatorManager.finished(this);
    indicator.checkCanceled();
    if (affectedProductionModules.size() > 0) {
      FileUtil.createDirectory(productionDir);
      Pair<Boolean, Map<File, String>> downloadResultsPair = myClient.downloadCompiledModules(myProject, progressIndicatorManager,
                                                                                              CompilerModuleExtension.PRODUCTION,
                                                                                              affectedProductionModules, productionDir);
      myTmpFolderToModuleName = downloadResultsPair.second;
      if (!downloadResultsPair.first) return LoaderStatus.FAILED;
    } else {
      // Move progress up to the half of segment size
      displaySkippedStepOnProgressBar(progressIndicatorManager);
    }

    File testDir = new File(compilerOutputDir, CompilerModuleExtension.TEST);
    progressIndicatorManager.setText(this, "Calculating affected test modules");
    Map<String, String> affectedTestModules = getAffectedModules(testDir, myHashingService::getAffectedTests,
                                                                 myHashingService::computeTestHashesForProject);
    progressIndicatorManager.finished(this);
    indicator.checkCanceled();
    if (affectedTestModules.size() > 0) {
      FileUtil.createDirectory(testDir);
      Pair<Boolean, Map<File, String>> downloadResultsPair = myClient.downloadCompiledModules(myProject, progressIndicatorManager,
                                                                                              CompilerModuleExtension.TEST,
                                                                                              affectedTestModules, testDir);
      myTmpFolderToModuleName.putAll(downloadResultsPair.second);
      if (!downloadResultsPair.first) return LoaderStatus.FAILED;
    } else {
      // Move progress up to the half of segment size
      displaySkippedStepOnProgressBar(progressIndicatorManager);
    }
    return LoaderStatus.COMPLETE;
  }

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

  private static Map<String, String> getAffectedModules(@NotNull File outDir, @NotNull Supplier<Map<String, String>> affectedModules,
                                                        @NotNull Supplier<Map<String, String>> allModules) {
    long start = System.currentTimeMillis();
    Map<String, String> allModulesMap = allModules.get();
    if (outDir.exists()) {
      File[] listFiles = outDir.listFiles();
      if (listFiles == null) return allModulesMap;
      // Create map for currently exists module compilation outputs
      Map<String, File> currentModulesFolderMap = Arrays.stream(listFiles).filter(File::isDirectory)
                                                                   .collect(Collectors.toMap(folder -> folder.getName(), Function.identity()));

      // Detect modules which compilation outputs were not found but should be
      Set<String> modulesWithRemovedOutDir = new HashSet<>(allModulesMap.keySet());
      modulesWithRemovedOutDir.removeAll(currentModulesFolderMap.keySet());

      // Delete compilation outputs for currently not existing modules
      Set<String> oldModulesOutDir = new HashSet<>(currentModulesFolderMap.keySet());
      oldModulesOutDir.removeAll(allModulesMap.keySet());
      oldModulesOutDir.stream().map(currentModulesFolderMap::get).forEach(FileUtil::delete);

      Map<String, String> affectedModulesMap = affectedModules.get();
      modulesWithRemovedOutDir.forEach(moduleName -> {
        affectedModulesMap.put(moduleName, allModulesMap.get(moduleName));
      });
      LOG.debug("Compilation output affected for the " + affectedModulesMap.size() + " modules. Computation took " + (System.currentTimeMillis() - start) + "ms");
      return affectedModulesMap;
    }
    LOG.warn("Compilation output doesn't exist, force to download " + allModulesMap.size() +" modules. Computation took " +  (System.currentTimeMillis() - start) + "ms");
    return allModulesMap;
  }

  private static void displaySkippedStepOnProgressBar(@NotNull SegmentedProgressIndicatorManager progressIndicatorManager) {
    progressIndicatorManager.setTasksCount(1);
    progressIndicatorManager.updateFraction(1.0 / myStepsCount);
  }
}