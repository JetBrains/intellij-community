package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.JpsCachesProjectStateListener;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.DatatypeConverter;
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
  private final JpsServerClient myClient;
  private final Project myProject;

  JpsCompilationOutputLoader(JpsServerClient client, Project project, PersistentCachingModuleHashingService hashingService) {
    myClient = client;
    myProject = project;
    myHashingService = hashingService;
  }

  @Override
  public void load(@NotNull String commitId) {
    CompilerProjectExtension projectExtension = CompilerProjectExtension.getInstance(myProject);
    if (projectExtension == null || projectExtension.getCompilerOutputUrl() == null) {
      LOG.warn("Compiler output setting not specified for the project ");
      return;
    }
    File compilerOutputDir = new File(VfsUtilCore.urlToPath(projectExtension.getCompilerOutputUrl()));
    File productionDir = new File(compilerOutputDir, CompilerModuleExtension.PRODUCTION);
    Map<String, byte[]> affectedProductionModules = getAffectedModules(productionDir, myHashingService::getAffectedProduction,
                                                                       myHashingService::computeProductionHashesForProject);
    FileUtil.createDirectory(productionDir);
    downloadAffectedModuleBinaryData(affectedProductionModules, productionDir, CompilerModuleExtension.PRODUCTION);

    File testDir = new File(compilerOutputDir, CompilerModuleExtension.TEST);
    Map<String, byte[]> affectedTestModules = getAffectedModules(testDir, myHashingService::getAffectedTests,
                                                                 myHashingService::computeTestHashesForProject);
    FileUtil.createDirectory(testDir);
    downloadAffectedModuleBinaryData(affectedTestModules, testDir, CompilerModuleExtension.TEST);
  }

  @Override
  public void rollback() {

  }

  @Override
  public void apply() {

  }

  private static Map<String, byte[]> getAffectedModules(@NotNull File outDir, @NotNull Supplier<Map<String, byte[]>> affectedModules,
                                                        @NotNull Supplier<Map<String, byte[]>> allModules) {
    long start = System.currentTimeMillis();
    Map<String, byte[]> allModulesMap = allModules.get();
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

      Map<String, byte[]> affectedModulesMap = affectedModules.get();
      modulesWithRemovedOutDir.forEach(moduleName -> {
        affectedModulesMap.put(moduleName, allModulesMap.get(moduleName));
      });
      LOG.warn("Compilation output affected for the following modules: " + affectedModulesMap.keySet() + " " + (System.currentTimeMillis() - start));
      return affectedModulesMap;
    }
    LOG.warn("Compilation output doesn't exist, force to download all modules compilation " +  (System.currentTimeMillis() - start));
    return allModulesMap;
  }

  private void downloadAffectedModuleBinaryData(@NotNull Map<String, byte[]> affectedModules, @NotNull File targetDir, @NotNull String prefix) {
    int[] i = new int[1];
    affectedModules.forEach((moduleName, moduleHash) -> {
      if (i[0] % 10 == 0) {
        try {
          Thread.sleep(1000);
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      String stringHash = DatatypeConverter.printHexBinary(moduleHash).toLowerCase();
      myClient.downloadCompiledModuleByNameAndHash(myProject, moduleName, prefix, stringHash, targetDir, JpsCompilationOutputLoader::renameTmpModuleFolder);
      i[0]++;
    });
  }

  private static void renameTmpModuleFolder(@Nullable File tmpModuleFolder, String moduleName) {
    if (tmpModuleFolder == null) {
      //TODO:: Think about rollback
      LOG.warn("Couldn't download JPS portable caches");
      return;
    }
    File currentModuleBuildDir = new File(tmpModuleFolder.getParentFile(), moduleName);
    FileUtil.delete(currentModuleBuildDir);
    try {
      FileUtil.rename(tmpModuleFolder, currentModuleBuildDir);
      LOG.warn("Done for module: " + moduleName);
    }
    catch (IOException e) {
      LOG.warn("Couldn't replace existing caches by downloaded portable", e);
    }
  }
}