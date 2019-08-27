package com.intellij.jps.cache;

import com.intellij.jps.cache.client.ArtifactoryJpsCacheServerClient;
import com.intellij.jps.cache.client.JpsCacheServerClient;
import com.intellij.jps.cache.hashing.ModuleHashingService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.io.Compressor;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

public class JpsBinaryDataSyncAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsBinaryDataSyncAction");
  private final JpsCacheServerClient myCacheServerClient = new ArtifactoryJpsCacheServerClient();
  public static final String PRODUCTION = "production";
  public static final String TEST = "test";

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
        String sourceRootsHash = calculateProductionSourceRootsHash(ModuleRootManager.getInstance(module));
        File zipFile = new File(productionDir, sourceRootsHash);
        zipBinaryData(zipFile, productionModule);
        myCacheServerClient.uploadBinaryData(zipFile, module.getName(), PRODUCTION);
        FileUtil.delete(zipFile);
      }
      for (File testModule : testDir.listFiles()) {
        Module module = moduleManager.findModuleByName(testModule.getName());
        if (module == null) {
          LOG.warn("Couldn't find module by name " + testModule.getName() + " will be skipped from sync process");
          continue;
        }
        String sourceRootsHash = calculateTestSourceRootsHash(ModuleRootManager.getInstance(module));
        File zipFile = new File(testDir, sourceRootsHash);
        zipBinaryData(zipFile, testModule);
        myCacheServerClient.uploadBinaryData(zipFile, module.getName(), TEST);
        FileUtil.delete(zipFile);
      }
      long finish = System.currentTimeMillis() - start;
      LOG.warn("Sync of binary data took: " + finish + "ms");
    });
  }

  private static void zipBinaryData(File zipFile, File dir) {
    try (Compressor zip = new Compressor.Zip(zipFile)) {
      zip.addDirectory(dir);
    }
    catch (IOException e) {
      LOG.warn("Couldn't compress binary data: " + dir, e);
    }
  }

  private static String calculateProductionSourceRootsHash(ModuleRootManager moduleRootManager) {
    File[] sourceRoots = Stream.concat(moduleRootManager.getSourceRoots(JavaSourceRootType.SOURCE).stream(),
                                       moduleRootManager.getSourceRoots(JavaResourceRootType.RESOURCE).stream())
      .map(vf -> new File(vf.getPath())).toArray(File[]::new);
    byte[] hash = ModuleHashingService.hashDirectories(sourceRoots);
    return DatatypeConverter.printHexBinary(hash).toLowerCase();
  }

  private static String calculateTestSourceRootsHash(ModuleRootManager moduleRootManager) {
    File[] sourceRoots = Stream.concat(moduleRootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE).stream(),
                                       moduleRootManager.getSourceRoots(JavaResourceRootType.TEST_RESOURCE).stream())
      .map(vf -> new File(vf.getPath())).toArray(File[]::new);
    byte[] hash = ModuleHashingService.hashDirectories(sourceRoots);
    return DatatypeConverter.printHexBinary(hash).toLowerCase();
  }
}