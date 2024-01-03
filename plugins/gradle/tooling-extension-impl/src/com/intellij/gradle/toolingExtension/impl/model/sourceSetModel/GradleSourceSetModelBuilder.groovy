// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy
import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicyCache
import com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel.GradleResourceFilterModelBuilder
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.GradleObjectUtil
import com.intellij.gradle.toolingExtension.impl.util.collectionUtil.GradleCollectionVisitor
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil
import com.intellij.gradle.toolingExtension.util.GradleNegotiationUtil
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import groovy.transform.CompileDynamic
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.internal.JavaToolchain
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Path

import static com.intellij.gradle.toolingExtension.util.GradleNegotiationUtil.getTaskArchiveFile
import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.dynamicCheckInstanceOf
import static org.jetbrains.plugins.gradle.tooling.util.StringUtils.toCamelCase

@ApiStatus.Internal
class GradleSourceSetModelBuilder extends AbstractModelBuilderService {

  private static final GradleVersion gradleBaseVersion = GradleVersion.current().baseVersion
  private static final boolean is4OrBetter = gradleBaseVersion >= GradleVersion.version("4.0")
  private static final boolean is67OrBetter = gradleBaseVersion >= GradleVersion.version("6.7")
  private static final boolean is74OrBetter = gradleBaseVersion >= GradleVersion.version("7.4")
  private static final boolean is80OrBetter = gradleBaseVersion >= GradleVersion.version("8.0")

  @Override
  boolean canBuild(String modelName) {
    return GradleSourceSetModel.class.name == modelName
  }

  @Override
  Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    DefaultGradleSourceSetModel sourceSetModel = new DefaultGradleSourceSetModel()
    sourceSetModel.setSourceCompatibility(JavaPluginUtil.getSourceCompatibility(project))
    sourceSetModel.setTargetCompatibility(JavaPluginUtil.getTargetCompatibility(project))
    sourceSetModel.setTaskArtifacts(collectProjectTaskArtifacts(project, context))
    sourceSetModel.setConfigurationArtifacts(collectProjectConfigurationArtifacts(project, context))
    sourceSetModel.setSourceSets(getSourceSets(project, context))
    sourceSetModel.setAdditionalArtifacts(collectNonSourceSetArtifacts(project, context))

    GradleSourceSetCache.getInstance(context)
      .setSourceSetModel(project, sourceSetModel)

