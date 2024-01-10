// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy
import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicyCache
import com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel.GradleResourceFilterModelBuilder
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
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl

import static com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetModelBuilder.cleanupSharedSourceFolders
import static com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetModelBuilder.containsAllSourceSetOutput
import static com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFile
import static org.jetbrains.plugins.gradle.tooling.util.StringUtils.toCamelCase

class GradleSourceSetGroovyHelper {

  private static final boolean is4OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("4.0")
  private static final boolean is67OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("6.7")
  private static final boolean is74OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("7.4")
  private static final boolean is80OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("8.0")

  @NotNull
  @CompileDynamic
  static Map<String, DefaultExternalSourceSet> getSourceSets(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    def resolveSourceSetDependencies = System.properties.'idea.resolveSourceSetDependencies' as boolean
    final IdeaPlugin ideaPlugin = project.getPlugins().findPlugin(IdeaPlugin.class)
    def ideaPluginModule = ideaPlugin?.model?.module
    boolean inheritOutputDirs = ideaPluginModule?.inheritOutputDirs ?: false
    def ideaPluginOutDir = ideaPluginModule?.outputDir
    def ideaPluginTestOutDir = ideaPluginModule?.testOutputDir
    def generatedSourceDirs = null
    def ideaSourceDirs = null
    def ideaResourceDirs = null
    def ideaTestSourceDirs = null
    def ideaTestResourceDirs = null

    GradleDependencyDownloadPolicy dependencyDownloadPolicy = GradleDependencyDownloadPolicyCache.getInstance(context)
      .getDependencyDownloadPolicy(project)

    def testSourceSets = []

    def testingExtensionClass = null
    try {
      testingExtensionClass =
        project.getPlugins().findPlugin("jvm-test-suite")?.getClass()?.classLoader?.loadClass("org.gradle.testing.base.TestingExtension")
    }
    catch (Exception ignore) {
    }

    if (is74OrBetter
      && project.hasProperty("testing")
      && testingExtensionClass != null
      && testingExtensionClass.isAssignableFrom(project.testing.getClass())) {
      testSourceSets = project.testing.suites.collect { it.getSources() }
    }

    if (ideaPluginModule) {
      generatedSourceDirs =
        ideaPluginModule.hasProperty("generatedSourceDirs") ? new LinkedHashSet<>(ideaPluginModule.generatedSourceDirs) : null
      ideaSourceDirs = new LinkedHashSet<>(ideaPluginModule.sourceDirs)
      ideaResourceDirs = ideaPluginModule.hasProperty("resourceDirs") ? new LinkedHashSet<>(ideaPluginModule.resourceDirs) : []
      if (is74OrBetter) {
        ideaTestSourceDirs = new LinkedHashSet<>(ideaPluginModule.testSources.files)
        ideaTestResourceDirs = new LinkedHashSet<>(ideaPluginModule.testResources.files)
      }
      else {
        ideaTestSourceDirs = new LinkedHashSet<>(ideaPluginModule.testSourceDirs)
        ideaTestResourceDirs =
          ideaPluginModule.hasProperty("testResourceDirs") ? new LinkedHashSet<>(ideaPluginModule.testResourceDirs) : []
      }
    }

    def projectSourceCompatibility = JavaPluginUtil.getSourceCompatibility(project)
    def projectTargetCompatibility = JavaPluginUtil.getTargetCompatibility(project)

    def result = new LinkedHashMap<String, DefaultExternalSourceSet>()
    def sourceSets = JavaPluginUtil.getSourceSetContainer(project)
    if (sourceSets == null) {
      return result
    }

    // ignore inherited source sets from parent project
    def parentProjectSourceSets = project.parent == null ? null : JavaPluginUtil.getSourceSetContainer(project.parent)
    if (parentProjectSourceSets && sourceSets.is(parentProjectSourceSets)) {
      return result
    }

    def (resourcesIncludes, resourcesExcludes, filterReaders) = GradleResourceFilterModelBuilder.getFilters(project, context, 'processResources')
    def (testResourcesIncludes, testResourcesExcludes, testFilterReaders) = GradleResourceFilterModelBuilder.getFilters(project, context, 'processTestResources')
    //def (javaIncludes, javaExcludes) = GradleResourceFilterModelBuilder.getFilters(project, 'compileJava')

    def additionalIdeaGenDirs = [] as Collection<File>
    if (generatedSourceDirs && !generatedSourceDirs.isEmpty()) {
      additionalIdeaGenDirs.addAll(generatedSourceDirs)
    }
    def testFixtures = sourceSets.findByName("testFixtures")
    if (testFixtures != null) {
      testSourceSets.add(testFixtures)
    }
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
      if (is4OrBetter) {
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
      }
      else {
        resourcesDirectorySet.addGradleOutputDir(GradleObjectUtil.notNull(
          sourceSet.output.resourcesDir,
          sourceSet.output.classesDir as File,
          GradleProjectUtil.getBuildDirectory(project)
        ))
      }

      def ideaOutDir = new File(project.projectDir, "out/" + (SourceSet.MAIN_SOURCE_SET_NAME == sourceSet.name ||
                                                              (!resolveSourceSetDependencies && SourceSet.TEST_SOURCE_SET_NAME !=
                                                               sourceSet.name) ? "production" : toCamelCase(sourceSet.name, true)))
      resourcesDirectorySet.outputDir = new File(ideaOutDir, "resources")
      resourcesDirectorySet.inheritedCompilerOutput = inheritOutputDirs

      ExternalSourceDirectorySet javaDirectorySet = new DefaultExternalSourceDirectorySet()
      javaDirectorySet.name = sourceSet.allJava.name
      javaDirectorySet.srcDirs = sourceSet.allJava.srcDirs
      if (is4OrBetter) {
        for (File outDir : sourceSet.output.classesDirs.files) {
          javaDirectorySet.addGradleOutputDir(outDir)
        }
        if (javaDirectorySet.gradleOutputDirs.isEmpty()) {
          javaDirectorySet.addGradleOutputDir(GradleProjectUtil.getBuildDirectory(project))
        }
      }
      else {
        javaDirectorySet.addGradleOutputDir(GradleObjectUtil.notNull(
          sourceSet.output.classesDir as File,
          GradleProjectUtil.getBuildDirectory(project)
        ))
      }

      javaDirectorySet.outputDir = new File(ideaOutDir, "classes")
      javaDirectorySet.inheritedCompilerOutput = inheritOutputDirs
//      javaDirectorySet.excludes = javaExcludes + sourceSet.java.excludes;
//      javaDirectorySet.includes = javaIncludes + sourceSet.java.includes;

      DefaultExternalSourceDirectorySet generatedDirectorySet = null
      def hasExplicitlyDefinedGeneratedSources = generatedSourceDirs && !generatedSourceDirs.isEmpty()
      if (hasExplicitlyDefinedGeneratedSources) {

        def files = new HashSet<File>()
        for (File file : generatedSourceDirs) {
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
        additionalIdeaGenDirs.removeAll(files)
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
        boolean explicitlyMarkedAsTests = ideaTestSourceDirs && (ideaTestSourceDirs as Collection).containsAll(javaDirectorySet.srcDirs)
        boolean knownTestingSourceSet = testSourceSets.contains(sourceSet)
        if (!inheritOutputDirs && resolveSourceSetDependencies && SourceSet.MAIN_SOURCE_SET_NAME != sourceSet.name
          && (explicitlyMarkedAsTests || knownTestingSourceSet)) {
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

        if (!resolveSourceSetDependencies && ideaTestSourceDirs) {
          def testDirs = javaDirectorySet.srcDirs.intersect(ideaTestSourceDirs as Collection)
          if (!testDirs.isEmpty()) {
            javaDirectorySet.srcDirs.removeAll(ideaTestSourceDirs)

            def testDirectorySet = new DefaultExternalSourceDirectorySet()
            testDirectorySet.name = javaDirectorySet.name
            testDirectorySet.srcDirs = testDirs
            testDirectorySet.addGradleOutputDir(javaDirectorySet.outputDir)
            testDirectorySet.outputDir = ideaPluginTestOutDir ?: new File(project.projectDir, "out/test/classes")
            testDirectorySet.inheritedCompilerOutput = javaDirectorySet.isCompilerOutputPathInherited()
            sources.put(ExternalSystemSourceType.TEST, testDirectorySet)
          }

          def testResourcesDirs = resourcesDirectorySet.srcDirs.intersect(ideaTestSourceDirs as Collection)
          if (!testResourcesDirs.isEmpty()) {
            resourcesDirectorySet.srcDirs.removeAll(ideaTestSourceDirs)

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
          if (!resolveSourceSetDependencies && ideaTestSourceDirs) {
            def testGeneratedDirs = generatedDirectorySet.srcDirs.intersect(ideaTestSourceDirs as Collection)
            if (!testGeneratedDirs.isEmpty()) {
              generatedDirectorySet.srcDirs.removeAll(ideaTestSourceDirs)

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

        if (ideaPluginModule && SourceSet.MAIN_SOURCE_SET_NAME != sourceSet.name && SourceSet.TEST_SOURCE_SET_NAME != sourceSet.name) {
          for (sourceDirectorySet in sources.values()) {
            ideaSourceDirs.removeAll(sourceDirectorySet.srcDirs)
            ideaResourceDirs.removeAll(sourceDirectorySet.srcDirs)
            ideaTestSourceDirs.removeAll(sourceDirectorySet.srcDirs)
            ideaTestResourceDirs.removeAll(sourceDirectorySet.srcDirs)
          }
        }
      }

      if (resolveSourceSetDependencies) {
        def dependencies = new DependencyResolverImpl(context, project, dependencyDownloadPolicy)
          .resolveDependencies(sourceSet)
        externalSourceSet.dependencies.addAll(dependencies)
      }

      externalSourceSet.sources = sources
      result[sourceSet.name] = externalSourceSet
    }

    def mainSourceSet = result[SourceSet.MAIN_SOURCE_SET_NAME]
    if (ideaPluginModule && mainSourceSet && ideaSourceDirs && !ideaSourceDirs.isEmpty()) {
      def mainGradleSourceSet = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME)
      if (mainGradleSourceSet) {
        def mainSourceDirectorySet = mainSourceSet.sources.get(ExternalSystemSourceType.SOURCE)
        if (mainSourceDirectorySet) {
          mainSourceDirectorySet.srcDirs.addAll(ideaSourceDirs - (mainGradleSourceSet.resources.srcDirs + generatedSourceDirs))
        }
        def mainResourceDirectorySet = mainSourceSet.sources.get(ExternalSystemSourceType.RESOURCE)
        if (mainResourceDirectorySet) {
          mainResourceDirectorySet.srcDirs.addAll(ideaResourceDirs)
        }

        if (!additionalIdeaGenDirs.isEmpty()) {
          def mainAdditionalGenDirs = additionalIdeaGenDirs.intersect(ideaSourceDirs)
          def mainGenSourceDirectorySet = mainSourceSet.sources.get(ExternalSystemSourceType.SOURCE_GENERATED)
          if (mainGenSourceDirectorySet) {
            mainGenSourceDirectorySet.srcDirs.addAll(mainAdditionalGenDirs)
          }
          else {
            def generatedDirectorySet = new DefaultExternalSourceDirectorySet()
            generatedDirectorySet.name = "generated " + mainSourceSet.name
            generatedDirectorySet.srcDirs.addAll(mainAdditionalGenDirs)
            generatedDirectorySet.addGradleOutputDir(mainSourceDirectorySet.outputDir)
            generatedDirectorySet.outputDir = mainSourceDirectorySet.outputDir
            generatedDirectorySet.inheritedCompilerOutput = mainSourceDirectorySet.isCompilerOutputPathInherited()
            mainSourceSet.sources.put(ExternalSystemSourceType.SOURCE_GENERATED, generatedDirectorySet)
          }
        }
      }
    }

    def testSourceSet = result[SourceSet.TEST_SOURCE_SET_NAME]
    if (ideaPluginModule && testSourceSet && ideaTestSourceDirs && !ideaTestSourceDirs.isEmpty()) {
      def testGradleSourceSet = sourceSets.findByName(SourceSet.TEST_SOURCE_SET_NAME)
      if (testGradleSourceSet) {
        def testSourceDirectorySet = testSourceSet.sources.get(ExternalSystemSourceType.TEST)
        if (testSourceDirectorySet) {
          testSourceDirectorySet.srcDirs.addAll(ideaTestSourceDirs - (testGradleSourceSet.resources.srcDirs + generatedSourceDirs))
        }
        def testResourceDirectorySet = testSourceSet.sources.get(ExternalSystemSourceType.TEST_RESOURCE)
        if (testResourceDirectorySet) {
          testResourceDirectorySet.srcDirs.addAll(ideaTestResourceDirs)
        }

        if (!additionalIdeaGenDirs.isEmpty()) {
          def testAdditionalGenDirs = additionalIdeaGenDirs.intersect(ideaTestSourceDirs)
          def testGenSourceDirectorySet = testSourceSet.sources.get(ExternalSystemSourceType.TEST_GENERATED)
          if (testGenSourceDirectorySet) {
            testGenSourceDirectorySet.srcDirs.addAll(testAdditionalGenDirs)
          }
          else {
            def generatedDirectorySet = new DefaultExternalSourceDirectorySet()
            generatedDirectorySet.name = "generated " + testSourceSet.name
            generatedDirectorySet.srcDirs.addAll(testAdditionalGenDirs)
            generatedDirectorySet.addGradleOutputDir(testSourceDirectorySet.outputDir)
            generatedDirectorySet.outputDir = testSourceDirectorySet.outputDir
            generatedDirectorySet.inheritedCompilerOutput = testSourceDirectorySet.isCompilerOutputPathInherited()
            testSourceSet.sources.put(ExternalSystemSourceType.TEST_GENERATED, generatedDirectorySet)
          }
        }
      }
    }

    cleanupSharedSourceFolders(result)

    result
  }
}
