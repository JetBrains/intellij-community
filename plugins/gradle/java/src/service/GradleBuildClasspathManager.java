// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service;

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.settings.ProjectBuildClasspathManager;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Vladislav.Soroka
 */
public class GradleBuildClasspathManager {
  private final @NotNull Project myProject;

  private volatile @Nullable List<VirtualFile> allFilesCache = null;

  private final @NotNull AtomicReference<Map<String/*module path*/, List<VirtualFile> /*module build classpath*/>> myClasspathMap
    = new AtomicReference<>(new HashMap<>());

  private final @NotNull Map<String, PackageDirectoryCache> myClassFinderCache = ConcurrentFactoryMap
    .createMap(path -> PackageDirectoryCache.createCache(getModuleClasspathEntries(path)));

  public GradleBuildClasspathManager(@NotNull Project project) {
    myProject = project;
  }

  public static @NotNull GradleBuildClasspathManager getInstance(@NotNull Project project) {
    return project.getService(GradleBuildClasspathManager.class);
  }

  public void reload() {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    assert manager != null;
    ProjectBuildClasspathManager buildClasspathManager = myProject.getService(ProjectBuildClasspathManager.class);

    Map<String/*module path*/, List<VirtualFile> /*module build classpath*/> map = new HashMap<>();

    final JarFileSystem jarFileSystem = JarFileSystem.getInstance();
    final Map<String, VirtualFile> localVFCache = new HashMap<>();
    final IdentityHashMap<List<String>, List<VirtualFile>> moduleClasspathCache = new IdentityHashMap<>();
    final Map<VirtualFile, VirtualFile> jarRootCache = new HashMap<>();


    for (final ExternalProjectBuildClasspathPojo projectBuildClasspathPojo : buildClasspathManager.getProjectBuildClasspath().values()) {
      final List<VirtualFile> projectBuildClasspath = new ArrayList<>();
      for (String path : projectBuildClasspathPojo.getProjectBuildClasspath()) {
        final VirtualFile virtualFile = localVFCache.computeIfAbsent(path, it -> ExternalSystemUtil.findLocalFileByPath(it));
        ContainerUtil.addIfNotNull(projectBuildClasspath,
                                   virtualFile == null || virtualFile.isDirectory()
                                   ? virtualFile
                                   : jarRootCache.computeIfAbsent(virtualFile, it -> jarFileSystem.getJarRootForLocalFile(it)));
      }

      for (final ExternalModuleBuildClasspathPojo moduleBuildClasspathPojo : projectBuildClasspathPojo.getModulesBuildClasspath()
        .values()) {
        List<VirtualFile> moduleBuildClasspath = moduleClasspathCache.computeIfAbsent(moduleBuildClasspathPojo.getEntries(), entries -> {
          final List<VirtualFile> classpath = new ArrayList<>(projectBuildClasspath);
          for (String path : entries) {
            final VirtualFile virtualFile = localVFCache.computeIfAbsent(path, it -> ExternalSystemUtil.findLocalFileByPath(it));
            ContainerUtil.addIfNotNull(classpath,
                                       virtualFile == null || virtualFile.isDirectory()
                                       ? virtualFile
                                       : jarRootCache.computeIfAbsent(virtualFile, it -> jarFileSystem.getJarRootForLocalFile(it)));
          }
          return classpath;
        });

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

  public @NotNull Map<String, PackageDirectoryCache> getClassFinderCache() {
    return myClassFinderCache;
  }

  public @NotNull List<VirtualFile> getAllClasspathEntries() {
    checkRootsValidity(allFilesCache);
    if (allFilesCache == null) {
      reload();
    }
    return Objects.requireNonNull(allFilesCache);
  }

  public @NotNull List<VirtualFile> getModuleClasspathEntries(@NotNull String externalModulePath) {
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
