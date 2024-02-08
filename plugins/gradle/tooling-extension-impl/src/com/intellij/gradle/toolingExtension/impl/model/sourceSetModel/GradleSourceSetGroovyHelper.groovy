// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel

import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleSourceSetDependencyResolver
import com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel.GradleResourceFilterModelBuilder
import com.intellij.gradle.toolingExtension.impl.util.GradleIdeaPluginUtil
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import groovy.transform.CompileDynamic
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.internal.JavaToolchain
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

import static com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetModelBuilder.containsAllSourceSetOutput
import static com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFile
import static org.jetbrains.plugins.gradle.tooling.util.StringUtils.toCamelCase

class GradleSourceSetGroovyHelper {

  private static final boolean is67OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("6.7")
  private static final boolean is80OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("8.0")

  @NotNull
  @CompileDynamic
  static Map<String, DefaultExternalSourceSet> getSourceSets(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    def resolveSourceSetDependencies = System.properties.'idea.resolveSourceSetDependencies' as boolean
    def ideaPluginModule = GradleIdeaPluginUtil.getIdeaModule(project)
    boolean inheritOutputDirs = ideaPluginModule?.inheritOutputDirs ?: false
    def ideaPluginOutDir = ideaPluginModule?.outputDir
    def ideaPluginTestOutDir = ideaPluginModule?.testOutputDir

    def sourceSetResolutionContext = new GradleSourceSetResolutionContext(project, ideaPluginModule)

    def projectSourceCompatibility = JavaPluginUtil.getSourceCompatibility(project)
    def projectTargetCompatibility = JavaPluginUtil.getTargetCompatibility(project)

    def result = new LinkedHashMap<String, DefaultExternalSourceSet>()
    def sourceSets = JavaPluginUtil.getSourceSetContainer(project)
    if (sourceSets == null) {
      return result
    }

    def (resourcesIncludes, resourcesExcludes, filterReaders) = GradleResourceFilterModelBuilder.getFilters(project, context, 'processResources')
    def (testResourcesIncludes, testResourcesExcludes, testFilterReaders) = GradleResourceFilterModelBuilder.getFilters(project, context, 'processTestResources')
    //def (javaIncludes, javaExcludes) = GradleResourceFilterModelBuilder.getFilters(project, 'compileJava')

    sourceSets.each { SourceSet sourceSet ->
      ExternalSourceSet externalSourceSet = new DefaultExternalSourceSet()
      externalSourceSet.name = sourceSet.name

      def javaCompileTask = project.tasks.findByName(sourceSet.compileJavaTaskName)
      if (javaCompileTask instanceof JavaCompile) {
        if (is67OrBetter) {
          def compiler = javaCompileTask.javaCompiler
          if (compiler.present) {
            try {
              def metadata = compiler.get().metadata
              def configuredInstallationPath = metadata.installationPath.asFile.canonicalPath
              boolean isFallbackToolchain =
                is80OrBetter && metadata instanceof JavaToolchain && ((JavaToolchain)metadata).isFallbackToolchain()
              if (!isFallbackToolchain) {
                externalSourceSet.jdkInstallationPath = configuredInstallationPath
              }
            } catch (Throwable e) {
              project.logger.warn("Skipping java toolchain information for $javaCompileTask.path : $e.message")
              project.logger.info("Failed to resolve java toolchain info for $javaCompileTask.path", e)
            }
          }
        }
        externalSourceSet.sourceCompatibility = javaCompileTask.sourceCompatibility ?: projectSourceCompatibility
        externalSourceSet.preview = javaCompileTask.options.compilerArgs.contains("--enable-preview")
        externalSourceSet.targetCompatibility = javaCompileTask.targetCompatibility ?: projectTargetCompatibility
      }
      else {
        externalSourceSet.sourceCompatibility = projectSourceCompatibility
        externalSourceSet.targetCompatibility = projectTargetCompatibility
      }

      project.tasks.withType(AbstractArchiveTask) { AbstractArchiveTask task ->
        if (containsAllSourceSetOutput(task, sourceSet)) {
          externalSourceSet.artifacts.add(getTaskArchiveFile(task))
        }
      }


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

      def ideaOutDir = new File(project.projectDir, "out/" + (SourceSet.MAIN_SOURCE_SET_NAME == sourceSet.name ||
                                                              (!resolveSourceSetDependencies && SourceSet.TEST_SOURCE_SET_NAME !=
                                                               sourceSet.name) ? "production" : toCamelCase(sourceSet.name, true)))
      resourcesDirectorySet.outputDir = new File(ideaOutDir, "resources")
      resourcesDirectorySet.inheritedCompilerOutput = inheritOutputDirs

      ExternalSourceDirectorySet javaDirectorySet = new DefaultExternalSourceDirectorySet()
      javaDirectorySet.name = sourceSet.allJava.name
      javaDirectorySet.srcDirs = sourceSet.allJava.srcDirs
      for (File outDir : sourceSet.output.classesDirs.files) {
        javaDirectorySet.addGradleOutputDir(outDir)
      }
      if (javaDirectorySet.gradleOutputDirs.isEmpty()) {
        javaDirectorySet.addGradleOutputDir(GradleProjectUtil.getBuildDirectory(project))
      }

      javaDirectorySet.outputDir = new File(ideaOutDir, "classes")
      javaDirectorySet.inheritedCompilerOutput = inheritOutputDirs
//      javaDirectorySet.excludes = javaExcludes + sourceSet.java.excludes;
//      javaDirectorySet.includes = javaIncludes + sourceSet.java.includes;

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
          generatedDirectorySet.outputDir = javaDirectorySet.outputDir
          generatedDirectorySet.inheritedCompilerOutput = javaDirectorySet.isCompilerOutputPathInherited()
        }
        sourceSetResolutionContext.unprocessedIdeaGeneratedSourceDirs.removeAll(files)
      }

