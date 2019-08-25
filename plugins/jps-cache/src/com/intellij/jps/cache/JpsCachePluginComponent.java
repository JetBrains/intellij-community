package com.intellij.jps.cache;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JpsCachePluginComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachePluginComponent");
  private final Project project;
  private final ProjectRootManager projectRootManager;
  private final ProjectFileIndex projectFileIndex;
  private PersistentCachingModuleHashingService moduleHashingService;
  private Map<Module, List<VirtualFile>> moduleToContentSourceRoots;

  public JpsCachePluginComponent(Project project) {
    this.project = project;
    this.projectRootManager = ProjectRootManager.getInstance(project);
    this.projectFileIndex = ProjectFileIndex.getInstance(project);
    File hashCacheFile = new File(getCacheFileName());
    try {
      this.moduleHashingService = new PersistentCachingModuleHashingService(hashCacheFile);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  @Override
  public void projectOpened() {
    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new VFSListener());

    VirtualFile[] sourcesRoots = projectRootManager.getContentSourceRoots();
    moduleToContentSourceRoots =
      Arrays.stream(sourcesRoots).collect(Collectors.groupingBy(sourceRoot -> projectFileIndex.getModuleForFile(sourceRoot)));
    for (Map.Entry<Module, List<VirtualFile>> entry : moduleToContentSourceRoots.entrySet()) {
      Module curModule = entry.getKey();
      String moduleName = curModule.getName();
      File[] sourceRoots = entry.getValue().stream().map(virtualFile -> new File(virtualFile.getPath())).toArray(File[]::new);
      try {
        moduleHashingService
          .hashContentRootsAndPersist(curModule, sourceRoots);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
  }

  private String getCacheFileName() {
    File pluginsDir = new File(PathManager.getPluginsPath());
    String projectPathHash = "";
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("MD5");
      messageDigest.update(project.getBasePath().getBytes());
      projectPathHash = new String(messageDigest.digest());
    }
    catch (NoSuchAlgorithmException e) {
      LOG.warn(e);
    }
    return FileUtil.join(pluginsDir.getPath(), project.getName() + "_" + projectPathHash, "hashCaches");
  }

  private class VFSListener implements BulkFileListener {
    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      Module[] affectedModules =
        events.stream().map(vFileEvent -> vFileEvent.getFile()).map(projectFileIndex::getModuleForFile).toArray(Module[]::new);
      for (Module affectedModule : affectedModules) {
        List<VirtualFile> virtualContentSourceRoots = moduleToContentSourceRoots.get(affectedModule);
        if (virtualContentSourceRoots == null) {
          continue;
        }
        File[] contentSourceRoots =
          virtualContentSourceRoots.stream().map(virtualFile -> new File(virtualFile.getPath())).toArray(File[]::new);
        try {
          moduleHashingService.hashContentRootsAndPersist(affectedModule, contentSourceRoots);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }
}
