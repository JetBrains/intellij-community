package com.intellij.jps.cache;

import com.intellij.jps.cache.client.ArtifactoryJpsServerClient;
import com.intellij.jps.cache.hashing.ModuleHashingService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.io.Compressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Stream;

public class JpsBinaryDataSyncAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsBinaryDataSyncAction");

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

      File productionDir = new File(compilerOutputDir, CompilerModuleExtension.PRODUCTION);
      if (!productionDir.exists()) {
        LOG.warn("Production folder skipped from sync, it doesn't exist");
      } else {
        uploadSourceRootBinaryData(productionDir, moduleManager, CompilerModuleExtension.PRODUCTION, JpsBinaryDataSyncAction::calculateProductionSourceRootsHash);
      }

      File testDir = new File(compilerOutputDir, CompilerModuleExtension.TEST);
      if (!testDir.exists()) {
        LOG.warn("Test folder skipped from sync, it doesn't exist");
      } else {
        uploadSourceRootBinaryData(testDir, moduleManager, CompilerModuleExtension.TEST, JpsBinaryDataSyncAction::calculateTestSourceRootsHash);
      }

      long finish = System.currentTimeMillis() - start;
      LOG.warn("Sync of binary data took: " + finish + "ms");
    });
  }

  private static void uploadSourceRootBinaryData(@NotNull File sourceRoot, @NotNull ModuleManager moduleManager, @NotNull String prefix,
                                          @NotNull Function<ModuleRootManager, String> sourceRootHashFunction) {
    File[] moduleFolders = sourceRoot.listFiles();
    assert moduleFolders != null;
    for (File moduleFolder : moduleFolders) {
      Module module = moduleManager.findModuleByName(moduleFolder.getName());
      if (module == null) {
        LOG.warn("Couldn't find module by name " + moduleFolder.getName() + " will be skipped from sync process");
        continue;
      }

      String sourceRootsHash = sourceRootHashFunction.apply(ModuleRootManager.getInstance(module));
      if (sourceRootsHash.isEmpty()) continue;
      File zipFile = new File(sourceRoot, sourceRootsHash);
      zipBinaryData(zipFile, moduleFolder);
      ArtifactoryJpsServerClient.INSTANCE.uploadBinaryData(zipFile, module.getName(), prefix);
      FileUtil.delete(zipFile);
    }
  }

  public static String calculateProductionSourceRootsHash(ModuleRootManager moduleRootManager) {
    File[] sourceRoots = Stream.concat(moduleRootManager.getSourceRoots(JavaSourceRootType.SOURCE).stream(),
                                       moduleRootManager.getSourceRoots(JavaResourceRootType.RESOURCE).stream())
      .map(vf -> new File(vf.getPath())).toArray(File[]::new);
    return ModuleHashingService.hashDirectories(sourceRoots).map(hash -> DatatypeConverter.printHexBinary(hash).toLowerCase()).orElse("");
  }

  public static String calculateTestSourceRootsHash(ModuleRootManager moduleRootManager) {
    File[] sourceRoots = Stream.concat(moduleRootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE).stream(),
                                       moduleRootManager.getSourceRoots(JavaResourceRootType.TEST_RESOURCE).stream())
      .map(vf -> new File(vf.getPath())).toArray(File[]::new);
    return ModuleHashingService.hashDirectories(sourceRoots).map(hash -> DatatypeConverter.printHexBinary(hash).toLowerCase()).orElse("");
  }

  private static void zipBinaryData(File zipFile, File dir) {
    try (Compressor zip = new Compressor.Zip(zipFile)) {
      zip.addDirectory(dir);
    }
    catch (IOException e) {
      LOG.warn("Couldn't compress binary data: " + dir, e);
    }
  }
}