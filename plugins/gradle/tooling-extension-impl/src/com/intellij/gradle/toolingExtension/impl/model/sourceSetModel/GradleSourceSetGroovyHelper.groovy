// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel

import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleSourceSetDependencyResolver
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import groovy.transform.CompileDynamic
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.util.StringUtils

class GradleSourceSetGroovyHelper {

  @NotNull
  @CompileDynamic
  static Map<String, DefaultExternalSourceSet> getSourceSets(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    def resolveSourceSetDependencies = System.properties.'idea.resolveSourceSetDependencies' as boolean

    def sourceSetResolutionContext = new GradleSourceSetResolutionContext(project, context)

    def result = new LinkedHashMap<String, DefaultExternalSourceSet>()
    def sourceSets = JavaPluginUtil.getSourceSetContainer(project)
    if (sourceSets == null) {
      return result
    }

    sourceSets.each { SourceSet sourceSet ->
      ExternalSourceSet externalSourceSet = new DefaultExternalSourceSet()
      externalSourceSet.name = sourceSet.name
      externalSourceSet.artifacts = GradleSourceSetModelBuilder.collectSourceSetArtifacts(project, context, sourceSet)
      GradleSourceSetModelBuilder.addJavaCompilerOptions(externalSourceSet, project, sourceSet, sourceSetResolutionContext)
      externalSourceSet.sources = collectSourceDirs(project, sourceSet, sourceSetResolutionContext)

      cleanupSharedIdeaSourceDirs(externalSourceSet, sourceSetResolutionContext)

      if (resolveSourceSetDependencies) {
        def dependencies = new GradleSourceSetDependencyResolver(context, project)
          .resolveDependencies(sourceSet)
        externalSourceSet.setDependencies(dependencies)
      }

      result[sourceSet.name] = externalSourceSet
    }

    GradleSourceSetModelBuilder.addUnprocessedIdeaSourceDirs(result, sourceSets, sourceSetResolutionContext, SourceSet.MAIN_SOURCE_SET_NAME)
    GradleSourceSetModelBuilder.addUnprocessedIdeaResourceDirs(result, sourceSetResolutionContext, SourceSet.MAIN_SOURCE_SET_NAME)
    GradleSourceSetModelBuilder.addUnprocessedIdeaGeneratedSourcesDirs(result, sourceSetResolutionContext, SourceSet.MAIN_SOURCE_SET_NAME)

    GradleSourceSetModelBuilder.addUnprocessedIdeaSourceDirs(result, sourceSets, sourceSetResolutionContext, SourceSet.TEST_SOURCE_SET_NAME)
    GradleSourceSetModelBuilder.addUnprocessedIdeaResourceDirs(result, sourceSetResolutionContext, SourceSet.TEST_SOURCE_SET_NAME)
    GradleSourceSetModelBuilder.addUnprocessedIdeaGeneratedSourcesDirs(result, sourceSetResolutionContext, SourceSet.TEST_SOURCE_SET_NAME)


    GradleSourceSetModelBuilder.cleanupSharedSourceDirs(result, SourceSet.MAIN_SOURCE_SET_NAME, null)
    GradleSourceSetModelBuilder.cleanupSharedSourceDirs(result, SourceSet.TEST_SOURCE_SET_NAME, SourceSet.MAIN_SOURCE_SET_NAME)

    return result
  }

