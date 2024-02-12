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

    ExternalSourceDirectorySet resourcesDirectorySet = new DefaultExternalSourceDirectorySet()
    resourcesDirectorySet.name = sourceSet.resources.name
    resourcesDirectorySet.srcDirs = sourceSet.resources.srcDirs
    if (sourceSet.output.resourcesDir) {
      resourcesDirectorySet.addGradleOutputDir(sourceSet.output.resourcesDir)
    }
    else {
      for (File outDir : sourceSet.output.classesDirs.files) {
        resourcesDirectorySet.addGradleOutputDir(outDir)
      }
      if (resourcesDirectorySet.gradleOutputDirs.isEmpty()) {
        resourcesDirectorySet.addGradleOutputDir(GradleProjectUtil.getBuildDirectory(project))
      }
    }
    resourcesDirectorySet.inheritedCompilerOutput = sourceSetResolutionContext.isIdeaInheritOutputDirs

    ExternalSourceDirectorySet javaDirectorySet = new DefaultExternalSourceDirectorySet()
    javaDirectorySet.name = sourceSet.allJava.name
    javaDirectorySet.srcDirs = sourceSet.allJava.srcDirs
    for (File outDir : sourceSet.output.classesDirs.files) {
      javaDirectorySet.addGradleOutputDir(outDir)
    }
    if (javaDirectorySet.gradleOutputDirs.isEmpty()) {
      javaDirectorySet.addGradleOutputDir(GradleProjectUtil.getBuildDirectory(project))
    }
    javaDirectorySet.inheritedCompilerOutput = sourceSetResolutionContext.isIdeaInheritOutputDirs

    DefaultExternalSourceDirectorySet generatedDirectorySet = null
    def hasExplicitlyDefinedGeneratedSources = !sourceSetResolutionContext.ideaGeneratedSourceDirs.isEmpty()
    if (hasExplicitlyDefinedGeneratedSources) {

      def files = new HashSet<File>()
      for (File file : sourceSetResolutionContext.ideaGeneratedSourceDirs) {
        if (javaDirectorySet.srcDirs.contains(file)) {
          files.add(file)
        }
      }

      if (!files.isEmpty()) {
        javaDirectorySet.srcDirs.removeAll(files)
        generatedDirectorySet = new DefaultExternalSourceDirectorySet()
        generatedDirectorySet.name = "generated " + javaDirectorySet.name
        generatedDirectorySet.srcDirs = files
        for (file in javaDirectorySet.gradleOutputDirs) {
          generatedDirectorySet.addGradleOutputDir(file)
        }
        generatedDirectorySet.inheritedCompilerOutput = javaDirectorySet.isCompilerOutputPathInherited()
      }
      sourceSetResolutionContext.unprocessedIdeaGeneratedSourceDirs.removeAll(files)
    }

    boolean isIdeaTestSourceSet = sourceSetResolutionContext.ideaTestSourceDirs.containsAll(javaDirectorySet.srcDirs)
    boolean isKnownTestSourceSet = sourceSetResolutionContext.testSourceSets.contains(sourceSet)
    boolean isCustomTestSourceSet = (isIdeaTestSourceSet || isKnownTestSourceSet) &&
                                    SourceSet.MAIN_SOURCE_SET_NAME != sourceSet.name
    if (SourceSet.TEST_SOURCE_SET_NAME == sourceSet.name || resolveSourceSetDependencies && isCustomTestSourceSet) {
      if (!sourceSetResolutionContext.isIdeaInheritOutputDirs && sourceSetResolutionContext.ideaTestOutputDir != null) {
        javaDirectorySet.outputDir = sourceSetResolutionContext.ideaTestOutputDir
        resourcesDirectorySet.outputDir = sourceSetResolutionContext.ideaTestOutputDir
      }
      else {
        javaDirectorySet.outputDir = new File(project.projectDir, "out/test/classes")
        resourcesDirectorySet.outputDir = new File(project.projectDir, "out/test/resources")
      }
      if (generatedDirectorySet != null) {
        generatedDirectorySet.outputDir = javaDirectorySet.outputDir
      }

      resourcesDirectorySet.excludes = sourceSetResolutionContext.testResourcesExcludes + sourceSet.resources.excludes
      resourcesDirectorySet.includes = sourceSetResolutionContext.testResourcesIncludes + sourceSet.resources.includes
      resourcesDirectorySet.filters = sourceSetResolutionContext.testResourceFilters

      sources.put(ExternalSystemSourceType.TEST, javaDirectorySet)
      sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet)
      if (generatedDirectorySet != null) {
        sources.put(ExternalSystemSourceType.TEST_GENERATED, generatedDirectorySet)
      }
    }
    else {
      if (!sourceSetResolutionContext.isIdeaInheritOutputDirs && sourceSetResolutionContext.ideaOutputDir != null) {
        javaDirectorySet.outputDir = sourceSetResolutionContext.ideaOutputDir
        resourcesDirectorySet.outputDir = sourceSetResolutionContext.ideaOutputDir
      }
      else if (SourceSet.MAIN_SOURCE_SET_NAME == sourceSet.name || !resolveSourceSetDependencies) {
        javaDirectorySet.outputDir = new File(project.projectDir, "out/production/classes")
        resourcesDirectorySet.outputDir = new File(project.projectDir, "out/production/resources")
      }
      else {
        def outputName = StringUtils.toCamelCase(sourceSet.name, true)
        javaDirectorySet.outputDir = new File(project.projectDir, "out/$outputName/classes")
        resourcesDirectorySet.outputDir = new File(project.projectDir, "out/$outputName/resources")
      }
      if (generatedDirectorySet != null) {
        generatedDirectorySet.outputDir = javaDirectorySet.outputDir
      }

      resourcesDirectorySet.excludes = sourceSetResolutionContext.resourcesExcludes + sourceSet.resources.excludes
      resourcesDirectorySet.includes = sourceSetResolutionContext.resourcesIncludes + sourceSet.resources.includes
      resourcesDirectorySet.filters = sourceSetResolutionContext.resourceFilters

      sources.put(ExternalSystemSourceType.SOURCE, javaDirectorySet)
      sources.put(ExternalSystemSourceType.RESOURCE, resourcesDirectorySet)
      if (generatedDirectorySet != null) {
        sources.put(ExternalSystemSourceType.SOURCE_GENERATED, generatedDirectorySet)
      }

      if (!resolveSourceSetDependencies) {
        def testDirs = javaDirectorySet.srcDirs.intersect(sourceSetResolutionContext.ideaTestSourceDirs)
        if (!testDirs.isEmpty()) {
          javaDirectorySet.srcDirs.removeAll(sourceSetResolutionContext.ideaTestSourceDirs)

          def testDirectorySet = new DefaultExternalSourceDirectorySet()
          testDirectorySet.name = javaDirectorySet.name
          testDirectorySet.srcDirs = testDirs
          testDirectorySet.addGradleOutputDir(javaDirectorySet.outputDir)
          testDirectorySet.outputDir = sourceSetResolutionContext.ideaTestOutputDir
            ?: new File(project.projectDir, "out/test/classes")
          testDirectorySet.inheritedCompilerOutput = javaDirectorySet.isCompilerOutputPathInherited()
          sources.put(ExternalSystemSourceType.TEST, testDirectorySet)
        }

        def testResourcesDirs = resourcesDirectorySet.srcDirs.intersect(sourceSetResolutionContext.ideaTestSourceDirs)
        if (!testResourcesDirs.isEmpty()) {
          resourcesDirectorySet.srcDirs.removeAll(sourceSetResolutionContext.ideaTestSourceDirs)

          def testResourcesDirectorySet = new DefaultExternalSourceDirectorySet()
          testResourcesDirectorySet.name = resourcesDirectorySet.name
          testResourcesDirectorySet.srcDirs = testResourcesDirs
          testResourcesDirectorySet.addGradleOutputDir(resourcesDirectorySet.outputDir)
          testResourcesDirectorySet.outputDir = sourceSetResolutionContext.ideaTestOutputDir
            ?: new File(project.projectDir, "out/test/resources")
          testResourcesDirectorySet.inheritedCompilerOutput = resourcesDirectorySet.isCompilerOutputPathInherited()
          sources.put(ExternalSystemSourceType.TEST_RESOURCE, testResourcesDirectorySet)
        }

        if (generatedDirectorySet != null) {
          def testGeneratedDirs = generatedDirectorySet.srcDirs.intersect(sourceSetResolutionContext.ideaTestSourceDirs)
          if (!testGeneratedDirs.isEmpty()) {
            generatedDirectorySet.srcDirs.removeAll(sourceSetResolutionContext.ideaTestSourceDirs)

            def testGeneratedDirectorySet = new DefaultExternalSourceDirectorySet()
            testGeneratedDirectorySet.name = generatedDirectorySet.name
            testGeneratedDirectorySet.srcDirs = testGeneratedDirs
            testGeneratedDirectorySet.addGradleOutputDir(generatedDirectorySet.outputDir)
            testGeneratedDirectorySet.outputDir = generatedDirectorySet.outputDir
            testGeneratedDirectorySet.inheritedCompilerOutput = generatedDirectorySet.isCompilerOutputPathInherited()

            sources.put(ExternalSystemSourceType.TEST_GENERATED, testGeneratedDirectorySet)
          }
        }
      }
    }

    if (SourceSet.MAIN_SOURCE_SET_NAME != sourceSet.name && SourceSet.TEST_SOURCE_SET_NAME != sourceSet.name) {
      for (sourceDirectorySet in sources.values()) {
        sourceSetResolutionContext.ideaSourceDirs.removeAll(sourceDirectorySet.srcDirs)
        sourceSetResolutionContext.ideaResourceDirs.removeAll(sourceDirectorySet.srcDirs)
        sourceSetResolutionContext.ideaTestSourceDirs.removeAll(sourceDirectorySet.srcDirs)
        sourceSetResolutionContext.ideaTestResourceDirs.removeAll(sourceDirectorySet.srcDirs)
      }
    }

    return sources
  }
}
