// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.composite.internal.DefaultIncludedBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFile;
import static org.jetbrains.plugins.gradle.tooling.ModelBuilderContext.DataProvider;
import static org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl.isIsNewDependencyResolutionApplicable;

public class GradleSourceSetCachedFinder {

  private static final boolean is51OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("5.1");
  private static final boolean is73OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("7.3");

  private final @NotNull ArtifactsMap myArtifactsMap;
  private final @NotNull ConcurrentMap<String, Set<File>> mySourceMap;

  private GradleSourceSetCachedFinder(@NotNull ModelBuilderContext context) {
    mySourceMap = new ConcurrentHashMap<>();
    myArtifactsMap = createArtifactsMap(context);
  }

  public @NotNull List<File> findArtifactSources(@NotNull Collection<? extends File> artifactFiles) {
    List<File> artifactSources = new ArrayList<>();
    for (File artifactFile : artifactFiles) {
      artifactSources.addAll(findSourcesByArtifact(artifactFile.getPath()));
    }
    return artifactSources;
  }

  private @NotNull Set<File> findSourcesByArtifact(@NotNull String path) {
    if (!mySourceMap.containsKey(path)) {
      SourceSet sourceSet = myArtifactsMap.myArtifactsMap.get(path);
      if (sourceSet != null) {
        Set<File> sources = sourceSet.getAllJava().getSrcDirs();
        Set<File> calculatedSources = mySourceMap.putIfAbsent(path, sources);
        return calculatedSources != null ? calculatedSources : sources;
      }
    }
    return Collections.emptySet();
  }

  public @Nullable SourceSet findByArtifact(@NotNull String artifactPath) {
    return myArtifactsMap.myArtifactsMap.get(artifactPath);
  }

  public @Nullable String findArtifactBySourceSetOutputDir(@NotNull String outputPath) {
    return myArtifactsMap.mySourceSetOutputDirsToArtifactsMap.get(outputPath);
  }

  private static @NotNull ArtifactsMap createArtifactsMap(@NotNull ModelBuilderContext context) {
    Gradle gradle = context.getGradle();
    Map<String, SourceSet> artifactsMap = new HashMap<>();
    Map<String, String> sourceSetOutputDirsToArtifactsMap = new HashMap<>();
    List<Project> projects = new ArrayList<>(gradle.getRootProject().getAllprojects());
    boolean isCompositeBuildsSupported = isIsNewDependencyResolutionApplicable() ||
                                         GradleVersionUtil.isCurrentGradleAtLeast("3.1");
    if (isCompositeBuildsSupported) {
      projects.addAll(exposeIncludedBuilds(gradle));
    }
    for (Project project : projects) {
      SourceSetContainer sourceSetContainer = JavaPluginUtil.getSourceSetContainer(project);
      if (sourceSetContainer == null || sourceSetContainer.isEmpty()) continue;

      for (SourceSet sourceSet : sourceSetContainer) {
        Task task = project.getTasks().findByName(sourceSet.getJarTaskName());
        if (task instanceof AbstractArchiveTask) {
          AbstractArchiveTask jarTask = (AbstractArchiveTask)task;
          File archivePath = getTaskArchiveFile(jarTask);
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
        if (is73OrBetter) {
          build.ensureProjectsConfigured();
        }
        if (is51OrBetter) {
          result.addAll(build.withState(
            it -> it.getRootProject().getAllprojects()
          ));
        } else {
          result.addAll(getProjectsWithReflection(build));
        }
      }
    }
    return result;
  }

  private static @NotNull Set<Project> getProjectsWithReflection(@NotNull DefaultIncludedBuild build) {
    GradleInternal gradleInternal = GradleReflectionUtil.reflectiveCall(build, "getConfiguredBuild", GradleInternal.class);
    return gradleInternal.getRootProject().getAllprojects();
  }

  private static @NotNull Object maybeUnwrapIncludedBuildInternal(@NotNull IncludedBuild includedBuild) {
    Class<?> includedBuildInternalClass = GradleReflectionUtil.findClassForName("org.gradle.internal.composite.IncludedBuildInternal");
    if (includedBuildInternalClass != null && includedBuildInternalClass.isAssignableFrom(includedBuild.getClass())) {
      return GradleReflectionUtil.reflectiveCall(includedBuild, "getTarget", Object.class);
    }
    return includedBuild;
  }

  private static class ArtifactsMap {

    public final @NotNull Map<String, SourceSet> myArtifactsMap;
    public final @NotNull Map<String, String> mySourceSetOutputDirsToArtifactsMap;

    ArtifactsMap(@NotNull Map<String, SourceSet> artifactsMap, @NotNull Map<String, String> sourceSetOutputDirsToArtifactsMap) {
      myArtifactsMap = Collections.unmodifiableMap(artifactsMap);
      mySourceSetOutputDirsToArtifactsMap = Collections.unmodifiableMap(sourceSetOutputDirsToArtifactsMap);
    }
  }

  private static final @NotNull DataProvider<GradleSourceSetCachedFinder> INSTANCE_PROVIDER = GradleSourceSetCachedFinder::new;

  public static @NotNull GradleSourceSetCachedFinder getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}