  @NotNull
  private static Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> collectSourceDirs(
    @NotNull Project project,
    @NotNull SourceSet sourceSet,
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext
  ) {
    def resolveSourceSetDependencies = System.properties.'idea.resolveSourceSetDependencies' as boolean

    def sources = [:] as Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet>

    ExternalSourceDirectorySet sourceDirectorySet = new DefaultExternalSourceDirectorySet()
    sourceDirectorySet.name = sourceSet.allJava.name
    sourceDirectorySet.srcDirs = sourceSet.allJava.srcDirs
    sourceDirectorySet.gradleOutputDirs = sourceSet.output.classesDirs.files
    if (sourceDirectorySet.gradleOutputDirs.isEmpty()) {
      sourceDirectorySet.gradleOutputDirs = Collections.singleton(GradleProjectUtil.getBuildDirectory(project))
    }
    sourceDirectorySet.inheritedCompilerOutput = sourceSetResolutionContext.isIdeaInheritOutputDirs

    ExternalSourceDirectorySet resourcesDirectorySet = new DefaultExternalSourceDirectorySet()
    resourcesDirectorySet.name = sourceSet.resources.name
    resourcesDirectorySet.srcDirs = sourceSet.resources.srcDirs
    if (sourceSet.output.resourcesDir != null) {
      resourcesDirectorySet.gradleOutputDirs = Collections.singleton(sourceSet.output.resourcesDir)
    }
    if (resourcesDirectorySet.gradleOutputDirs.isEmpty()) {
      resourcesDirectorySet.gradleOutputDirs = sourceDirectorySet.gradleOutputDirs
    }
    resourcesDirectorySet.inheritedCompilerOutput = sourceSetResolutionContext.isIdeaInheritOutputDirs

    DefaultExternalSourceDirectorySet generatedSourceDirectorySet = null
    def generatedSourceDirs = sourceDirectorySet.srcDirs.intersect(sourceSetResolutionContext.ideaGeneratedSourceDirs)
    if (!generatedSourceDirs.isEmpty()) {
      sourceDirectorySet.srcDirs.removeAll(generatedSourceDirs)
      sourceSetResolutionContext.unprocessedIdeaGeneratedSourceDirs.removeAll(generatedSourceDirs)

      generatedSourceDirectorySet = new DefaultExternalSourceDirectorySet()
      generatedSourceDirectorySet.name = "generated " + sourceDirectorySet.name
      generatedSourceDirectorySet.srcDirs = generatedSourceDirs
      generatedSourceDirectorySet.gradleOutputDirs = sourceDirectorySet.gradleOutputDirs
      generatedSourceDirectorySet.inheritedCompilerOutput = sourceDirectorySet.isCompilerOutputPathInherited()
    }

    boolean isIdeaTestSourceSet = sourceSetResolutionContext.ideaTestSourceDirs.containsAll(sourceDirectorySet.srcDirs)
    boolean isKnownTestSourceSet = sourceSetResolutionContext.testSourceSets.contains(sourceSet)
    boolean isCustomTestSourceSet = (isIdeaTestSourceSet || isKnownTestSourceSet) && SourceSet.MAIN_SOURCE_SET_NAME != sourceSet.name
    if (SourceSet.TEST_SOURCE_SET_NAME == sourceSet.name || resolveSourceSetDependencies && isCustomTestSourceSet) {
      if (!sourceSetResolutionContext.isIdeaInheritOutputDirs && sourceSetResolutionContext.ideaTestOutputDir != null) {
        sourceDirectorySet.outputDir = sourceSetResolutionContext.ideaTestOutputDir
        resourcesDirectorySet.outputDir = sourceSetResolutionContext.ideaTestOutputDir
      }
      else {
        sourceDirectorySet.outputDir = new File(project.projectDir, "out/test/classes")
        resourcesDirectorySet.outputDir = new File(project.projectDir, "out/test/resources")
      }
      if (generatedSourceDirectorySet != null) {
        generatedSourceDirectorySet.outputDir = sourceDirectorySet.outputDir
      }

      resourcesDirectorySet.excludes = sourceSetResolutionContext.testResourcesExcludes + sourceSet.resources.excludes
      resourcesDirectorySet.includes = sourceSetResolutionContext.testResourcesIncludes + sourceSet.resources.includes
      resourcesDirectorySet.filters = sourceSetResolutionContext.testResourceFilters

      sources.put(ExternalSystemSourceType.TEST, sourceDirectorySet)
      sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet)
      if (generatedSourceDirectorySet != null) {
        sources.put(ExternalSystemSourceType.TEST_GENERATED, generatedSourceDirectorySet)
      }
    }
    else {
      if (!sourceSetResolutionContext.isIdeaInheritOutputDirs && sourceSetResolutionContext.ideaOutputDir != null) {
        sourceDirectorySet.outputDir = sourceSetResolutionContext.ideaOutputDir
        resourcesDirectorySet.outputDir = sourceSetResolutionContext.ideaOutputDir
      }
      else if (SourceSet.MAIN_SOURCE_SET_NAME == sourceSet.name || !resolveSourceSetDependencies) {
        sourceDirectorySet.outputDir = new File(project.projectDir, "out/production/classes")
        resourcesDirectorySet.outputDir = new File(project.projectDir, "out/production/resources")
      }
      else {
        def outputName = StringUtils.toCamelCase(sourceSet.name, true)
        sourceDirectorySet.outputDir = new File(project.projectDir, "out/$outputName/classes")
        resourcesDirectorySet.outputDir = new File(project.projectDir, "out/$outputName/resources")
      }
      if (generatedSourceDirectorySet != null) {
        generatedSourceDirectorySet.outputDir = sourceDirectorySet.outputDir
      }

      resourcesDirectorySet.excludes = sourceSetResolutionContext.resourcesExcludes + sourceSet.resources.excludes
      resourcesDirectorySet.includes = sourceSetResolutionContext.resourcesIncludes + sourceSet.resources.includes
      resourcesDirectorySet.filters = sourceSetResolutionContext.resourceFilters

      sources.put(ExternalSystemSourceType.SOURCE, sourceDirectorySet)
      sources.put(ExternalSystemSourceType.RESOURCE, resourcesDirectorySet)
      if (generatedSourceDirectorySet != null) {
        sources.put(ExternalSystemSourceType.SOURCE_GENERATED, generatedSourceDirectorySet)
      }

      if (!resolveSourceSetDependencies) {
        def testSourceDirs = sourceDirectorySet.srcDirs.intersect(sourceSetResolutionContext.ideaTestSourceDirs)
        if (!testSourceDirs.isEmpty()) {
          sourceDirectorySet.srcDirs.removeAll(sourceSetResolutionContext.ideaTestSourceDirs)

          def testSourceDirectorySet = new DefaultExternalSourceDirectorySet()
          testSourceDirectorySet.name = sourceDirectorySet.name
          testSourceDirectorySet.srcDirs = testSourceDirs
          testSourceDirectorySet.gradleOutputDirs = Collections.singleton(sourceDirectorySet.outputDir)
          testSourceDirectorySet.outputDir = sourceSetResolutionContext.ideaTestOutputDir
            ?: new File(project.projectDir, "out/test/classes")
          testSourceDirectorySet.inheritedCompilerOutput = sourceDirectorySet.isCompilerOutputPathInherited()
          sources.put(ExternalSystemSourceType.TEST, testSourceDirectorySet)
        }

        def testResourceDirs = resourcesDirectorySet.srcDirs.intersect(sourceSetResolutionContext.ideaTestSourceDirs)
        if (!testResourceDirs.isEmpty()) {
          resourcesDirectorySet.srcDirs.removeAll(sourceSetResolutionContext.ideaTestSourceDirs)

          def testResourcesDirectorySet = new DefaultExternalSourceDirectorySet()
          testResourcesDirectorySet.name = resourcesDirectorySet.name
          testResourcesDirectorySet.srcDirs = testResourceDirs
          testResourcesDirectorySet.gradleOutputDirs = Collections.singleton(resourcesDirectorySet.outputDir)
          testResourcesDirectorySet.outputDir = sourceSetResolutionContext.ideaTestOutputDir
            ?: new File(project.projectDir, "out/test/resources")
          testResourcesDirectorySet.inheritedCompilerOutput = resourcesDirectorySet.isCompilerOutputPathInherited()
          sources.put(ExternalSystemSourceType.TEST_RESOURCE, testResourcesDirectorySet)
        }

        if (generatedSourceDirectorySet != null) {
          def testGeneratedSourceDirs = generatedSourceDirectorySet.srcDirs.intersect(sourceSetResolutionContext.ideaTestSourceDirs)
          if (!testGeneratedSourceDirs.isEmpty()) {
            generatedSourceDirectorySet.srcDirs.removeAll(sourceSetResolutionContext.ideaTestSourceDirs)

            def testGeneratedDirectorySet = new DefaultExternalSourceDirectorySet()
            testGeneratedDirectorySet.name = generatedSourceDirectorySet.name
            testGeneratedDirectorySet.srcDirs = testGeneratedSourceDirs
            testGeneratedDirectorySet.gradleOutputDirs = Collections.singleton(generatedSourceDirectorySet.outputDir)
            testGeneratedDirectorySet.outputDir = generatedSourceDirectorySet.outputDir
            testGeneratedDirectorySet.inheritedCompilerOutput = generatedSourceDirectorySet.isCompilerOutputPathInherited()

            sources.put(ExternalSystemSourceType.TEST_GENERATED, testGeneratedDirectorySet)
          }
        }
      }
    }

    return sources
  }

  private static void cleanupSharedIdeaSourceDirs(
    @NotNull DefaultExternalSourceSet externalSourceSet, // mutable
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext
  ) {
    if (SourceSet.MAIN_SOURCE_SET_NAME != externalSourceSet.name && SourceSet.TEST_SOURCE_SET_NAME != externalSourceSet.name) {
      for (sourceDirectorySet in externalSourceSet.sources.values()) {
        sourceSetResolutionContext.ideaSourceDirs.removeAll(sourceDirectorySet.srcDirs)
        sourceSetResolutionContext.ideaResourceDirs.removeAll(sourceDirectorySet.srcDirs)
        sourceSetResolutionContext.ideaTestSourceDirs.removeAll(sourceDirectorySet.srcDirs)
        sourceSetResolutionContext.ideaTestResourceDirs.removeAll(sourceDirectorySet.srcDirs)
      }
    }
  }
}
