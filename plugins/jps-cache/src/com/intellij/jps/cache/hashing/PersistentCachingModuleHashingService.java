package com.intellij.jps.cache.hashing;

import com.intellij.jps.cache.JpsCachesUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class PersistentCachingModuleHashingService implements BulkFileListener {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService");
  private static final String PRODUCTION_CACHE_FILE_NAME = "productionCache";
  private static final String TEST_CACHE_FILE_NAME = "testCache";
  private final PersistentHashMap<String, byte[]> myProductionHashes;
  private final PersistentHashMap<String, byte[]> myTestHashes;
  private final Map<String, Module> myModuleNameToModuleMap;
  private final ProjectFileIndex myProjectFileIndex;
  private final Project myProject;

  public PersistentCachingModuleHashingService(File baseDir, Project project) throws IOException {
    myProject = project;
    myModuleNameToModuleMap = JpsCachesUtils.createModuleNameToModuleMap(project);
    File testHashesFile = new File(baseDir, TEST_CACHE_FILE_NAME);
    File productionHashesFile = new File(baseDir, PRODUCTION_CACHE_FILE_NAME);
    boolean shouldComputeHashesForEntireProject = !testHashesFile.exists() || !productionHashesFile.exists();
    myTestHashes = new PersistentHashMap<>(testHashesFile, EnumeratorStringDescriptor.INSTANCE, ByteArrayDescriptor.INSTANCE);
    myProductionHashes = new PersistentHashMap<>(productionHashesFile, EnumeratorStringDescriptor.INSTANCE,
                                                 ByteArrayDescriptor.INSTANCE);
    myProjectFileIndex = ProjectFileIndex.getInstance(project);

    if (shouldComputeHashesForEntireProject) {
      long start = System.currentTimeMillis();
      LOG.debug("Modules hash calculating started");
      computeHashesForProjectAndPersist();
      long end = System.currentTimeMillis() - start;
      LOG.debug("Modules hash calculating finished: " + end + "ms");
    }
    else {
      LOG.debug("Initialization finished");
    }
    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    Set<Module> affectedProductionModules = new HashSet<>();
    Set<Module> affectedTestModules = new HashSet<>();
    events.stream().map(VFileEvent::getFile).filter(Objects::nonNull).forEach(vf -> {
      Module affectedModule = myProjectFileIndex.getModuleForFile(vf);
      if (affectedModule == null) return;
      ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(affectedModule).getFileIndex();
      if (moduleFileIndex.isInTestSourceContent(vf)) {
        affectedTestModules.add(affectedModule);
      } else {
        affectedProductionModules.add(affectedModule);
      }
    });

    for (Module affectedModule : affectedProductionModules) {
      try {
        LOG.debug("Changed production sources of module: " + affectedModule.getName());
        myProductionHashes.remove(affectedModule.getName());
      }
      catch (IOException ex) {
        LOG.warn("Couldn't remove affected module from collection", ex);
      }
    }

    for (Module affectedModule : affectedTestModules) {
      try {
        LOG.debug("Changed test sources of module: " + affectedModule.getName());
        myTestHashes.remove(affectedModule.getName());
      }
      catch (IOException ex) {
        LOG.warn("Couldn't remove affected module from collection", ex);
      }
    }
  }

  public void close() {
    try {
      myProductionHashes.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    try {
      myTestHashes.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public void computeHashesForProjectAndPersist() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    Arrays.stream(modules).forEach(module -> {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

      File[] productionSourceRoots = JpsCachesUtils.getProductionSourceRootFiles(moduleRootManager);
      hashDirectoriesAndPersist(module.getName(), productionSourceRoots, myProductionHashes);

      File[] testSourceRoots = JpsCachesUtils.getTestSourceRootFiles(moduleRootManager);
      hashDirectoriesAndPersist(module.getName(), testSourceRoots, myTestHashes);
    });
  }

  public Map<String, byte[]> computeProductionHashesForProject() {
    Map<String, byte[]> result = new HashMap<>();
    try {
      Collection<String> keys = myProductionHashes.getAllKeysWithExistingMapping();
      for (String key : keys) {
        result.put(key, myProductionHashes.get(key));
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }

  public Map<String, byte[]> computeTestHashesForProject() {
    Map<String, byte[]> result = new HashMap<>();
    try {
      Collection<String> keys = myTestHashes.getAllKeysWithExistingMapping();
      for (String key : keys) {
        result.put(key, myTestHashes.get(key));
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }

  public Map<String, byte[]> getAffectedTests() {
    return getAffected(myTestHashes, JpsCachesUtils::getTestSourceRootFiles);
  }

  public Map<String, byte[]> getAffectedProduction() {
    return getAffected(myProductionHashes, JpsCachesUtils::getProductionSourceRootFiles);
  }

  private Map<String, byte[]> getAffected(PersistentHashMap<String, byte[]> hashCache, Function<ModuleRootManager, File[]> sourceRootFunction) {
    Map<String, byte[]> result = new HashMap<>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();

    Arrays.stream(modules).forEach(module -> {
      try {
        String moduleName = module.getName();
        boolean isAffected = hashCache.get(moduleName) == null;
        if (!isAffected) {
          return;
        }
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModuleNameToModuleMap.get(moduleName));
        File[] sourceRoots = sourceRootFunction.apply(moduleRootManager);
        getFromCacheOrCalcAndPersist(moduleName, hashCache, sourceRoots).map(hash -> result.put(moduleName, hash));
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    });

    return result;
  }

  private static Optional<byte[]> getFromCacheOrCalcAndPersist(String moduleName, PersistentHashMap<String, byte[]> hashCache,
                                                               File[] sourceRoots) throws IOException {
    byte[] hashFromCache = hashCache.get(moduleName);
    if (hashFromCache != null) {
      return Optional.of(hashFromCache);
    }
    return hashDirectoriesAndPersist(moduleName, sourceRoots, hashCache);
  }

  private static Optional<byte[]> hashDirectoriesAndPersist(String name, File[] directories, PersistentHashMap<String, byte[]> hashCache) {
    if (directories == null || directories.length == 0) {
      return Optional.empty();
    }
    return ModuleHashingService.hashDirectories(directories).map(hash -> {
      try {
        hashCache.put(name, hash);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      return hash;
    });
  }
}
