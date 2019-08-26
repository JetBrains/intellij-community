package com.intellij.jps.cache;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.io.Compressor;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;

public class JpsBinaryDataSyncAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsBinaryDataSyncAction");
  private static final String PRODUCTION = "production";
  private static final String TEST = "test";

  @Override
  public void actionPerformed(AnActionEvent actionEvent) {
    Project project = actionEvent.getProject();
    if (project == null) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      long start = System.currentTimeMillis();
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      CompilerProjectExtension projectExtension = CompilerProjectExtension.getInstance(project);
      if (projectExtension == null || projectExtension.getCompilerOutputUrl() == null) {
        LOG.warn("Compiler output setting not specified for the project ");
        return;
      }
      File compilerOutputDir = new File(VfsUtilCore.urlToPath(projectExtension.getCompilerOutputUrl()));
      File productionDir = new File(compilerOutputDir, PRODUCTION);
      File testDir = new File(compilerOutputDir, TEST);
      for (File productionModule : productionDir.listFiles()) {
        Module module = moduleManager.findModuleByName(productionModule.getName());
        if (module == null) {
          LOG.warn("Couldn't find module by name " + productionModule.getName() + " will be skipped from sync process");
          continue;
        }
        calculateProductionSourceRootsHash(ModuleRootManager.getInstance(module));
        zipBinaryData(new File(productionDir, productionModule.getName()+".zip"), productionModule);
      }
      for (File testModule : testDir.listFiles()) {

      }
      long finish = System.currentTimeMillis() - start;
      LOG.debug("Sync of binary data took: " +  finish + "ms");
    });
  }

  private static void zipBinaryData(File zipFile, File dir) {
    try (Compressor zip = new Compressor.Zip(zipFile)) {
      zip.addDirectory(dir);
    } catch (IOException e) {
      LOG.warn("Couldn't compress binary data: " + dir, e);
    }
  }

  private static void calculateProductionSourceRootsHash(ModuleRootManager moduleRootManager) {
    System.out.println(moduleRootManager.getSourceRoots(JavaSourceRootType.SOURCE));
    System.out.println(moduleRootManager.getSourceRoots(JavaResourceRootType.RESOURCE));
  }

  private static void calculateTestSourceRootsHash(ModuleRootManager moduleRootManager) {
    System.out.println(moduleRootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE));
    System.out.println(moduleRootManager.getSourceRoots(JavaResourceRootType.TEST_RESOURCE));
  }
}