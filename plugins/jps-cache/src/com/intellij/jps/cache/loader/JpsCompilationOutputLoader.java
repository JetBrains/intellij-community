package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.JpsCachePluginComponent;
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
import java.util.Map;

public class JpsCompilationOutputLoader implements JpsOutputLoader{
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.loader.JpsCompilationOutputLoader");
  private final JpsServerClient myClient;
  private final Project myProject;

  public JpsCompilationOutputLoader(JpsServerClient client, Project project) {
    myClient = client;
    myProject = project;
  }

  @Override
  public void load() {
    //Set<String> binaryKeys = myClient.getAllBinaryKeys();
    //System.out.println(binaryKeys);
    testScenario();
    //mainScenario();
  }

  private void mainScenario() {
    CompilerProjectExtension projectExtension = CompilerProjectExtension.getInstance(myProject);
    if (projectExtension == null || projectExtension.getCompilerOutputUrl() == null) {
      LOG.warn("Compiler output setting not specified for the project ");
      return;
    }
    File compilerOutputDir = new File(VfsUtilCore.urlToPath(projectExtension.getCompilerOutputUrl()));
    JpsCachePluginComponent pluginComponent = myProject.getComponent(JpsCachePluginComponent.class);
    PersistentCachingModuleHashingService moduleHashingService = pluginComponent.getModuleHashingService();

    File productionDir = new File(compilerOutputDir, CompilerModuleExtension.PRODUCTION);
    downloadAffectedModuleBinaryData(moduleHashingService.getAffectedProduction(), productionDir, CompilerModuleExtension.PRODUCTION);

    File testDir = new File(compilerOutputDir, CompilerModuleExtension.TEST);
    downloadAffectedModuleBinaryData(moduleHashingService.getAffectedTests(), testDir, CompilerModuleExtension.TEST);
  }

  private void testScenario() {
    CompilerProjectExtension projectExtension = CompilerProjectExtension.getInstance(myProject);
    if (projectExtension == null || projectExtension.getCompilerOutputUrl() == null) {
      LOG.warn("Compiler output setting not specified for the project ");
      return;
    }
    File compilerOutputDir = new File(VfsUtilCore.urlToPath(projectExtension.getCompilerOutputUrl()));
    JpsCachePluginComponent pluginComponent = myProject.getComponent(JpsCachePluginComponent.class);
    PersistentCachingModuleHashingService moduleHashingService = pluginComponent.getModuleHashingService();

    File productionDir = new File(compilerOutputDir, CompilerModuleExtension.PRODUCTION);
    if (!productionDir.exists()) {
      productionDir.mkdirs();
    }
    downloadAffectedModuleBinaryData(moduleHashingService.computeProductionHashesForProject(), productionDir, CompilerModuleExtension.PRODUCTION);

    File testDir = new File(compilerOutputDir, CompilerModuleExtension.TEST);
    if (!testDir.exists()) {
      testDir.mkdirs();
    }
    downloadAffectedModuleBinaryData(moduleHashingService.computeTestHashesForProject(), testDir, CompilerModuleExtension.TEST);
  }

  @Override
  public void rollback() {

  }

  @Override
  public void apply() {

  }

  private void downloadAffectedModuleBinaryData(@NotNull Map<String, byte[]> affectedModules, @NotNull File targetDir, @NotNull String prefix) {
    int[] i = new int[1];
    affectedModules.forEach((moduleName, moduleHash) -> {
      if (i[0] % 10  == 0) {
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