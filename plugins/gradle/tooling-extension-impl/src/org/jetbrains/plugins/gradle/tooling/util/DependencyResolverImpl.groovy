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

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.*
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.util.GUtil
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.model.*

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @author Vladislav.Soroka
 * @since 8/19/2015
 */
class DependencyResolverImpl implements DependencyResolver {

  private static is4OrBetter = GradleVersion.current().baseVersion >= GradleVersion.version("4.0")
  private static isJavaLibraryPluginSupported = is4OrBetter ||
                                                      (GradleVersion.current() >= GradleVersion.version("3.4"))
  private static isDependencySubstitutionsSupported = isJavaLibraryPluginSupported ||
                                                      (GradleVersion.current() > GradleVersion.version("2.5"))
  private static isArtifactResolutionQuerySupported = isDependencySubstitutionsSupported ||
                                                      (GradleVersion.current() >= GradleVersion.version("2.0"))

  @NotNull
  private final Project myProject
  private final boolean myIsPreview
  private final boolean myDownloadJavadoc
  private final boolean myDownloadSources
  private final SourceSetCachedFinder mySourceSetFinder

  @SuppressWarnings("GroovyUnusedDeclaration")
  DependencyResolverImpl(@NotNull Project project, boolean isPreview) {
    myProject = project
    myIsPreview = isPreview
    myDownloadJavadoc = false
    myDownloadSources = false
    mySourceSetFinder = new SourceSetCachedFinder(project)
  }

  DependencyResolverImpl(
    @NotNull Project project,
    boolean isPreview,
    boolean downloadJavadoc,
    boolean downloadSources,
    SourceSetCachedFinder sourceSetFinder) {
    myProject = project
    myIsPreview = isPreview
    myDownloadJavadoc = downloadJavadoc
    myDownloadSources = downloadSources
    mySourceSetFinder = sourceSetFinder
  }