      if (SourceSet.TEST_SOURCE_SET_NAME == sourceSet.name) {
        if (!inheritOutputDirs && ideaPluginTestOutDir != null) {
          javaDirectorySet.outputDir = ideaPluginTestOutDir
          resourcesDirectorySet.outputDir = ideaPluginTestOutDir
        }
        resourcesDirectorySet.excludes = testResourcesExcludes + sourceSet.resources.excludes
        resourcesDirectorySet.includes = testResourcesIncludes + sourceSet.resources.includes
        resourcesDirectorySet.filters = testFilterReaders
        sources.put(ExternalSystemSourceType.TEST, javaDirectorySet)
        sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet)
        if (generatedDirectorySet) {
          sources.put(ExternalSystemSourceType.TEST_GENERATED, generatedDirectorySet)
        }
      }
      else {
        boolean isTestSourceSet = false
        boolean explicitlyMarkedAsTests = sourceSetResolutionContext.ideaTestSourceDirs.containsAll(javaDirectorySet.srcDirs)
        boolean knownTestSourceSet = sourceSetResolutionContext.testSourceSets.contains(sourceSet)
        if (!inheritOutputDirs && resolveSourceSetDependencies && SourceSet.MAIN_SOURCE_SET_NAME != sourceSet.name
          && (explicitlyMarkedAsTests || knownTestSourceSet)) {
          javaDirectorySet.outputDir = ideaPluginTestOutDir ?: new File(project.projectDir, "out/test/classes")
          resourcesDirectorySet.outputDir = ideaPluginTestOutDir ?: new File(project.projectDir, "out/test/resources")
          sources.put(ExternalSystemSourceType.TEST, javaDirectorySet)
          sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet)
          isTestSourceSet = true
        }
        else if (!inheritOutputDirs && ideaPluginOutDir != null) {
          javaDirectorySet.outputDir = ideaPluginOutDir
          resourcesDirectorySet.outputDir = ideaPluginOutDir
        }

        resourcesDirectorySet.excludes = resourcesExcludes + sourceSet.resources.excludes
        resourcesDirectorySet.includes = resourcesIncludes + sourceSet.resources.includes
        resourcesDirectorySet.filters = filterReaders
        if (!isTestSourceSet) {
          sources.put(ExternalSystemSourceType.SOURCE, javaDirectorySet)
          sources.put(ExternalSystemSourceType.RESOURCE, resourcesDirectorySet)
        }

        if (!resolveSourceSetDependencies) {
          def testDirs = javaDirectorySet.srcDirs.intersect(sourceSetResolutionContext.ideaTestSourceDirs)
          if (!testDirs.isEmpty()) {
            javaDirectorySet.srcDirs.removeAll(sourceSetResolutionContext.ideaTestSourceDirs)

            def testDirectorySet = new DefaultExternalSourceDirectorySet()
            testDirectorySet.name = javaDirectorySet.name
            testDirectorySet.srcDirs = testDirs
            testDirectorySet.addGradleOutputDir(javaDirectorySet.outputDir)
            testDirectorySet.outputDir = ideaPluginTestOutDir ?: new File(project.projectDir, "out/test/classes")
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
            testResourcesDirectorySet.outputDir = ideaPluginTestOutDir ?: new File(project.projectDir, "out/test/resources")
            testResourcesDirectorySet.inheritedCompilerOutput = resourcesDirectorySet.isCompilerOutputPathInherited()
            sources.put(ExternalSystemSourceType.TEST_RESOURCE, testResourcesDirectorySet)
          }
        }

        if (generatedDirectorySet) {
          sources.put(ExternalSystemSourceType.SOURCE_GENERATED, generatedDirectorySet)
          if (!resolveSourceSetDependencies) {
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

        if (SourceSet.MAIN_SOURCE_SET_NAME != sourceSet.name && SourceSet.TEST_SOURCE_SET_NAME != sourceSet.name) {
          for (sourceDirectorySet in sources.values()) {
            sourceSetResolutionContext.ideaSourceDirs.removeAll(sourceDirectorySet.srcDirs)
            sourceSetResolutionContext.ideaResourceDirs.removeAll(sourceDirectorySet.srcDirs)
            sourceSetResolutionContext.ideaTestSourceDirs.removeAll(sourceDirectorySet.srcDirs)
            sourceSetResolutionContext.ideaTestResourceDirs.removeAll(sourceDirectorySet.srcDirs)
          }
        }
      }

      if (resolveSourceSetDependencies) {
        def dependencies = new GradleSourceSetDependencyResolver(context, project)
          .resolveDependencies(sourceSet)
        externalSourceSet.setDependencies(dependencies)
      }

      externalSourceSet.sources = sources
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

    result
  }
}
