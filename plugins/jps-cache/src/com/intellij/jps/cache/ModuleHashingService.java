package com.intellij.jps.cache;

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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

interface PathRelativizer {
  String relativize(File target);
}

class ModuleHashingService {
  private static final Logger LOG = Logger.getInstance("com.jetbrains.cachepuller.ModuleHashingService");
  protected static final int HASH_SIZE_IN_BYTES = 16;

  byte[] hashContentRoots(File[] contentRoots) {
    byte[] hash = new byte[HASH_SIZE_IN_BYTES];

    for (File curContentRoot : contentRoots) {
      byte[] curHash = hashDirectory(curContentRoot, new RelativeToDirectoryRelativizer(curContentRoot.getPath()));
      sum(hash, curHash);
    }

    return hash;
  }

  byte[] hashDirectory(File dir, RelativeToDirectoryRelativizer relativizer) {
    File[] fileList = Optional.ofNullable(dir.listFiles()).orElse(new File[0]);
    byte[] hash = new byte[HASH_SIZE_IN_BYTES];


    for (File file : fileList) {
      byte[] curHash = file.isDirectory() ? hashDirectory(file, relativizer) : hashFile(file, relativizer);
      sum(hash, curHash);
    }

    byte[] dirNameHash = hashFileName(dir);
    sum(hash, dirNameHash);

    return hash;
  }

  byte[] hashFile(File file, PathRelativizer relativizer) {
    String filePathRelativeToModule = relativizer.relativize(file);
    byte[] fileNameBytes = filePathRelativeToModule.getBytes();
    byte[] buffer = new byte[(int)file.length() + fileNameBytes.length];
    try (FileInputStream fileInputStream = new FileInputStream(file)) {
      fileInputStream.read(buffer);
    }
    catch (FileNotFoundException e) {
      LOG.warn("File not found: ", e);
      return null;
    }
    catch (IOException e) {
      LOG.warn(String.format("Error while hashing file %s : ", file.getAbsolutePath()), e);
      return null;
    }
    for (int i = (int)file.length(), j = 0; i < buffer.length; ++i, ++j) {
      buffer[i] = fileNameBytes[j];
    }

    MessageDigest messageDigest = null;
    try {
      messageDigest = MessageDigest.getInstance("MD5");
    }
    catch (NoSuchAlgorithmException e) {
      LOG.warn("MD5 hashing algorithm not found: ", e);
    }
    messageDigest.reset();
    messageDigest.update(buffer);
    return messageDigest.digest();
  }

  private byte[] hashFileName(File file) {
    byte[] fileNameBytes = file.getName().getBytes();
    MessageDigest messageDigest = null;
    try {
      messageDigest = MessageDigest.getInstance("MD5");
    }
    catch (NoSuchAlgorithmException e) {
      LOG.warn("MD5 hashing algorithm not found : ", e);
      return null;
    }
    messageDigest.reset();
    messageDigest.update(fileNameBytes);
    return messageDigest.digest();
  }

  private void sum(byte[] firstHash, byte[] secondHash) {
    for (int i = 0; i < firstHash.length; ++i) {
      firstHash[i] += secondHash[i];
    }
  }
}

class PersistentCachingModuleHashingService extends ModuleHashingService {
  private static final Logger LOG = Logger.getInstance("com.jetbrains.cachepuller.PersistentCachingModuleHashingService");
  private static final String HASH_CACHE_FILE_NAME = "hashCache";
  private static final String IS_MODULE_AFFECTED_FILE_NAME = "affectedModules";
  private final PersistentHashMap<String, byte[]> hashCache;
  private final ProjectFileIndex projectFileIndex;
  private final Map<Module, List<VirtualFile>> moduleToContentSourceRoots;
  private final Map<String, Module> moduleNameToModule;
  private final PersistentHashMap<String, Boolean> isModuleAffected;

  PersistentCachingModuleHashingService(File baseDir, Project project) throws IOException {
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

  Map<String, byte[]> getAffectedModulesWithHashes() throws IOException {
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
      directoriesHash = super.hashContentRoots(contentRoots);
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

  private void cacheIfAbsent(String moduleName, byte[] hash) throws IOException {
    byte[] hashFromCache = hashCache.get(moduleName);
    if (hashFromCache != null) {
      return;
    }
    hashCache.put(moduleName, hash);
  }

  private void removeHashForModule(Module module) throws IOException {
    hashCache.remove(module.getName());
  }
}

class RelativeToDirectoryRelativizer implements PathRelativizer {
  private final String rootPath;

  RelativeToDirectoryRelativizer(String rootModulePath) {
    this.rootPath = rootModulePath;
  }

  @Override
  public String relativize(File target) {
    return Paths.get(rootPath).relativize(Paths.get(target.getPath())).toString();
  }
}

