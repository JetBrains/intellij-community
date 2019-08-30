package com.intellij.jps.cache.hashing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PersistentCachingModuleHashingService {
  private static final Logger LOG = Logger.getInstance("com.jetbrains.cachepuller.PersistentCachingModuleHashingService");
  private static final String TEST_CACHE_FILE_NAME = "testCache";
  private static final String PRODUCTION_CACHE_FILE_NAME = "productionCache";
  private final PersistentHashMap<String, byte[]> testHashes;
  private final PersistentHashMap<String, byte[]> productionHashes;
  private final ProjectFileIndex projectFileIndex;
  private final Map<String, Module> moduleNameToModule;
  private final Project project;

  public PersistentCachingModuleHashingService(File baseDir, Project project) throws IOException {
    this.project = project;
    this.moduleNameToModule = JpsCacheUtils.createModuleNameToModuleMap(project);
    File testHashesFile = new File(baseDir, TEST_CACHE_FILE_NAME);
    File productionHashesFile = new File(baseDir, PRODUCTION_CACHE_FILE_NAME);
    boolean shouldComputeHashesForEntireProject = !testHashesFile.exists() || !productionHashesFile.exists();
    this.testHashes =
      new PersistentHashMap<>(testHashesFile, EnumeratorStringDescriptor.INSTANCE, ByteArrayDescriptor.INSTANCE);
    this.productionHashes =
      new PersistentHashMap<>(productionHashesFile, EnumeratorStringDescriptor.INSTANCE,
                              ByteArrayDescriptor.INSTANCE);
    this.projectFileIndex = ProjectFileIndex.getInstance(project);

    if (shouldComputeHashesForEntireProject) {
      long start = System.currentTimeMillis();
      System.out.println("Start filling productionHashes and testHashes");
      computeHashesForProjectAndPersist();
      long end = System.currentTimeMillis() - start;
      System.out.println("Finish in itialization: " + end);
    }

    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent e : events) {
          Module affectedModule = projectFileIndex.getModuleForFile(e.getFile());
          if (affectedModule == null) {
            continue;
          }
          final ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(affectedModule).getFileIndex();
          boolean isTest = moduleFileIndex.isInTestSourceContent(e.getFile());
          if (isTest) {
            try {
              testHashes.remove(affectedModule.getName());
            }
            catch (IOException ex) {
              LOG.warn(ex);
            }
          }
          else {
            try {
              productionHashes.remove(affectedModule.getName());
            }
            catch (IOException ex) {
              LOG.warn(ex);
            }
          }
        }
      }
    });

    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        try {
          productionHashes.close();
        }
        catch (IOException e) {
          LOG.warn(e);
        }
        try {
          testHashes.close();
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
    });
  }

  public void computeHashesForProjectAndPersist() {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    System.out.println("Modules count " + modules.length);
    Arrays.stream(modules).forEach(module -> {
      File[] productionSourceRoots = getProductionSources(module);
      hashDirectoriesAndPersist(module.getName(), productionSourceRoots, productionHashes);

      File[] testSourceRoots = getTestSources(module);
      hashDirectoriesAndPersist(module.getName(), testSourceRoots, testHashes);
    });
  }

  public Map<String, byte[]> computeProductionHashesForProject() {
    Map<String, byte[]> result = new HashMap<>();
    try {
      Collection<String> keys = productionHashes.getAllKeysWithExistingMapping();
      for (String key : keys) {
        result.put(key, productionHashes.get(key));
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
      Collection<String> keys = testHashes.getAllKeysWithExistingMapping();
      for (String key : keys) {
        result.put(key, testHashes.get(key));
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }

  public Map<String, byte[]> getAffectedTests() {
    return getAffected(testHashes, Arrays.asList(JavaSourceRootType.TEST_SOURCE, JavaResourceRootType.TEST_RESOURCE));
  }

  public Map<String, byte[]> getAffectedProduction() {
    return getAffected(productionHashes, Arrays.asList(JavaSourceRootType.SOURCE, JavaResourceRootType.RESOURCE));
  }

  private File[] getProductionSources(Module module) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

    return Stream.concat(moduleRootManager.getSourceRoots(JavaSourceRootType.SOURCE).stream(),
                         moduleRootManager.getSourceRoots(JavaResourceRootType.RESOURCE).stream())
      .map(vf -> new File(vf.getPath())).toArray(File[]::new);
  }

  private File[] getTestSources(Module module) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

    return Stream.concat(moduleRootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE).stream(),
                         moduleRootManager.getSourceRoots(JavaResourceRootType.TEST_RESOURCE).stream())
      .map(vf -> new File(vf.getPath())).toArray(File[]::new);
  }

  private Map<String, byte[]> getAffected(PersistentHashMap<String, byte[]> hashCache,
                                          List<JpsModuleSourceRootType<?>> rootTypes) {
    Map<String, byte[]> result = new HashMap<>();
    Module[] modules = ModuleManager.getInstance(project).getModules();

    Arrays.stream(modules).forEach(module -> {
      try {
        String moduleName = module.getName();
        boolean isAffected = hashCache.get(moduleName) == null;
        if (!isAffected) {
          return;
        }
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(moduleNameToModule.get(moduleName));
        File[] sourceRoots =
          rootTypes.stream().map(moduleRootManager::getSourceRoots).flatMap(List::stream).map(vFile -> new File(vFile.getPath()))
            .toArray(File[]::new);
        getFromCacheOrCalcAndPersist(moduleName, hashCache, sourceRoots).map(hash -> result.put(moduleName, hash));
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    });

    return result;
  }

  private static Optional<byte[]> getFromCacheOrCalcAndPersist(String moduleName,
                                                               PersistentHashMap<String, byte[]> hashCache,
                                                               File[] sourceRoots)
    throws IOException {
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
