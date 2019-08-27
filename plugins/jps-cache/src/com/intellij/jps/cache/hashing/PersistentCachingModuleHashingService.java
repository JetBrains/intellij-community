package com.intellij.jps.cache.hashing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.io.BooleanDataDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.jps.cache.hashing.ModuleHashingService.HASH_SIZE_IN_BYTES;

public class PersistentCachingModuleHashingService {
  private static final Logger LOG = Logger.getInstance("com.jetbrains.cachepuller.PersistentCachingModuleHashingService");
  private static final String TEST_CACHE_FILE_NAME = "testCache";
  private static final String PRODUCTION_CACHE_FILE_NAME = "productionCache";
  private static final String AFFECTED_TESTS_FILE_NAME = "affectedTests";
  private static final String AFFECTED_PRODUCTION_FILE_NAME = "affectedProduction";
  private final PersistentHashMap<String, byte[]> testHashes;
  private final PersistentHashMap<String, byte[]> productionHashes;
  private final PersistentHashMap<String, Boolean> affectedTests;
  private final PersistentHashMap<String, Boolean> affectedProduction;
  private final ProjectFileIndex projectFileIndex;
  private final Map<String, Module> moduleNameToModule;

  public PersistentCachingModuleHashingService(File baseDir, Project project) throws IOException {
    this.moduleNameToModule = JpsCacheUtils.createModuleNameToModuleMap(project);
    this.testHashes =
      new PersistentHashMap<>(new File(baseDir, TEST_CACHE_FILE_NAME), EnumeratorStringDescriptor.INSTANCE, ByteArrayDescriptor.INSTANCE);
    this.productionHashes =
      new PersistentHashMap<>(new File(baseDir, PRODUCTION_CACHE_FILE_NAME), EnumeratorStringDescriptor.INSTANCE,
                              ByteArrayDescriptor.INSTANCE);
    this.affectedTests =
      new PersistentHashMap<>(new File(baseDir, AFFECTED_TESTS_FILE_NAME), EnumeratorStringDescriptor.INSTANCE,
                              BooleanDataDescriptor.INSTANCE);
    this.affectedProduction =
      new PersistentHashMap<>(new File(baseDir, AFFECTED_PRODUCTION_FILE_NAME), EnumeratorStringDescriptor.INSTANCE,
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
          final ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(affectedModule).getFileIndex();
          boolean isTest = moduleFileIndex.isInTestSourceContent(e.getFile());
          if (isTest) {
            try {
              affectedTests.put(affectedModule.getName(), true);
              testHashes.remove(affectedModule.getName());
            }
            catch (IOException ex) {
              LOG.warn(ex);
            }
          }
          else {
            try {
              affectedProduction.put(affectedModule.getName(), true);
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
        try {
          affectedTests.close();
        }
        catch (IOException e) {
          LOG.warn(e);
        }
        try {
          affectedProduction.close();
        }
        catch (IOException ex) {
          LOG.warn(ex);
        }
      }
    });
  }

  public Map<String, byte[]> getAffectedTests() {
    try {
      return getAffected(testHashes, affectedTests, Arrays.asList(JavaSourceRootType.TEST_SOURCE, JavaResourceRootType.TEST_RESOURCE));
    }
    catch (IOException e) {
      LOG.warn("Error while calculating hashes for affected tests", e);
      return Collections.emptyMap();
    }
  }

  public Map<String, byte[]> getAffectedProduction() {
    try {
      return getAffected(productionHashes, affectedProduction, Arrays.asList(JavaSourceRootType.SOURCE, JavaResourceRootType.RESOURCE));
    }
    catch (IOException e) {
      LOG.warn("Error while calculating hashes for affected sources", e);
      return Collections.emptyMap();
    }
  }

  private Map<String, byte[]> getAffected(PersistentHashMap<String, byte[]> hashCache,
                                          PersistentHashMap<String, Boolean> affected,
                                          List<JpsModuleSourceRootType<?>> rootTypes)
    throws IOException {
    Map<String, byte[]> result = new HashMap<>();

    affected.processKeysWithExistingMapping(moduleName -> {
      try {
        boolean isAffected = affected.get(moduleName);
        if (!isAffected) {
          return true;
        }
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(moduleNameToModule.get(moduleName));
        File[] sourceRoots =
          rootTypes.stream().map(moduleRootManager::getSourceRoots).flatMap(List::stream).map(vFile -> new File(vFile.getPath()))
            .toArray(File[]::new);
        result.put(moduleName, getFromCacheOrCalcAndPersist(moduleName, hashCache, sourceRoots));
        affected.put(moduleName, false);
        return true;
      }
      catch (IOException e) {
        LOG.warn(e);
        return false;
      }
    });

    return result;
  }

  private static byte[] getFromCacheOrCalcAndPersist(String moduleName, PersistentHashMap<String, byte[]> hashCache, File[] sourceRoots)
    throws IOException {
    byte[] hash = hashCache.get(moduleName);
    if (hash != null) {
      return hash;
    }
    hash = hashDirectoriesAndPersist(moduleName, sourceRoots, hashCache);
    return hash;
  }

  private static byte[] hashDirectoriesAndPersist(String name, File[] directories,
                                                  PersistentHashMap<String, byte[]> hashCache) throws IOException {
    byte[] directoriesHash;
    if (directories != null) {
      directoriesHash = ModuleHashingService.hashDirectories(directories);
    }
    else {
      directoriesHash = new byte[HASH_SIZE_IN_BYTES];
    }
    hashCache.put(name, directoriesHash);
    return directoriesHash;
  }
}
