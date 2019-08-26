package com.intellij.jps.cache.hashing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.io.BooleanDataDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PersistentCachingModuleHashingService extends ModuleHashingService {
  private static final Logger LOG = Logger.getInstance("com.jetbrains.cachepuller.PersistentCachingModuleHashingService");
  private static final String HASH_CACHE_FILE_NAME = "hashCache";
  private static final String IS_MODULE_AFFECTED_FILE_NAME = "affectedModules";
  private final PersistentHashMap<String, byte[]> hashCache;
  private final ProjectFileIndex projectFileIndex;
  private final Map<Module, List<VirtualFile>> moduleToContentSourceRoots;
  private final Map<String, Module> moduleNameToModule;
  private final PersistentHashMap<String, Boolean> isModuleAffected;

  public PersistentCachingModuleHashingService(File baseDir, Project project) throws IOException {
    this.moduleToContentSourceRoots = JpsCacheUtils.createModuleToSourceRootsMap(project);
    this.moduleNameToModule = JpsCacheUtils.createModuleNameToModuleMap(project);
    this.hashCache =
      new PersistentHashMap<>(new File(baseDir, HASH_CACHE_FILE_NAME), EnumeratorStringDescriptor.INSTANCE, ByteArrayDescriptor.INSTANCE);
    this.isModuleAffected = new PersistentHashMap<>(new File(baseDir, IS_MODULE_AFFECTED_FILE_NAME), EnumeratorStringDescriptor.INSTANCE,
                                                    BooleanDataDescriptor.INSTANCE);
    this.projectFileIndex = ProjectFileIndex.getInstance(project);


    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent e : events) {
          Module affectedModule = projectFileIndex.getModuleForFile(e.getFile());
          if (affectedModule == null) {
            continue;
          }
          try {
            isModuleAffected.put(affectedModule.getName(), true);
            hashCache.remove(affectedModule.getName());
          }
          catch (IOException ex) {
            LOG.warn(ex);
          }
        }
      }
    });

    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        try {
          hashCache.close();
        }
        catch (IOException e) {
        }
        try {
          isModuleAffected.close();
        }
        catch (IOException e) {
        }
      }
    });
  }

  public Map<String, byte[]> getAffectedModulesWithHashes() throws IOException {
    Map<String, byte[]> result = new HashMap<>();

    isModuleAffected.processKeysWithExistingMapping(moduleName -> {
      boolean isAffected = false;
      try {
        isAffected = isModuleAffected.get(moduleName);
        if (!isAffected) {
          return true;
        }
        result.put(moduleName, get(moduleName));
        isModuleAffected.put(moduleName, false);
        return true;
      }
      catch (IOException e) {
        LOG.warn(e);
        return false;
      }
    });

    return result;
  }

  byte[] get(String moduleName) throws IOException {
    byte[] hash = hashCache.get(moduleName);
    if (hash != null) {
      return hash;
    }

    hash = hashContentRootsAndPersist(moduleName,
                                      Optional.ofNullable(moduleToContentSourceRoots.get(moduleNameToModule.get(moduleName)))
                                        .map(vFiles ->
                                               vFiles.stream()
                                                 .map(vFile -> new File(vFile.getPath()))
                                                 .toArray(File[]::new)).orElse(null));
    cache(moduleName, hash);
    return hash;
  }

  private byte[] hashContentRootsAndPersist(String moduleName, File[] contentRoots) throws IOException {
    byte[] directoriesHash;
    if (contentRoots != null) {
      directoriesHash = super.hashDirectories(contentRoots);
    }
    else {
      directoriesHash = new byte[HASH_SIZE_IN_BYTES];
    }
    cache(moduleName, directoriesHash);
    return directoriesHash;
  }

  private void cache(String moduleName, byte[] hash) throws IOException {
    hashCache.put(moduleName, hash);
  }
}