  @Override
  Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName) {
    return resolveDependencies(configurationName, null)
  }

  Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName, @Nullable String scope) {
    if (configurationName == null) return Collections.emptyList()
    def (result, resolvedFileDependencies) = resolveDependencies(myProject.configurations.findByName(configurationName), scope)
    return result
  }

  @Override
  Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    def (result, resolvedFileDependencies) = resolveDependencies(configuration, null)
    return result
  }

  def resolveDependencies(@Nullable Configuration configuration, @Nullable String scope) {
    if (configuration == null) return [Collections.emptyList(), Collections.emptyList()]
    if (configuration.allDependencies.isEmpty()) return [Collections.emptyList(), Collections.emptyList()]

    final Collection<ExternalDependency> result = new LinkedHashSet<>()

    def resolvedFileDependencies = []
    if (!myIsPreview && isArtifactResolutionQuerySupported) {
      def jvmLibrary = null
      try {
        jvmLibrary = Class.forName('org.gradle.jvm.JvmLibrary')
      }
      catch (ClassNotFoundException ignored) {
      }
      if (jvmLibrary == null) {
        try {
          jvmLibrary = Class.forName('org.gradle.runtime.jvm.JvmLibrary')
        }
        catch (ClassNotFoundException ignored) {
        }
      }
      if (jvmLibrary != null) {
        Class[] artifactTypes = ([myDownloadSources?SourcesArtifact:null, myDownloadJavadoc?JavadocArtifact:null] - null) as Class[]
        Set<ResolvedArtifact> resolvedArtifacts = configuration.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)

        Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap = ArrayListMultimap.create()
        resolvedArtifacts.each { artifactMap.put(it.moduleVersion.id, it) }

        def isBuildScriptConfiguration = myProject.buildscript.configurations.find { it == configuration } != null
        //noinspection GroovyAssignabilityCheck
        def dependencyHandler = isBuildScriptConfiguration ? myProject.buildscript.dependencies : myProject.dependencies
        Set<ComponentArtifactsResult> componentResults = dependencyHandler.createArtifactResolutionQuery()
          .forComponents(resolvedArtifacts
                           .findAll { !isProjectDependencyArtifact(it) }
                           .collect { toComponentIdentifier(it.moduleVersion.id) })
          .withArtifacts(jvmLibrary, artifactTypes)
          .execute()
          .getResolvedComponents()

        Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap = [:]
        componentResults.each { componentResultsMap.put(it.id, it) }

        Set<Configuration> processedConfigurations = new HashSet<>()
        def projectDeps
        projectDeps = { Configuration conf, map = ArrayListMultimap.create() ->
          if(!processedConfigurations.add(conf)) return map
          conf.incoming.dependencies.findAll { it instanceof ProjectDependency }.each { it ->
            map.put(toComponentIdentifier(it.group, it.name, it.version), it as ProjectDependency)
            projectDeps(getTargetConfiguration(it as ProjectDependency), map)
          }
          map
        }
        Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies = projectDeps(configuration)

        ResolutionResult resolutionResult = configuration.incoming.resolutionResult
        if(!configuration.resolvedConfiguration.hasError()) {
          Collection<File> fileDeps = new LinkedHashSet<File>(configuration.incoming.files.files)
          artifactMap.values().each {
            fileDeps.remove(it.file)
          }
          configurationProjectDependencies.values().each {
            def intersect = fileDeps.intersect(it.resolve())
            if(!intersect.isEmpty()) {
              def fileCollectionDependency = new DefaultFileCollectionDependency(intersect)
              fileCollectionDependency.scope = scope
              result.add(fileCollectionDependency)
              fileDeps.removeAll(intersect)
            }
          }
          fileDeps.each {
            def fileCollectionDependency = new DefaultFileCollectionDependency([it])
            fileCollectionDependency.scope = scope
            result.add(fileCollectionDependency)
          }
        }

        def dependencyResultsTransformer = new DependencyResultsTransformer(artifactMap, componentResultsMap, configurationProjectDependencies, scope)
        result.addAll(dependencyResultsTransformer.transform(resolutionResult.root.dependencies))

        resolvedFileDependencies.addAll(dependencyResultsTransformer.resolvedDepsFiles)
      }
    }

    if (myIsPreview || !isArtifactResolutionQuerySupported) {
      def projectDependencies = findDependencies(configuration, configuration.allDependencies, scope)
      result.addAll(projectDependencies)
    }
    def fileDependencies = findAllFileDependencies(configuration.allDependencies, scope)
    result.addAll(fileDependencies - resolvedFileDependencies)

    return [new ArrayList(result), resolvedFileDependencies]
  }

  @Override
  Collection<ExternalDependency> resolveDependencies(@NotNull SourceSet sourceSet) {
    Collection<ExternalDependency> result = new ArrayList<>()

    // resolve compile dependencies
    def isMainSourceSet = sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME
    String deprecatedCompileConfigurationName = isMainSourceSet ? "compile" : GUtil.toCamelCase(sourceSet.name) + "Compile"
    def deprecatedCompileConfiguration = myProject.configurations.findByName(deprecatedCompileConfigurationName)
    def compileConfigurationName = sourceSet.compileConfigurationName
    def compileClasspathConfiguration = myProject.configurations.findByName(compileConfigurationName + 'Classpath')
    def originCompileConfiguration = myProject.configurations.findByName(compileConfigurationName)
    def compileConfiguration = compileClasspathConfiguration ?: originCompileConfiguration
    def compileOnlyConfiguration = isJavaLibraryPluginSupported ? myProject.configurations.findByName(sourceSet.compileOnlyConfigurationName) : null

    def compileScope = 'COMPILE'
    def (compileDependencies, resolvedCompileFileDependencies) = resolveDependencies(compileConfiguration, compileScope)
    // resolve runtime dependencies
    def runtimeConfigurationName = sourceSet.runtimeConfigurationName
    def runtimeClasspathConfiguration = myProject.configurations.findByName(runtimeConfigurationName + 'Classpath')
    def originRuntimeConfiguration = myProject.configurations.findByName(runtimeConfigurationName)
    def runtimeConfiguration = runtimeClasspathConfiguration ?: originRuntimeConfiguration

    def runtimeScope = 'RUNTIME'
    def (runtimeDependencies, resolvedRuntimeFileDependencies) = resolveDependencies(runtimeConfiguration, runtimeScope)

    def providedScope = 'PROVIDED'

    Multimap<Object, ExternalDependency> resolvedMap = ArrayListMultimap.create()

    boolean checkCompileOnlyDeps = compileClasspathConfiguration && !originCompileConfiguration.resolvedConfiguration.hasError()
    new DependencyTraverser(compileDependencies).each {
      def resolvedObj = resolve(it)
      resolvedMap.put(resolvedObj, it)

      // since version 3.4 compileOnly no longer extends compile
      // so, we can use compileOnly configuration for the check
      Object[] resolvedObjArray = resolvedObj instanceof Collection ? ((Collection)resolvedObj).toArray() : [resolvedObj]
      if (isJavaLibraryPluginSupported) {
        if (compileOnlyConfiguration != null && compileOnlyConfiguration.containsAll(resolvedObjArray)) {
          // deprecated 'compile' configuration still can be used
          if (deprecatedCompileConfiguration == null || !deprecatedCompileConfiguration.containsAll(resolvedObjArray)) {
            ((AbstractExternalDependency)it).scope = providedScope
          }
        }
      }
      else {
        if (checkCompileOnlyDeps && !originCompileConfiguration.containsAll(resolvedObjArray) &&
            !runtimeConfiguration.containsAll(resolvedObjArray)) {
          ((AbstractExternalDependency)it).scope = providedScope
        }
      }
    }

    Multimap<Object, ExternalDependency> resolvedRuntimeMap = ArrayListMultimap.create()
    new DependencyTraverser(runtimeDependencies).each {
      Collection<ExternalDependency> dependencies = resolvedMap.get(resolve(it))
      if (dependencies && !dependencies.isEmpty() && it.dependencies.isEmpty()) {
        runtimeDependencies.remove(it)
        ((AbstractExternalDependency)it).scope = compileScope
        dependencies.each {((AbstractExternalDependency)it).scope = compileScope}
      }
      else {
        if(dependencies) {
          ((AbstractExternalDependency)it).scope = compileScope
        }
        resolvedRuntimeMap.put(resolve(it), it)
      }
    }
    resolvedMap.putAll(resolvedRuntimeMap)

    result.addAll(compileDependencies)
    result.addAll(runtimeDependencies)
    result.unique()

    // merge file dependencies
    def jvmLanguages = ['Java', 'Groovy', 'Scala']
    def sourceSetCompileTaskPrefix = sourceSet.name == 'main' ? '' : sourceSet.name
    def compileTasks = jvmLanguages.collect { 'compile' + sourceSetCompileTaskPrefix.capitalize() + it }

    Map<File, Integer> compileClasspathOrder = new LinkedHashMap()
    Set<File> compileClasspathFiles = new LinkedHashSet<>()

    compileTasks.each {
      def compileTask = myProject.tasks.findByName(it)
      if (compileTask instanceof AbstractCompile) {
        try {
          def files = new ArrayList<>(compileTask.classpath.files)
          files.removeAll(compileClasspathFiles)
          compileClasspathFiles.addAll(files)
        }
        catch (ignore) {
        }
      }
    }

    try {
      compileClasspathFiles = compileClasspathFiles.isEmpty() ? sourceSet.compileClasspath.files : compileClasspathFiles
    }
    catch (ignore) {
    }
    int order = 0
    for (File file : compileClasspathFiles) {
      compileClasspathOrder.put(file, order++)
    }
    Map<File, Integer> runtimeClasspathOrder = new LinkedHashMap()
    order = 0
    Set<File> runtimeClasspathFiles = new LinkedHashSet<File>()
    try {
      def files = sourceSet.runtimeClasspath.files
      for (File file : files) {
        runtimeClasspathOrder.put(file, order++)
      }
      runtimeClasspathFiles.addAll(files)
    }
    catch (ignore) {
    }

    runtimeClasspathFiles -= compileClasspathFiles
    runtimeClasspathFiles -= sourceSet.output.files
    compileClasspathFiles -= sourceSet.output.files

    Multimap<String, File> resolvedDependenciesMap = ArrayListMultimap.create()
    resolvedDependenciesMap.putAll(compileScope, resolvedCompileFileDependencies)
    resolvedDependenciesMap.putAll(runtimeScope, resolvedRuntimeFileDependencies)
    Project rootProject = myProject.rootProject

    new DependencyTraverser(result).each {
      def dependency = it
      def scope = dependency.scope
      order = -1
      if (dependency instanceof ExternalProjectDependency) {
        ExternalProjectDependency projectDependency = dependency
        def project = rootProject.findProject(projectDependency.projectPath)
        def configuration = project?.configurations?.findByName(projectDependency.configurationName)
        configuration?.allArtifacts?.files?.files?.each {
          resolvedDependenciesMap.put(scope, it)
          def classpathOrderMap = scope == compileScope ? compileClasspathOrder :
                                  scope == runtimeScope ? runtimeClasspathOrder : null
          if (classpathOrderMap) {
            def fileOrder = classpathOrderMap.get(it)
            if (fileOrder != null && (order == -1 || fileOrder < order)) {
              order = fileOrder
            }
          }
        }

        //noinspection GrUnresolvedAccess
        if (project.hasProperty("sourceSets") && (project.sourceSets instanceof SourceSetContainer) && project.sourceSets.main) {
          //noinspection GrUnresolvedAccess
          addSourceSetOutputDirsAsSingleEntryLibraries(result, project.sourceSets.main, runtimeClasspathOrder, scope)
        }
      }
      else if (dependency instanceof ExternalLibraryDependency) {
        resolvedDependenciesMap.put(scope, dependency.file)
        def classpathOrderMap = scope == compileScope ? compileClasspathOrder :
                                scope == runtimeScope ? runtimeClasspathOrder : null
        if (classpathOrderMap) {
          def fileOrder = classpathOrderMap.get(dependency.file)
          order = fileOrder != null ? fileOrder : -1
        }
      }
      else if (dependency instanceof FileCollectionDependency) {
        for (File file : dependency.files) {
          resolvedDependenciesMap.put(scope, file)
          def classpathOrderMap = scope == compileScope ? compileClasspathOrder :
                                  scope == runtimeScope ? runtimeClasspathOrder : null
          if (classpathOrderMap) {
            def fileOrder = classpathOrderMap.get(file)
            if (fileOrder != null && (order == -1 || fileOrder < order)) {
              order = fileOrder
            }
            if (order == 0) break
          }
        }
      }

      if (dependency instanceof AbstractExternalDependency) {
        dependency.classpathOrder = order
      }
    }

    compileClasspathFiles.removeAll(resolvedDependenciesMap.get(compileScope))
    compileClasspathFiles.removeAll(resolvedDependenciesMap.get(providedScope))
    runtimeClasspathFiles.removeAll(resolvedDependenciesMap.get(runtimeScope))
    runtimeClasspathFiles.removeAll(resolvedDependenciesMap.get(compileScope))
    runtimeClasspathFiles.removeAll(resolvedDependenciesMap.get(providedScope))

    Collection<ExternalDependency> fileDependencies = new ArrayList<>()
    mapFileDependencies(runtimeClasspathFiles, runtimeScope, fileDependencies)
    mapFileDependencies(compileClasspathFiles, compileScope, fileDependencies)

    fileDependencies.each {
      def dependency = it
      def scope = dependency.scope
      order = -1
      if (dependency instanceof ExternalLibraryDependency) {
        def classpathOrderMap = scope == compileScope ? compileClasspathOrder :
                                scope == runtimeScope ? runtimeClasspathOrder : null
        if (classpathOrderMap) {
          def fileOrder = classpathOrderMap.get(dependency.file)
          order = fileOrder != null ? fileOrder : -1
        }
      }
      if (dependency instanceof AbstractExternalDependency) {
        dependency.classpathOrder = order
      }
    }
    result.addAll(fileDependencies)

    if (!compileClasspathFiles.isEmpty()) {
      final compileClasspathFilesDependency = new DefaultFileCollectionDependency(compileClasspathFiles)
      compileClasspathFilesDependency.scope = compileScope

      order = -1
      for (File file : compileClasspathFiles) {
        def fileOrder = compileClasspathOrder.get(file)
        if (fileOrder != null && (order == -1 || fileOrder < order)) {
          order = fileOrder
        }
        if (order == 0) break
      }

      if (order != -1) {
        compileClasspathFilesDependency.classpathOrder = order
      }
      result.add(compileClasspathFilesDependency)
      for (File file : compileClasspathFiles) {
        def outputDirSourceSet = mySourceSetFinder.findByArtifact(file.path)
        if(outputDirSourceSet) {
          addSourceSetOutputDirsAsSingleEntryLibraries(result, outputDirSourceSet, compileClasspathOrder, compileScope)
        }
      }
    }

    if (!runtimeClasspathFiles.isEmpty()) {
      final runtimeClasspathFilesDependency = new DefaultFileCollectionDependency(runtimeClasspathFiles)
      runtimeClasspathFilesDependency.scope = runtimeScope

      order = -1
      for (File file : runtimeClasspathFiles) {
        def fileOrder = runtimeClasspathOrder.get(file)
        if (fileOrder != null && (order == -1 || fileOrder < order)) {
          order = fileOrder
        }
        if (order == 0) break
      }

      runtimeClasspathFilesDependency.classpathOrder = order
      result.add(runtimeClasspathFilesDependency)

      for (File file : runtimeClasspathFiles) {
        def outputDirSourceSet = mySourceSetFinder.findByArtifact(file.path)
        if(outputDirSourceSet) {
          addSourceSetOutputDirsAsSingleEntryLibraries(result, outputDirSourceSet, runtimeClasspathOrder, runtimeScope)
        }
      }
    }

    addSourceSetOutputDirsAsSingleEntryLibraries(result, sourceSet, runtimeClasspathOrder, runtimeScope)

    // handle provided dependencies
    def providedConfigurations = new LinkedHashSet<Configuration>()
    resolvedMap = ArrayListMultimap.create()
    new DependencyTraverser(result).each { resolvedMap.put(resolve(it), it) }

    if (sourceSet.name == 'main' && myProject.plugins.findPlugin(WarPlugin)) {
      providedConfigurations.add(myProject.configurations.findByName('providedCompile'))
      providedConfigurations.add(myProject.configurations.findByName('providedRuntime'))
    }

    final IdeaPlugin ideaPlugin = myProject.getPlugins().findPlugin(IdeaPlugin.class)
    if (ideaPlugin) {
      def scopes = ideaPlugin.model.module.scopes
      def providedPlusScopes = scopes.get(providedScope)
      if (providedPlusScopes && providedPlusScopes.get("plus")) {
        // filter default 'compileClasspath' for slight optimization since it has been already processed as compile dependencies
        def ideaPluginProvidedConfigurations = providedPlusScopes.get("plus").findAll { it.name != "compileClasspath"}
        // since gradle 3.4 'idea' plugin PROVIDED scope.plus contains 'providedCompile' and 'providedRuntime' configurations
        // see https://github.com/gradle/gradle/commit/c46897ae840c5ebb32946009c83d861ee194ab96#diff-0fa13ec419e839ef2d355b7feb88b815R432
        ideaPluginProvidedConfigurations.removeAll(providedConfigurations)
        ideaPluginProvidedConfigurations.each {
          def (providedDependencies, _) = resolveDependencies(it, providedScope)
          new DependencyTraverser(providedDependencies).each { resolvedMap.put(resolve(it), it) }
          result.addAll(providedDependencies)
        }
      }
    }
    providedConfigurations.each {
      def (providedDependencies, _) = resolveDependencies(it, providedScope)
      new DependencyTraverser(providedDependencies).each {
        Collection<ExternalDependency> dependencies = resolvedMap.get(resolve(it))
        if (!dependencies.isEmpty()) {
          if (it.dependencies.isEmpty()) {
            providedDependencies.remove(it)
          }
          dependencies.each {
            ((AbstractExternalDependency)it).scope = providedScope
          }
        }
        else {
          resolvedMap.put(resolve(it), it)
        }
      }
      result.addAll(providedDependencies)
    }

    return removeDuplicates(resolvedMap, result)
  }

  private static List<ExternalDependency> removeDuplicates(
    ArrayListMultimap<Object, ExternalDependency> resolvedMap,  List<ExternalDependency> result) {
    resolvedMap.asMap().values().each {
      def toRemove = []
      def isCompileScope = false
      def isProvidedScope = false
      it.each {
        if (it.dependencies.isEmpty()) {
          toRemove.add(it)
          if(it.scope == 'COMPILE') isCompileScope = true
          else if(it.scope == 'PROVIDED') isProvidedScope = true
        }
      }
      if (toRemove.size() != it.size()) {
        result.removeAll(toRemove)
      }
      else if (toRemove.size() > 1) {
        toRemove = toRemove.drop(1)
        result.removeAll(toRemove)
      }
      if(!toRemove.isEmpty()) {
        def retained = it - toRemove
        if(!retained.isEmpty()) {
          def retainedDependency = retained.find{true} as AbstractExternalDependency
          if(retainedDependency instanceof AbstractExternalDependency && retainedDependency.scope != 'COMPILE') {
            if(isCompileScope) retainedDependency.scope = 'COMPILE'
            else if(isProvidedScope) retainedDependency.scope = 'PROVIDED'
          }
        }
      }
    }

    return result.unique()
  }

  static def resolve(ExternalDependency dependency) {
    if (dependency instanceof ExternalLibraryDependency) {
      return dependency.file
    } else if (dependency instanceof FileCollectionDependency) {
      return dependency.files
    } else if (dependency instanceof ExternalMultiLibraryDependency) {
      return dependency.files
    } else if (dependency instanceof ExternalProjectDependency) {
      return dependency.projectDependencyArtifacts
    }
    null
  }

  private static void addSourceSetOutputDirsAsSingleEntryLibraries(
    Collection<ExternalDependency> dependencies,
    SourceSet sourceSet,
    Map<File, Integer> classpathOrder,
    String scope) {
    Set<File> runtimeOutputDirs = sourceSet.output.dirs.files
    runtimeOutputDirs.each {
      final runtimeOutputDirsDependency = new DefaultFileCollectionDependency([it])
      runtimeOutputDirsDependency.scope = scope
      def fileOrder = classpathOrder.get(it)
      runtimeOutputDirsDependency.classpathOrder = fileOrder != null ? fileOrder : -1
      dependencies.add(runtimeOutputDirsDependency)
    }
  }


  @Nullable
  ExternalLibraryDependency resolveLibraryByPath(File file, String scope) {
    File modules2Dir = new File(myProject.gradle.gradleUserHomeDir, "caches/modules-2/files-2.1")
    return resolveLibraryByPath(file, modules2Dir, scope)
  }

  @Nullable
  static ExternalLibraryDependency resolveLibraryByPath(File file, File modules2Dir, String scope) {
    File sourcesFile = null
    def modules2Path = modules2Dir.canonicalPath
    def filePath = file.canonicalPath
    if (filePath.startsWith(modules2Path)) {
      List<File> parents = new ArrayList<>()
      File parent = file.parentFile
      while(parent && !parent.name.equals(modules2Dir.name)) {
        parents.add(parent)
        parent = parent.parentFile
      }

      def groupDir = parents.get(parents.size() - 1)
      def artifactDir = parents.get(parents.size() - 2)
      def versionDir = parents.get(parents.size() - 3)

      def parentFile = versionDir
      if (parentFile != null) {
        def hashDirs = parentFile.listFiles()
        if (hashDirs != null) {
          for (File hashDir : hashDirs) {
            def sourcesJars = hashDir.listFiles(new FilenameFilter() {
              @Override
              boolean accept(File dir, String name) {
                return name.endsWith("sources.jar")
              }
            })

            if (sourcesJars != null && sourcesJars.length > 0) {
              sourcesFile = sourcesJars[0]
              break
            }
          }

          def packaging = resolvePackagingType(file)
          def classifier = resolveClassifier(artifactDir.name, versionDir.name, file)
          return new DefaultExternalLibraryDependency(
            name: artifactDir.name,
            group: groupDir.name,
            packaging: packaging,
            classifier: classifier,
            version: versionDir.name,
            file: file,
            source: sourcesFile,
            scope: scope
          )
        }
      }
    }

    null
  }

  def mapFileDependencies(Set<File> fileDependencies, String scope, Collection<ExternalDependency> dependencies) {
    File modules2Dir = new File(myProject.gradle.gradleUserHomeDir, "caches/modules-2/files-2.1")
    List toRemove = new ArrayList()
    for (File file : fileDependencies) {
      def libraryDependency = resolveLibraryByPath(file, modules2Dir, scope)
      if (libraryDependency) {
        dependencies.add(libraryDependency)
        toRemove.add(file)
      }
      else {
        //noinspection GrUnresolvedAccess
        def name = file.name.lastIndexOf('.').with { it != -1 ? file.name[0..<it] : file.name }
        def sourcesFile = new File(file.parentFile, name + '-sources.jar')
        if (sourcesFile.exists()) {
          libraryDependency = new DefaultExternalLibraryDependency(
            file: file,
            source: sourcesFile,
            scope: scope
          )
          if (libraryDependency) {
            dependencies.add(libraryDependency)
            toRemove.add(file)
          }
        }
      }
    }

    fileDependencies.removeAll(toRemove)
  }

  @Nullable
  static String resolvePackagingType(File file) {
    if (file == null) return 'jar'
    def path = file.getPath()
    int index = path.lastIndexOf('.')
    if (index < 0) return 'jar'
    return path.substring(index + 1)
  }

  @Nullable
  static String resolveClassifier(String name, String version, File file) {
    String libraryFileName = getNameWithoutExtension(file)
    final String mavenLibraryFileName = "$name-$version"
    if (!mavenLibraryFileName.equals(libraryFileName)) {
      Matcher matcher = Pattern.compile("$name-$version-(.*)").matcher(libraryFileName)
      if (matcher.matches()) {
        return matcher.group(1)
      }
    }
    return null
  }

  static String getNameWithoutExtension(File file) {
    if (file == null) return null
    def name = file.name
    int i = name.lastIndexOf('.')
    if (i != -1) {
      name = name.substring(0, i)
    }
    return name
  }

  private static toComponentIdentifier(ModuleVersionIdentifier id) {
    return new ModuleComponentIdentifierImpl(id.getGroup(), id.getName(), id.getVersion())
  }

  private static toComponentIdentifier(@NotNull String group, @NotNull String module, @NotNull String version) {
    return new ModuleComponentIdentifierImpl(group, module, version)
  }

  private static Set<ExternalDependency> findAllFileDependencies(
    Collection<Dependency> dependencies, String scope) {
    Set<ExternalDependency> result = new LinkedHashSet<>()

    dependencies.each {
      try {
        if (it instanceof SelfResolvingDependency && !(it instanceof ProjectDependency)) {
          def files = it.resolve()
          if (files && !files.isEmpty()) {
            final dependency = new DefaultFileCollectionDependency(files)
            dependency.scope = scope
            result.add(dependency)
          }
        }
      }
      catch (ignore) {
      }
    }

    return result
  }

  private Set<ExternalDependency> findDependencies(
    Configuration configuration,
    Collection<Dependency> dependencies,
    String scope) {
    Set<ExternalDependency> result = new LinkedHashSet<>()

    Set<ResolvedArtifact> resolvedArtifacts = myIsPreview ? new HashSet<>() :
                                              configuration.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)

    Multimap<MyModuleIdentifier, ResolvedArtifact> artifactMap = ArrayListMultimap.create()
    resolvedArtifacts.each { artifactMap.put(toMyModuleIdentifier(it.moduleVersion.id), it) }

    dependencies.each {
      try {
        if (it instanceof ProjectDependency) {
          def project = it.getDependencyProject()
          Configuration targetConfiguration = getTargetConfiguration(it)

          final projectDependency = new DefaultExternalProjectDependency(
            name: project.name,
            group: project.group,
            version: project.version,
            scope: scope,
            projectPath: project.path,
            configurationName: targetConfiguration.name
          )
          projectDependency.projectDependencyArtifacts = targetConfiguration.allArtifacts.files.files
          result.add(projectDependency)
        }
        else if (it instanceof Dependency) {
          def artifactsResult = artifactMap.get(toMyModuleIdentifier(it.name, it.group))
          if (artifactsResult && !artifactsResult.isEmpty()) {
            def artifact = artifactsResult.find{true}
            def packaging = artifact.extension ?: 'jar'
            def classifier = artifact.classifier
            File sourcesFile = resolveLibraryByPath(artifact.file, scope)?.source
            def libraryDependency = new DefaultExternalLibraryDependency(
              name: it.name,
              group: it.group,
              packaging: packaging,
              classifier: classifier,
              version: artifact.moduleVersion.id.version,
              scope: scope,
              file: artifact.file,
              source: sourcesFile
            )
            result.add(libraryDependency)
          }
          else {
            if (!(it instanceof SelfResolvingDependency) && !myIsPreview) {
              final dependency = new DefaultUnresolvedExternalDependency(
                name: it.name,
                group: it.group,
                version: it.version,
                scope: scope,
                failureMessage: "Could not find " + it.group + ":" + it.name + ":" + it.version
              )
              result.add(dependency)
            }
          }
        }
      }
      catch (ignore) {
      }
    }

    return result
  }

  private static Configuration getTargetConfiguration(ProjectDependency projectDependency) {
    return !is4OrBetter ? projectDependency.projectConfiguration :
           projectDependency.dependencyProject.configurations.getByName(projectDependency.targetConfiguration ?: 'default')
  }

  class DependencyResultsTransformer {
    Collection<DependencyResult> handledDependencyResults
    Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap
    Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap
    Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies
    String scope
    Set<File> resolvedDepsFiles = []

    DependencyResultsTransformer(
      Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap,
      Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap,
      Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies,
      String scope) {
      this.handledDependencyResults = Lists.newArrayList()
      this.artifactMap = artifactMap
      this.componentResultsMap = componentResultsMap
      this.configurationProjectDependencies = configurationProjectDependencies
      this.scope = scope
    }

    Set<ExternalDependency> transform(Collection<DependencyResult> dependencyResults) {

      Set<ExternalDependency> dependencies = new LinkedHashSet<>()
      dependencyResults.each { DependencyResult dependencyResult ->

        // dependency cycles check
        if (!handledDependencyResults.contains(dependencyResult)) {
          handledDependencyResults.add(dependencyResult)

          if (dependencyResult instanceof ResolvedDependencyResult) {
            def componentResult = dependencyResult.selected
            def componentSelector = dependencyResult.requested
            def componentIdentifier = toComponentIdentifier(componentResult.moduleVersion)
            def name = componentResult.moduleVersion.name
            def group = componentResult.moduleVersion.group
            def version = componentResult.moduleVersion.version
            def selectionReason = componentResult.selectionReason.description
            def resolveFromArtifacts = componentSelector instanceof ModuleComponentSelector
            if (componentSelector instanceof ProjectComponentSelector) {
              def projectDependencies = configurationProjectDependencies.get(componentIdentifier)
              Collection<Configuration> dependencyConfigurations
              if(projectDependencies.isEmpty()) {
                def dependencyProject = myProject.findProject(componentSelector.projectPath)
                if(dependencyProject) {
                  def dependencyProjectConfiguration = dependencyProject.getConfigurations().getByName(Dependency.DEFAULT_CONFIGURATION)
                  dependencyConfigurations = [dependencyProjectConfiguration]
                } else {
                  dependencyConfigurations = []
                  resolveFromArtifacts = true
                  selectionReason = "composite build substitution"
                }
              } else {
                dependencyConfigurations = projectDependencies.collect { getTargetConfiguration(it) }
              }

              dependencyConfigurations.each {
                if (it.name == Dependency.DEFAULT_CONFIGURATION) {
                  final dependency = new DefaultExternalProjectDependency(
                    name: name,
                    group: group,
                    version: version,
                    scope: scope,
                    selectionReason: selectionReason,
                    projectPath: (componentSelector as ProjectComponentSelector).projectPath,
                    configurationName: it.name
                  )
                  dependency.projectDependencyArtifacts = it.allArtifacts.files.files
                  dependency.projectDependencyArtifacts.each { resolvedDepsFiles.add(it) }
                  if(it.artifacts.size() == 1) {
                    def publishArtifact = it.allArtifacts.find{true}
                    dependency.classifier = publishArtifact.classifier
                    dependency.packaging = publishArtifact.extension ?: 'jar'
                  }

                  if (componentResult != dependencyResult.from) {
                    dependency.dependencies.addAll(
                      transform(componentResult.dependencies)
                    )
                  }
                  dependencies.add(dependency)
                }
                else {
                  final dependency = new DefaultExternalProjectDependency(
                    name: name,
                    group: group,
                    version: version,
                    scope: scope,
                    selectionReason: selectionReason,
                    projectPath: (componentSelector as ProjectComponentSelector).projectPath,
                    configurationName: it.name
                  )
                  dependency.projectDependencyArtifacts = it.allArtifacts.files.files
                  dependency.projectDependencyArtifacts.each { resolvedDepsFiles.add(it) }
                  if(it.artifacts.size() == 1) {
                    def publishArtifact = it.allArtifacts.find{true}
                    dependency.classifier = publishArtifact.classifier
                    dependency.packaging = publishArtifact.extension ?: 'jar'
                  }

                  if (componentResult != dependencyResult.from) {
                    dependency.dependencies.addAll(
                      transform(componentResult.dependencies)
                    )
                  }
                  dependencies.add(dependency)

                  def files = []
                  def artifacts = it.getArtifacts()
                  if (artifacts && !artifacts.isEmpty()) {
                    def artifact = artifacts.find{true}
                    if (artifact.hasProperty("archiveTask") &&
                        (artifact.archiveTask instanceof org.gradle.api.tasks.bundling.AbstractArchiveTask)) {
                      def archiveTask = artifact.archiveTask as AbstractArchiveTask
                      resolvedDepsFiles.add(new File(archiveTask.destinationDir, archiveTask.archiveName))

                      def mainSpec = archiveTask.mainSpec
                      def sourcePaths
                      if (mainSpec.metaClass.respondsTo(mainSpec, 'getSourcePaths')) {
                        sourcePaths = mainSpec.getSourcePaths()
                      }
                      else if (mainSpec.hasProperty('sourcePaths')) {
                        sourcePaths = mainSpec.sourcePaths
                      }
                      if (sourcePaths) {
                        (sourcePaths.flatten() as List).each { path ->
                          if (path instanceof String) {
                            def file = new File(path)
                            if (file.isAbsolute()) {
                              files.add(file)
                            }
                          }
                          else if (path instanceof SourceSetOutput) {
                            files.addAll(path.files)
                          }
                        }
                      }
                    }
                  }

                  if(!files.isEmpty()) {
                    final fileCollectionDependency = new DefaultFileCollectionDependency(files)
                    fileCollectionDependency.scope = scope
                    dependencies.add(fileCollectionDependency)
                    resolvedDepsFiles.addAll(files)
                  }
                }
              }
            }
            if (resolveFromArtifacts) {
              def artifacts = artifactMap.get(componentResult.moduleVersion)
              def artifact = artifacts?.find { true }

              if (artifacts?.isEmpty()) {
                dependencies.addAll(
                  transform(componentResult.dependencies)
                )
              }
              boolean first = true
              artifacts?.each {
                artifact = it
                def packaging = it.extension ?: 'jar'
                def classifier = it.classifier
                final dependency
                if (isProjectDependencyArtifact(artifact)) {
                  def artifactComponentIdentifier = artifact.id.componentIdentifier as ProjectComponentIdentifier
                  dependency = new DefaultExternalProjectDependency(
                    name: name,
                    group: group,
                    version: version,
                    scope: scope,
                    selectionReason: selectionReason,
                    projectPath: artifactComponentIdentifier.projectPath,
                    configurationName: Dependency.DEFAULT_CONFIGURATION
                  )
                  dependency.projectDependencyArtifacts = artifactMap.get(componentResult.moduleVersion).collect { it.file }
                  dependency.projectDependencyArtifacts.each { resolvedDepsFiles.add(it) }
                }
                else {
                  dependency = new DefaultExternalLibraryDependency(
                    name: name,
                    group: group,
                    packaging: packaging,
                    classifier: classifier,
                    version: version,
                    scope: scope,
                    selectionReason: selectionReason,
                    file: artifact.file
                  )

                  def artifactsResult = componentResultsMap.get(componentIdentifier)
                  if (artifactsResult) {
                    def sourcesResult = artifactsResult.getArtifacts(SourcesArtifact)?.find { it instanceof ResolvedArtifactResult }
                    if (sourcesResult) {
                      dependency.setSource(((ResolvedArtifactResult)sourcesResult).getFile())
                    }
                    def javadocResult = artifactsResult.getArtifacts(JavadocArtifact)?.find { it instanceof ResolvedArtifactResult }
                    if (javadocResult) {
                      dependency.setJavadoc(((ResolvedArtifactResult)javadocResult).getFile())
                    }
                  }
                }
                if (first) {
                  dependency.dependencies.addAll(
                    transform(componentResult.dependencies)
                  )
                  first = false
                }

                dependencies.add(dependency)
                resolvedDepsFiles.add(artifact.file)
              }
            }
          }

          if (dependencyResult instanceof UnresolvedDependencyResult) {
            def componentResult = dependencyResult.attempted
            if (componentResult instanceof ModuleComponentSelector) {
              final dependency = new DefaultUnresolvedExternalDependency(
                name: componentResult.module,
                group: componentResult.group,
                version: componentResult.version,
                scope: scope,
                failureMessage: dependencyResult.failure.message
              )
              dependencies.add(dependency)
            }
          }
        }
      }

      return dependencies
    }
  }

  private static boolean isProjectDependencyArtifact(ResolvedArtifact artifact) {
    return isDependencySubstitutionsSupported && artifact.id.componentIdentifier instanceof ProjectComponentIdentifier
  }

  private static toMyModuleIdentifier(ModuleVersionIdentifier id) {
    return new MyModuleIdentifier(name: id.getName(), group: id.getGroup())
  }

  private static toMyModuleIdentifier(String name, String group) {
    return new MyModuleIdentifier(name: name, group: group)
  }

  static class MyModuleIdentifier {
    String name
    String group

    boolean equals(o) {
      if (this.is(o)) return true
      if (!(o instanceof MyModuleIdentifier)) return false

      MyModuleIdentifier that = (MyModuleIdentifier)o

      if (group != that.group) return false
      if (name != that.name) return false

      return true
    }

    int hashCode() {
      int result = (group != null ? group.hashCode() : 0)
      result = 31 * result + (name != null ? name.hashCode() : 0)
      return result
    }

    @Override
    String toString() {
      return "$group:$name"
    }
  }
}
