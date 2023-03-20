/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.tooling.util

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.composite.internal.DefaultIncludedBuild
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.tooling.MessageReporter
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import static java.util.Collections.unmodifiableMap
import static org.jetbrains.plugins.gradle.tooling.ModelBuilderContext.DataProvider
import static org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl.isIsNewDependencyResolutionApplicable

/**
 * @author Vladislav.Soroka
 */
@CompileStatic
class SourceSetCachedFinder {
  private static final GradleVersion gradleBaseVersion = GradleVersion.current().baseVersion
  private static final boolean is51OrBetter = gradleBaseVersion >= GradleVersion.version("5.1")

  private static final DataProvider<ArtifactsMap> ARTIFACTS_PROVIDER = new DataProvider<ArtifactsMap>() {
    @NotNull
    @Override
    ArtifactsMap create(@NotNull Gradle gradle, @NotNull MessageReporter messageReporter) {
      return createArtifactsMap(gradle)
    }
  }
  private static final DataProvider<ConcurrentMap<String, Set<File>>> SOURCES_DATA_KEY = new DataProvider<ConcurrentMap<String, Set<File>>>() {
    @NotNull
    @Override
    ConcurrentMap<String, Set<File>> create(@NotNull Gradle gradle, @NotNull MessageReporter messageReporter) {
      return new ConcurrentHashMap<String, Set<File>>()
    }
  }

  private ArtifactsMap myArtifactsMap
  private ConcurrentMap<String, Set<File>> mySourcesMap

  SourceSetCachedFinder(@NotNull ModelBuilderContext context) {
    init(context)
  }

  private void init(@NotNull ModelBuilderContext context) {
    myArtifactsMap = context.getData(ARTIFACTS_PROVIDER)
    mySourcesMap = context.getData(SOURCES_DATA_KEY)
  }

  Set<File> findSourcesByArtifact(String path) {
    def sources = mySourcesMap.get(path)
    if (sources == null) {
      def sourceSet = myArtifactsMap.myArtifactsMap[path]
      if (sourceSet != null) {
        sources = sourceSet.getAllJava().getSrcDirs()
        def calculatedSources = mySourcesMap.putIfAbsent(path, sources)
        return calculatedSources != null ? calculatedSources : sources
      } else {
        return null
      }
    }
  }

  SourceSet findByArtifact(String artifactPath) {
    myArtifactsMap.myArtifactsMap[artifactPath]
  }

  String findArtifactBySourceSetOutputDir(String outputPath) {
    myArtifactsMap.mySourceSetOutputDirsToArtifactsMap[outputPath]
  }

  private static ArtifactsMap createArtifactsMap(@NotNull Gradle gradle) {
    def artifactsMap = new HashMap<String, SourceSet>()
    def sourceSetOutputDirsToArtifactsMap = new HashMap<String, String>()
    def projects = new ArrayList<Project>(gradle.rootProject.allprojects)
    def isCompositeBuildsSupported = isIsNewDependencyResolutionApplicable() ||
                                     GradleVersion.current().baseVersion >= GradleVersion.version("3.1")
    if (isCompositeBuildsSupported) {
      projects = exposeIncludedBuilds(gradle, projects)
    }
    for (Project p : projects) {
      SourceSetContainer sourceSetContainer = JavaPluginUtil.getSourceSetContainer(p)
      if (sourceSetContainer == null || sourceSetContainer.isEmpty()) continue

      for (SourceSet sourceSet : sourceSetContainer) {
        def task = p.tasks.findByName(sourceSet.getJarTaskName())
        if (task instanceof AbstractArchiveTask) {
          AbstractArchiveTask jarTask = (AbstractArchiveTask)task
          def archivePath = is51OrBetter ? ReflectionUtil.reflectiveGetProperty(jarTask, "getArchiveFile", RegularFile).asFile : jarTask?.getArchivePath()
          if (archivePath) {
            artifactsMap[archivePath.path] = sourceSet
            if (isIsNewDependencyResolutionApplicable()) {
              for (File file : sourceSet.output.classesDirs.files) {
                sourceSetOutputDirsToArtifactsMap[file.path] = archivePath.path
              }
              sourceSetOutputDirsToArtifactsMap[sourceSet.output.resourcesDir.path] = archivePath.path
            }
          }
        }
      }
    }
    return new ArtifactsMap(artifactsMap, sourceSetOutputDirsToArtifactsMap)
  }

  private static List<Project> exposeIncludedBuilds(Gradle gradle, List<Project> projects) {
    for (IncludedBuild includedBuild : gradle.includedBuilds) {
      def unwrapped = maybeUnwrapIncludedBuildInternal(includedBuild)
      if (unwrapped instanceof DefaultIncludedBuild) {
        def build = unwrapped as DefaultIncludedBuild
        if (is51OrBetter) {
          projects += build.withState { it.rootProject.allprojects  }
        } else {
          projects += getProjectsWithReflection(build)
        }
      }
    }
    return projects
  }

  private static Set<Project> getProjectsWithReflection(DefaultIncludedBuild build) {
    def method = build.class.getMethod("getConfiguredBuild")
    GradleInternal gradleInternal = (GradleInternal)method.invoke(build)
    return gradleInternal.rootProject.allprojects
  }

  private static Object maybeUnwrapIncludedBuildInternal(IncludedBuild includedBuild) {
    def wrapee = includedBuild
    Class includedBuildInternalClass = null
    try {
      includedBuildInternalClass = Class.forName("org.gradle.internal.composite.IncludedBuildInternal");
    }
    catch (ClassNotFoundException ignored) {
    }
    if (includedBuildInternalClass != null &&
        includedBuildInternalClass.isAssignableFrom(includedBuild.class)) {
      def method = includedBuild.class.getMethod("getTarget")
      wrapee = method.invoke(includedBuild)
    }
    wrapee
  }

  private static class ArtifactsMap {
    private final Map<String, SourceSet> myArtifactsMap
    private final Map<String, String> mySourceSetOutputDirsToArtifactsMap

    ArtifactsMap(Map<String, SourceSet> artifactsMap, Map<String, String> sourceSetOutputDirsToArtifactsMap) {
      myArtifactsMap = unmodifiableMap(artifactsMap)
      mySourceSetOutputDirsToArtifactsMap = unmodifiableMap(sourceSetOutputDirsToArtifactsMap)
    }
  }
}

