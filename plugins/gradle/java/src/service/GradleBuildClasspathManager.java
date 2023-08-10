// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service;

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageDirectoryCache;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Vladislav.Soroka
 */
public class GradleBuildClasspathManager {
  @NotNull
  private final Project myProject;

  @Nullable
  private volatile List<VirtualFile> allFilesCache = null;

  @NotNull
  private final AtomicReference<Map<String/*module path*/, List<VirtualFile> /*module build classpath*/>> myClasspathMap
    = new AtomicReference<>(new HashMap<>());

  @NotNull
  private final Map<String, PackageDirectoryCache> myClassFinderCache = ConcurrentFactoryMap
    .createMap(path -> PackageDirectoryCache.createCache(getModuleClasspathEntries(path)));

  public GradleBuildClasspathManager(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static GradleBuildClasspathManager getInstance(@NotNull Project project) {
    return project.getService(GradleBuildClasspathManager.class);
  }

  public void reload() {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    assert manager != null;
    AbstractExternalSystemLocalSettings<?> localSettings = manager.getLocalSettingsProvider().fun(myProject);

    Map<String/*module path*/, List<VirtualFile> /*module build classpath*/> map = new HashMap<>();

    final JarFileSystem jarFileSystem = JarFileSystem.getInstance();
    final Map<String, VirtualFile> localVFCache = new HashMap<>();

    for (final ExternalProjectBuildClasspathPojo projectBuildClasspathPojo : localSettings.getProjectBuildClasspath().values()) {
      final List<VirtualFile> projectBuildClasspath = new ArrayList<>();
      for (String path : projectBuildClasspathPojo.getProjectBuildClasspath()) {
        final VirtualFile virtualFile = localVFCache.computeIfAbsent(path, it -> ExternalSystemUtil.findLocalFileByPath(it)) ;
        ContainerUtil.addIfNotNull(projectBuildClasspath,
                                   virtualFile == null || virtualFile.isDirectory()
                                   ? virtualFile
                                   : jarFileSystem.getJarRootForLocalFile(virtualFile));
      }

      for (final ExternalModuleBuildClasspathPojo moduleBuildClasspathPojo : projectBuildClasspathPojo.getModulesBuildClasspath().values()) {
        final List<VirtualFile> moduleBuildClasspath = new ArrayList<>(projectBuildClasspath);
            for (String path : moduleBuildClasspathPojo.getEntries()) {
              final VirtualFile virtualFile = localVFCache.computeIfAbsent(path, it -> ExternalSystemUtil.findLocalFileByPath(it)) ;
              ContainerUtil.addIfNotNull(moduleBuildClasspath,
                                         virtualFile == null || virtualFile.isDirectory()
                                         ? virtualFile
                                         : jarFileSystem.getJarRootForLocalFile(virtualFile));
            }

        map.put(moduleBuildClasspathPojo.getPath(), moduleBuildClasspath);
      }
    }

    myClasspathMap.set(map);

    Set<VirtualFile> set = new LinkedHashSet<>();
    for (List<VirtualFile> virtualFiles : myClasspathMap.get().values()) {
      set.addAll(virtualFiles);
    }
    allFilesCache = new ArrayList<>(set);
    myClassFinderCache.clear();
  }

  @NotNull
  public Map<String, PackageDirectoryCache> getClassFinderCache() {
    return myClassFinderCache;
  }

  @NotNull
  public List<VirtualFile> getAllClasspathEntries() {
    checkRootsValidity(allFilesCache);
    if (allFilesCache == null) {
      reload();
    }
    return Objects.requireNonNull(allFilesCache);
  }

  @NotNull
  public List<VirtualFile> getModuleClasspathEntries(@NotNull String externalModulePath) {
    checkRootsValidity(myClasspathMap.get().get(externalModulePath));
    List<VirtualFile> virtualFiles = myClasspathMap.get().get(externalModulePath);
    return virtualFiles == null ? Collections.emptyList() : virtualFiles;
  }

  private void checkRootsValidity(@Nullable List<VirtualFile> virtualFiles) {
    if (virtualFiles == null) return;

    if (!virtualFiles.isEmpty()) {
      for (VirtualFile file : virtualFiles) {
        if (!file.isValid()) {
          reload();
          break;
        }
      }
    }
  }
}