    return sourceSetModel
  }

  @Override
  void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    GradleSourceSetCache.getInstance(context)
      .markSourceSetModelAsError(project)

    context.getMessageReporter().createMessage()
      .withGroup(Messages.SOURCE_SET_MODEL_GROUP)
      .withKind(Message.Kind.ERROR)
      .withTitle("Source set model building failure")
      .withException(exception)
      .reportMessage(project)
  }

  @NotNull
  private static List<File> collectProjectTaskArtifacts(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    List<File> taskArtifacts = new ArrayList<File>()
    GradleCollectionVisitor.accept(project.getTasks().withType(Jar.class), new GradleCollectionVisitor<Jar>() {

      @Override
      void visit(Jar element) {
        def archiveFile = getTaskArchiveFile(element)
        if (archiveFile != null) {
          taskArtifacts.add(archiveFile)
        }
      }

      @Override
      void onFailure(Jar element, @NotNull Exception exception) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_PROJECT_TASK_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Cannot resolve artifact file for the project Jar task: " + element.path)
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project)
      }

      @Override
      void visitAfterAccept(Jar element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_PROJECT_TASK_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Artifact files collecting for project Jar task was finished. " +
                    "Resolution for Jar task " + element.path + " will be skipped.")
          .withKind(Message.Kind.INTERNAL)
          .withStackTrace()
          .reportMessage(project)
      }
    })
    return new ArrayList<>(taskArtifacts)
  }

  @NotNull
  private static List<File> collectNonSourceSetArtifacts(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    List<File> additionalArtifacts = new ArrayList<File>()
    GradleCollectionVisitor.accept(project.getTasks().withType(Jar.class), new GradleCollectionVisitor<Jar>() {

      @Override
      void visit(Jar element) {
        def archiveFile = getTaskArchiveFile(element)
        if (archiveFile != null) {
          if (isJarDescendant(element) || containsPotentialClasspathElements(element, project)) {
            additionalArtifacts.add(archiveFile)
          }
        }
      }

      @Override
      void onFailure(Jar element, @NotNull Exception exception) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_NON_SOURCE_SET_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Cannot resolve artifact file for the project Jar task: " + element.path)
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project)
      }

      @Override
      void visitAfterAccept(Jar element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_NON_SOURCE_SET_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Artifact files collecting for project Jar task was finished. " +
                    "Resolution for Jar task " + element.path + " will be skipped.")
          .withKind(Message.Kind.INTERNAL)
          .withStackTrace()
          .reportMessage(project)
      }
    })
    return additionalArtifacts
  }

  @NotNull
  private static Map<String, Set<File>> collectProjectConfigurationArtifacts(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    Map<String, Set<File>> configurationArtifacts = new HashMap<String, Set<File>>()
    GradleCollectionVisitor.accept(project.getConfigurations(), new GradleCollectionVisitor<Configuration>() {

      @Override
      void visit(Configuration element) {
        def artifactSet = element.getArtifacts()
        def fileCollection = artifactSet.getFiles()
        Set<File> files = fileCollection.getFiles()
        configurationArtifacts.put(element.name, new LinkedHashSet<>(files))
      }

      @Override
      void onFailure(Configuration element, @NotNull Exception exception) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_PROJECT_CONFIGURATION_ARTIFACT_GROUP)
          .withTitle("Project configuration error")
          .withText("Cannot resolve artifact files for project configuration" + element)
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project)
      }

      @Override
      void visitAfterAccept(Configuration element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_PROJECT_CONFIGURATION_ARTIFACT_GROUP)
          .withTitle("Project configuration error")
          .withText("Artifact files collecting for project configuration was finished. " +
                    "Resolution for configuration " + element + " will be skipped.")
          .withKind(Message.Kind.INTERNAL)
          .withStackTrace()
          .reportMessage(project)
      }
    })
    return configurationArtifacts
  }

  @NotNull
  @CompileDynamic
  private static Map<String, DefaultExternalSourceSet> getSourceSets(
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
            resourcesDirectorySet.addGradleOutputDir(project.buildDir)
          }
        }
      }
      else {
        resourcesDirectorySet.addGradleOutputDir(GradleObjectUtil.notNull(
          sourceSet.output.resourcesDir,
          sourceSet.output.classesDir as File,
          project.buildDir
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
          javaDirectorySet.addGradleOutputDir(project.buildDir)
        }
      }
      else {
        javaDirectorySet.addGradleOutputDir(GradleObjectUtil.notNull(
          sourceSet.output.classesDir as File,
          project.buildDir
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

  private static void cleanupSharedSourceFolders(Map<String, ExternalSourceSet> map) {
    def mainSourceSet = map[SourceSet.MAIN_SOURCE_SET_NAME]
    cleanupSharedSourceFolders(map, mainSourceSet, null)
    cleanupSharedSourceFolders(map, map[SourceSet.TEST_SOURCE_SET_NAME], mainSourceSet)
  }

  private static void cleanupSharedSourceFolders(Map<String, ExternalSourceSet> result,
                                                 ExternalSourceSet sourceSet,
                                                 ExternalSourceSet toIgnore) {
    if (!sourceSet) return

    for (sourceSetEntry in result.entrySet()) {
      if (!sourceSetEntry.value.is(sourceSet) && !sourceSetEntry.value.is(toIgnore)) {
        def customSourceSet = sourceSetEntry.value
        for (sourceType in ExternalSystemSourceType.values()) {
          def customSourceDirectorySet = customSourceSet.sources.get(sourceType) as ExternalSourceDirectorySet
          if (customSourceDirectorySet) {
            for (sourceDirEntry in sourceSet.sources.entrySet()) {
              customSourceDirectorySet.srcDirs.removeAll(sourceDirEntry.value.srcDirs)
            }
          }
        }
      }
    }
  }

  private static boolean isJarDescendant(Jar task) {
    return GradleNegotiationUtil.getTaskIdentityType(task) != Jar
  }

  /**
   * Check the configured archive task inputs for specific files.
   *
   * We want to check, if IDEA is interested in keeping this archive task output in dependencies list
   * <br>
   * This may happen if <ul>
   *    <li>there are some class files
   *    <li>there are directories, not known to be outputs of source sets (modules)
   * @param archiveTask task to check
   * @param project project with source sets, potentially contributing to this task.
   * @return true if this jar should be kept in IDEA modules' dependencies' lists.
   */
  private static boolean containsPotentialClasspathElements(@NotNull AbstractArchiveTask archiveTask, @NotNull Project project) {
    def sourceSetContainer = JavaPluginUtil.getSourceSetContainer(project)
    if (sourceSetContainer == null || sourceSetContainer.isEmpty()) {
      return true
    }
    def outputFiles = new HashSet<File>()
    sourceSetContainer.all { SourceSet ss -> outputFiles.addAll(ss.output.files) }
    for (Object path: getArchiveTaskSourcePaths(archiveTask)) {
      if (isSafeToResolve(path, project) || isResolvableFileCollection(path, project)) {
        def files = new HashSet<>(project.files(path).files)
        files.removeAll(outputFiles)
        if (files.any { it.isDirectory() || (it.isFile() && it.name.endsWith(".class"))}) {
          return true
        }
      } else {
        return true
      }
    }
    return false
  }

  private static boolean containsAllSourceSetOutput(@NotNull AbstractArchiveTask archiveTask, @NotNull SourceSet sourceSet) {
    def outputFiles = new HashSet<>(sourceSet.output.files)
    def project = archiveTask.project

    try {
      Set<Object> sourcePaths = getArchiveTaskSourcePaths(archiveTask)
      for (Object path : sourcePaths) {
        if (isSafeToResolve(path, project) || isResolvableFileCollection(path, project)) {
          def files = project.files(path).files
          outputFiles.removeAll(files)
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e)
    }
    return outputFiles.isEmpty()
  }

  private static Set<Object> getArchiveTaskSourcePaths(AbstractArchiveTask archiveTask) {
    Set<Object> sourcePaths = null
    final Method mainSpecGetter = AbstractCopyTask.class.getDeclaredMethod("getMainSpec")
    mainSpecGetter.setAccessible(true)
    Object mainSpec = mainSpecGetter.invoke(archiveTask)
    final List<MetaMethod> sourcePathGetters =
      DefaultGroovyMethods.respondsTo(mainSpec, "getSourcePaths", new Object[]{})
    if (!sourcePathGetters.isEmpty()) {
      sourcePaths = (Set<Object>)sourcePathGetters.get(0).doMethodInvoke(mainSpec, new Object[]{})
    }
    if (sourcePaths != null) {
      return sourcePaths
    }
    else {
      return Collections.emptySet()
    }
  }


  private static boolean isResolvableFileCollection(Object param, Project project) {
    Object object = tryUnpackPresentProvider(param, project)
    if (object instanceof FileCollection) {
      try {
        project.files(object).files
        return true
      } catch (Throwable ignored) {
        return false
      }
    }
    return false
  }

  /**
   * Checks that object can be safely resolved using {@link Project#files(java.lang.Object...)} API.
   *
   * @param object
   * @return true if object is safe to resolve using {@link Project#files(java.lang.Object...)}
   * @see GradleSourceSetModelBuilder#tryUnpackPresentProvider
   */
  private static boolean isSafeToResolve(Object param, Project project) {
    Object object = tryUnpackPresentProvider(param, project)
    boolean isDirectoryOrRegularFile = dynamicCheckInstanceOf(object,
                                                              "org.gradle.api.file.Directory",
                                                              "org.gradle.api.file.RegularFile")

    return object instanceof CharSequence
      || object instanceof File || object instanceof Path
      || isDirectoryOrRegularFile
      || object instanceof SourceSetOutput
  }

  /**
   * Some Gradle {@link org.gradle.api.provider.Provider} implementations can not be resolved during sync,
   * causing {@link org.gradle.api.InvalidUserCodeException} and {@link org.gradle.api.InvalidUserDataException}.
   * Also some {@link org.gradle.api.provider.Provider} attempts to resolve dynamic
   * configurations, witch results in resolving a configuration without write lock on the project.
   *
   * @return provided value or current if value isn't present or cannot be evaluated
   */
  private static Object tryUnpackPresentProvider(Object object, Project project) {
    if (!dynamicCheckInstanceOf(object, "org.gradle.api.provider.Provider")) {
      return object
    }
    try {
      def providerClass = object.getClass()
      def isPresentMethod = providerClass.getMethod("isPresent")
      def getterMethod = providerClass.getMethod("get")
      if ((Boolean)isPresentMethod.invoke(object)) {
        return getterMethod.invoke(object)
      }
      return object
    }
    catch (InvocationTargetException exception) {
      Throwable targetException = exception.targetException
      boolean isCodeException = dynamicCheckInstanceOf(targetException, "org.gradle.api.InvalidUserCodeException")
      boolean isDataException = dynamicCheckInstanceOf(targetException, "org.gradle.api.InvalidUserDataException")
      if (isCodeException || isDataException) {
        return object
      }
      project.getLogger().info("Unable to resolve task source path: ${targetException?.message} (${targetException?.class?.canonicalName})")
      return object
    }
  }
}
