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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.DatatypeConverter;
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
  private final ModuleHashingService myModuleHashingService;
  private final Set<Module> myAffectedProductionModules;
  private final Set<Module> myAffectedTestModules;
  private final ProjectFileIndex myProjectFileIndex;
  private final Project myProject;
  private boolean isInitialized;

  public PersistentCachingModuleHashingService(File baseDir, Project project) throws IOException {
    myProject = project;
    myAffectedTestModules = ContainerUtil.newConcurrentSet();
    myAffectedProductionModules = ContainerUtil.newConcurrentSet();
    myModuleHashingService = new ModuleHashingService();
    myModuleNameToModuleMap = JpsCachesUtils.createModuleNameToModuleMap(project);
    File testHashesFile = new File(baseDir, TEST_CACHE_FILE_NAME);
    File productionHashesFile = new File(baseDir, PRODUCTION_CACHE_FILE_NAME);
    boolean shouldComputeHashesForEntireProject = !testHashesFile.exists() || !productionHashesFile.exists();
    myTestHashes = new PersistentHashMap<>(testHashesFile, EnumeratorStringDescriptor.INSTANCE, ByteArrayDescriptor.INSTANCE);
    myProductionHashes = new PersistentHashMap<>(productionHashesFile, EnumeratorStringDescriptor.INSTANCE,
                                                 ByteArrayDescriptor.INSTANCE);
    myProjectFileIndex = ProjectFileIndex.getInstance(project);
    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, this);

    if (shouldComputeHashesForEntireProject) {
      long start = System.currentTimeMillis();
      LOG.debug("Modules hash calculating started");
      computeHashesForProjectAndPersist();
      long end = System.currentTimeMillis() - start;
      LOG.debug("Modules hash calculating finished: " + end + "ms");
      handleAffectedModules();
    }
    isInitialized = true;
    LOG.info("Plugin initialization finished");
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    events.stream().map(VFileEvent::getFile).filter(Objects::nonNull).forEach(vf -> {
      Module affectedModule = myProjectFileIndex.getModuleForFile(vf);
      if (affectedModule == null) return;
      ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(affectedModule).getFileIndex();
      if (moduleFileIndex.isInTestSourceContent(vf)) {
        myAffectedTestModules.add(affectedModule);
      }
      else {
        myAffectedProductionModules.add(affectedModule);
      }
    });
    if (isInitialized) handleAffectedModules();
  }

  private void handleAffectedModules() {
    for (Module affectedModule : myAffectedProductionModules) {
      try {
        LOG.debug("Changed production sources of module: " + affectedModule.getName());
        myProductionHashes.remove(affectedModule.getName());
      }
      catch (IOException ex) {
        LOG.warn("Couldn't remove affected module from collection", ex);
      }
    }
    myAffectedProductionModules.clear();

    for (Module affectedModule : myAffectedTestModules) {
      try {
        LOG.debug("Changed test sources of module: " + affectedModule.getName());
        myTestHashes.remove(affectedModule.getName());
      }
      catch (IOException ex) {
        LOG.warn("Couldn't remove affected module from collection", ex);
      }
    }
    myAffectedTestModules.clear();
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

  public Map<String, String> computeProductionHashesForProject() {
    Map<String, String> result = new HashMap<>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    Arrays.stream(modules).forEach(module -> {
      try {
        String moduleName = module.getName();
        byte[] moduleHash = myProductionHashes.get(moduleName);
        if (moduleHash != null) {
          result.put(moduleName, convertToStringRepr(moduleHash));
          return;
        }
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModuleNameToModuleMap.get(moduleName));
        File[] sourceRoots = JpsCachesUtils.getProductionSourceRootFiles(moduleRootManager);
        getFromCacheOrCalcAndPersist(moduleName, myProductionHashes, sourceRoots)
          .map(hash -> result.put(moduleName, convertToStringRepr(hash)));
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    });
    return result;
  }

  public Map<String, String> computeTestHashesForProject() {
    Map<String, String> result = new HashMap<>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    Arrays.stream(modules).forEach(module -> {
      try {
        String moduleName = module.getName();
        byte[] moduleHash = myTestHashes.get(moduleName);
        if (moduleHash != null) {
          result.put(moduleName, convertToStringRepr(moduleHash));
          return;
        }
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModuleNameToModuleMap.get(moduleName));
        File[] sourceRoots = JpsCachesUtils.getTestSourceRootFiles(moduleRootManager);
        getFromCacheOrCalcAndPersist(moduleName, myTestHashes, sourceRoots)
          .map(hash -> result.put(moduleName, convertToStringRepr(hash)));
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    });
    return result;
  }

  public Map<String, String> getAffectedTests() {
    return getAffected(myTestHashes, JpsCachesUtils::getTestSourceRootFiles);
  }

  public Map<String, String> getAffectedProduction() {
    return getAffected(myProductionHashes, JpsCachesUtils::getProductionSourceRootFiles);
  }

  private Map<String, String> getAffected(PersistentHashMap<String, byte[]> hashCache,
                                          Function<ModuleRootManager, File[]> sourceRootFunction) {
    Map<String, String> result = new HashMap<>();
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
        getFromCacheOrCalcAndPersist(moduleName, hashCache, sourceRoots).map(hash -> result.put(moduleName, convertToStringRepr(hash)));
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    });

    return result;
  }

  private Optional<byte[]> getFromCacheOrCalcAndPersist(String moduleName, PersistentHashMap<String, byte[]> hashCache,
                                                        File[] sourceRoots) throws IOException {
    byte[] hashFromCache = hashCache.get(moduleName);
    if (hashFromCache != null) {
      return Optional.of(hashFromCache);
    }
    return hashDirectoriesAndPersist(moduleName, sourceRoots, hashCache);
  }

  private Optional<byte[]> hashDirectoriesAndPersist(String name, File[] directories, PersistentHashMap<String, byte[]> hashCache) {
    if (directories == null || directories.length == 0) {
      return Optional.empty();
    }
    return myModuleHashingService.hashDirectories(directories).map(hash -> {
      try {
        hashCache.put(name, hash);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      return hash;
    });
  }

  private static String convertToStringRepr(byte[] hash) {
    //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
    return DatatypeConverter.printHexBinary(hash).toLowerCase();
  }
}
