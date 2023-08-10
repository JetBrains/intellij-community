// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.composite.internal.DefaultIncludedBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.util.JavaPluginUtil;
import org.jetbrains.plugins.gradle.tooling.util.ReflectionUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Collections.unmodifiableMap;
import static org.jetbrains.plugins.gradle.tooling.ModelBuilderContext.DataProvider;
import static org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl.isIsNewDependencyResolutionApplicable;

public class SourceSetCachedFinder {

  private static final @NotNull GradleVersion gradleBaseVersion = GradleVersion.current().getBaseVersion();
  private static final boolean is51OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("5.1")) >= 0;

  private static final @NotNull DataProvider<ArtifactsMap> ARTIFACTS_PROVIDER =
    (gradle, ___) -> createArtifactsMap(gradle);

  private static final @NotNull DataProvider<ConcurrentMap<String, Set<File>>> SOURCES_DATA_KEY =
    (__, ___) -> new ConcurrentHashMap<>();

  private final @NotNull ArtifactsMap myArtifactsMap;
  private final @NotNull ConcurrentMap<String, Set<File>> mySourcesMap;

  public SourceSetCachedFinder(@NotNull ModelBuilderContext context) {
    myArtifactsMap = context.getData(ARTIFACTS_PROVIDER);
    mySourcesMap = context.getData(SOURCES_DATA_KEY);
  }

  public @Nullable Set<File> findSourcesByArtifact(@NotNull String path) {
    if (!mySourcesMap.containsKey(path)) {
      SourceSet sourceSet = myArtifactsMap.myArtifactsMap.get(path);
      if (sourceSet != null) {
        Set<File> sources = sourceSet.getAllJava().getSrcDirs();
        Set<File> calculatedSources = mySourcesMap.putIfAbsent(path, sources);
        return calculatedSources != null ? calculatedSources : sources;
      }
    }
    return null;
  }

  public @Nullable SourceSet findByArtifact(@NotNull String artifactPath) {
    return myArtifactsMap.myArtifactsMap.get(artifactPath);
  }

  public @Nullable String findArtifactBySourceSetOutputDir(@NotNull String outputPath) {
    return myArtifactsMap.mySourceSetOutputDirsToArtifactsMap.get(outputPath);
  }

  private static @NotNull ArtifactsMap createArtifactsMap(@NotNull Gradle gradle) {
    Map<String, SourceSet> artifactsMap = new HashMap<>();
    Map<String, String> sourceSetOutputDirsToArtifactsMap = new HashMap<>();
    List<Project> projects = new ArrayList<>(gradle.getRootProject().getAllprojects());
    boolean isCompositeBuildsSupported = isIsNewDependencyResolutionApplicable() ||
                                         GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("3.1")) >= 0;
    if (isCompositeBuildsSupported) {
      projects.addAll(exposeIncludedBuilds(gradle));
    }
    for (Project project : projects) {
      SourceSetContainer sourceSetContainer = JavaPluginUtil.getJavaPluginAccessor(project).getSourceSetContainer();
      if (sourceSetContainer == null || sourceSetContainer.isEmpty()) continue;

      for (SourceSet sourceSet : sourceSetContainer) {
        Task task = project.getTasks().findByName(sourceSet.getJarTaskName());
        if (task instanceof AbstractArchiveTask) {
          AbstractArchiveTask jarTask = (AbstractArchiveTask)task;
          File archivePath = is51OrBetter ?
                             ReflectionUtil.reflectiveGetProperty(jarTask, "getArchiveFile", RegularFile.class).getAsFile() :
                             ReflectionUtil.reflectiveCall(jarTask, "getArchivePath", File.class);
          if (archivePath != null) {
            artifactsMap.put(archivePath.getPath(), sourceSet);
            if (isIsNewDependencyResolutionApplicable()) {
              for (File file : sourceSet.getOutput().getClassesDirs().getFiles()) {
                sourceSetOutputDirsToArtifactsMap.put(file.getPath(), archivePath.getPath());
              }
              File resourcesDir = Objects.requireNonNull(sourceSet.getOutput().getResourcesDir());
              sourceSetOutputDirsToArtifactsMap.put(resourcesDir.getPath(), archivePath.getPath());
            }
          }
        }
      }
    }
    return new ArtifactsMap(artifactsMap, sourceSetOutputDirsToArtifactsMap);
  }

  private static @NotNull List<Project> exposeIncludedBuilds(@NotNull Gradle gradle) {
    List<Project> result = new ArrayList<>();
    for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
      Object unwrapped = maybeUnwrapIncludedBuildInternal(includedBuild);
      if (unwrapped instanceof DefaultIncludedBuild) {
        DefaultIncludedBuild build = (DefaultIncludedBuild)unwrapped;
        if (is51OrBetter) {
          result.addAll(build.withState(it -> it.getRootProject().getAllprojects()));
        } else {
          result.addAll(getProjectsWithReflection(build));
        }
      }
    }
    return result;
  }

  private static @NotNull Set<Project> getProjectsWithReflection(@NotNull DefaultIncludedBuild build) {
    GradleInternal gradleInternal = ReflectionUtil.reflectiveCall(build, "getConfiguredBuild", GradleInternal.class);
    return gradleInternal.getRootProject().getAllprojects();
  }

  private static @NotNull Object maybeUnwrapIncludedBuildInternal(@NotNull IncludedBuild includedBuild) {
    Class<?> includedBuildInternalClass = ReflectionUtil.findClassForName("org.gradle.internal.composite.IncludedBuildInternal");
    if (includedBuildInternalClass != null && includedBuildInternalClass.isAssignableFrom(includedBuild.getClass())) {
      return ReflectionUtil.reflectiveCall(includedBuild, "getTarget", Object.class);
    }
    return includedBuild;
  }

  private static class ArtifactsMap {

    public final @NotNull Map<String, SourceSet> myArtifactsMap;
    public final @NotNull Map<String, String> mySourceSetOutputDirsToArtifactsMap;

    ArtifactsMap(@NotNull Map<String, SourceSet> artifactsMap, @NotNull Map<String, String> sourceSetOutputDirsToArtifactsMap) {
      myArtifactsMap = unmodifiableMap(artifactsMap);
      mySourceSetOutputDirsToArtifactsMap = unmodifiableMap(sourceSetOutputDirsToArtifactsMap);
    }
  }
}

