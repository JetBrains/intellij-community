/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.*
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.ExternalDependencyId
import org.jetbrains.plugins.gradle.model.*

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @author Vladislav.Soroka
 * @since 8/19/2015
 */
class DependencyResolverImpl implements DependencyResolver {

  @NotNull
  private final Project myProject
  private final boolean myIsPreview
  private final boolean myDownloadJavadoc
  private final boolean myDownloadSources

  DependencyResolverImpl(@NotNull Project project, boolean isPreview) {
    myProject = project
    myIsPreview = isPreview
    myDownloadJavadoc = false
    myDownloadSources = false
  }

  DependencyResolverImpl(@NotNull Project project, boolean isPreview, boolean downloadJavadoc, boolean downloadSources) {
    myProject = project
    myIsPreview = isPreview
    myDownloadJavadoc = downloadJavadoc
    myDownloadSources = downloadSources
  }

  @Override
  Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName) {
    return resolveDependencies(configurationName, null)
  }

  Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName, @Nullable String scope) {
    if (configurationName == null) return Collections.emptyList()
    return resolveDependencies(myProject.configurations.findByName(configurationName), scope)
  }

  @Override
  Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    return resolveDependencies(configuration, null)
  }

  Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration, @Nullable String scope) {
    if (configuration == null) return Collections.emptyList()
    if (configuration.allDependencies.isEmpty()) return Collections.emptyList()

    final Collection<ExternalDependency> result = new LinkedHashSet<>()


    def isArtifactResolutionQuerySupported = GradleVersion.current().compareTo(GradleVersion.version("2.0")) >= 0

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
        Class[] artifactTypes = ([myDownloadSources?SourcesArtifact:null, myDownloadJavadoc?JavadocArtifact:null] - null) as Class[];
        Set<ResolvedArtifact> resolvedArtifacts = configuration.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)

        Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap = ArrayListMultimap.create()
        resolvedArtifacts.each { artifactMap.put(it.moduleVersion.id, it) }
        Set<ComponentArtifactsResult> componentResults = myProject.dependencies.createArtifactResolutionQuery()
          .forComponents(resolvedArtifacts.collect { toComponentIdentifier(it.moduleVersion.id) })
          .withArtifacts(jvmLibrary, artifactTypes)
          .execute()
          .getResolvedComponents()

        Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap = [:];
        componentResults.each { componentResultsMap.put(it.id, it) }

        ResolutionResult resolutionResult = configuration.incoming.resolutionResult
        if(!configuration.resolvedConfiguration.hasError()) {
          def fileDeps = new LinkedHashSet<File>(configuration.incoming.files.files);
          artifactMap.values().each {
            fileDeps.remove(it.file)
          }
          fileDeps.each {
            def fileCollectionDependency = new DefaultFileCollectionDependency([it])
            fileCollectionDependency.scope = scope
            result.add(fileCollectionDependency)
          }
        }
        result.addAll(transform(Lists.newArrayList(), resolutionResult.root.dependencies, artifactMap, componentResultsMap, scope))
      }
    }

    if (myIsPreview || !isArtifactResolutionQuerySupported) {
      def projectDependencies = findDependencies(configuration, configuration.allDependencies, scope)
      result.addAll(projectDependencies);
    }
    def fileDependencies = findAllFileDependencies(configuration.allDependencies, scope)
    result.addAll(fileDependencies)

    return new ArrayList(result)
  }

  @Override
  Collection<ExternalDependency> resolveDependencies(@NotNull SourceSet sourceSet) {
    Collection<ExternalDependency> result = new ArrayList<>()

    // resolve compile dependencies
    def compileConfigurationName = sourceSet.compileConfigurationName
    def compileConfiguration = myProject.configurations.findByName(compileConfigurationName)

    def compileScope = 'COMPILE'
    def compileDependencies = resolveDependencies(compileConfiguration, compileScope)
    // resolve runtime dependencies
    def runtimeConfigurationName = sourceSet.runtimeConfigurationName
    def runtimeConfiguration = myProject.configurations.findByName(runtimeConfigurationName)

    def runtimeScope = 'RUNTIME'
    def runtimeDependencies = resolveDependencies(runtimeConfiguration, runtimeScope)

    def providedScope = 'PROVIDED'

    Multimap<ExternalDependencyId, ExternalDependency> resolvedMap = ArrayListMultimap.create()
    new DependencyTraverser(compileDependencies).each { resolvedMap.put(it.id, it) }

    new DependencyTraverser(runtimeDependencies).each {
      Collection<ExternalDependency> dependencies = resolvedMap.get(it.id);
      if (dependencies && !dependencies.isEmpty() && it.dependencies.isEmpty()) {
        runtimeDependencies.remove(it)
        ((AbstractExternalDependency)it).scope = dependencies.first().scope
      }
      else {
        resolvedMap.put(it.id, it)
      }
    }

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
    int order = 0;
    for (File file : compileClasspathFiles) {
      compileClasspathOrder.put(file, order++);
    }
    Map<File, Integer> runtimeClasspathOrder = new LinkedHashMap()
    order = 0;
    Set<File> runtimeClasspathFiles = new LinkedHashSet<File>()
    try {
      def files = sourceSet.runtimeClasspath.files
      for (File file : files) {
        runtimeClasspathOrder.put(file, order++);
      }
      runtimeClasspathFiles.addAll(files)
    }
    catch (ignore) {
    }

    runtimeClasspathFiles -= compileClasspathFiles
    runtimeClasspathFiles -= sourceSet.output.files
    compileClasspathFiles -= sourceSet.output.files

    Multimap<String, File> resolvedDependenciesMap = ArrayListMultimap.create()
    Project rootProject = myProject.rootProject

    new DependencyTraverser(result).each {
      def dependency = it
      def scope = dependency.scope
      order = -1;
      if (dependency instanceof ExternalProjectDependency) {
        ExternalProjectDependency projectDependency = dependency
        def project = rootProject.findProject(projectDependency.projectPath)
        def configuration = project?.configurations?.getByName("default")
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
          addSourceSetOutputDirsAsSingleEntryLibraries(result, project.sourceSets.main, runtimeClasspathOrder, runtimeScope)
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
      order = -1;
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

      order = -1;
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
    }

    if (!runtimeClasspathFiles.isEmpty()) {
      final runtimeClasspathFilesDependency = new DefaultFileCollectionDependency(runtimeClasspathFiles)
      runtimeClasspathFilesDependency.scope = runtimeScope

      order = -1;
      for (File file : runtimeClasspathFiles) {
        def fileOrder = runtimeClasspathOrder.get(file)
        if (fileOrder != null && (order == -1 || fileOrder < order)) {
          order = fileOrder
        }
        if (order == 0) break
      }

      runtimeClasspathFilesDependency.classpathOrder = order
      result.add(runtimeClasspathFilesDependency)
    }

    addSourceSetOutputDirsAsSingleEntryLibraries(result, sourceSet, runtimeClasspathOrder, runtimeScope)

    // handle provided dependencies
    def providedConfigurations = new LinkedHashSet<Configuration>()
    if (sourceSet.name == 'main' || sourceSet.name == 'test') {
      resolvedMap = ArrayListMultimap.create()
      new DependencyTraverser(result).each { resolvedMap.put(it.id, it) }
      final IdeaPlugin ideaPlugin = myProject.getPlugins().findPlugin(IdeaPlugin.class);
      if (ideaPlugin) {
        def scopes = ideaPlugin.model.module.scopes
        def providedPlusScopes = scopes.get(providedScope)
        if (providedPlusScopes && providedPlusScopes.get("plus")) {
          providedConfigurations.addAll(providedPlusScopes.get("plus"))
        }
      }
    }
    if (sourceSet.name == 'main' && myProject.plugins.findPlugin(WarPlugin)) {
      providedConfigurations.add(myProject.configurations.findByName('providedCompile'))
      providedConfigurations.add(myProject.configurations.findByName('providedRuntime'))
    }
    providedConfigurations.each {
      def providedDependencies = resolveDependencies(it, providedScope)
      new DependencyTraverser(providedDependencies).each {
        Collection<ExternalDependency> dependencies = resolvedMap.get(it.id);
        if (!dependencies.isEmpty()) {
          dependencies.each { ((AbstractExternalDependency)it).scope = providedScope }
          if (it.dependencies.isEmpty()) {
            providedDependencies.remove(it)
          }
        }
        else {
          resolvedMap.put(it.id, it)
        }
      }
      result.addAll(providedDependencies)
    }

    return result.unique()
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
    File modules2Dir = new File(myProject.gradle.gradleUserHomeDir, "caches/modules-2/files-2.1");
    return resolveLibraryByPath(file, modules2Dir, scope)
  }

  @Nullable
  static ExternalLibraryDependency resolveLibraryByPath(File file, File modules2Dir, String scope) {
    File sourcesFile = null;
    def modules2Path = modules2Dir.canonicalPath
    def filePath = file.canonicalPath
    if (filePath.startsWith(modules2Path)) {
      List<File> parents = new ArrayList<>()
      File parent = file.parentFile;
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
              sourcesFile = sourcesJars[0];
              break
            }
          }

          def packaging = resolvePackagingType(file);
          def classifier = resolveClassifier(artifactDir.name, versionDir.name, file);
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
    File modules2Dir = new File(myProject.gradle.gradleUserHomeDir, "caches/modules-2/files-2.1");
    List toRemove = new ArrayList()
    for (File file : fileDependencies) {
      def libraryDependency = resolveLibraryByPath(file, modules2Dir, scope)
      if (libraryDependency) {
        dependencies.add(libraryDependency)
        toRemove.add(file)
      }
    }

    fileDependencies.removeAll(toRemove)
  }

  @Nullable
  static String resolvePackagingType(File file) {
    if (file == null) return 'jar'
    def path = file.getPath()
    int index = path.lastIndexOf('.');
    if (index < 0) return 'jar';
    return path.substring(index + 1)
  }

  @Nullable
  static String resolveClassifier(String name, String version, File file) {
    String libraryFileName = getNameWithoutExtension(file);
    final String mavenLibraryFileName = "$name-$version";
    if (!mavenLibraryFileName.equals(libraryFileName)) {
      Matcher matcher = Pattern.compile("$name-$version-(.*)").matcher(libraryFileName);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return null
  }

  static String getNameWithoutExtension(File file) {
    if (file == null) return null
    def name = file.name
    int i = name.lastIndexOf('.');
    if (i != -1) {
      name = name.substring(0, i);
    }
    return name;
  }

  private static toComponentIdentifier(ModuleVersionIdentifier id) {
    return new ModuleComponentIdentifierImpl(id.getGroup(), id.getName(), id.getVersion());
  }

  private static Set<ExternalDependency> findAllFileDependencies(
    Collection<Dependency> dependencies, String scope) {
    Set<ExternalDependency> result = new LinkedHashSet<>()

    dependencies.each {
      try {
        if (it instanceof SelfResolvingDependency && !(it instanceof ProjectDependency)) {
          final dependency = new DefaultFileCollectionDependency(it.resolve())
          dependency.scope = scope
          result.add(dependency)
        }
      }
      catch (ignore) {
      }
    }

    return result;
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
          final projectDependency = new DefaultExternalProjectDependency(
            name: project.name,
            group: project.group,
            version: project.version,
            scope: scope,
            projectPath: project.path,
          )
          projectDependency.projectDependencyArtifacts = it.projectConfiguration.allArtifacts.files.collect {it.path}
          result.add(projectDependency)
        }
        else if (it instanceof Dependency) {
          def artifactsResult = artifactMap.get(toMyModuleIdentifier(it.name, it.group))
          if (artifactsResult && !artifactsResult.isEmpty()) {
            def artifact = artifactsResult.first()
            def packaging = artifact.extension ?: 'jar' //  resolvePackagingType(artifact.file);
            def classifier = artifact.classifier // resolveClassifier(it.name, it.version, artifact.file);
            File sourcesFile = resolveLibraryByPath(artifact.file, scope)?.source;
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

    return result;
  }

  private static Set<ExternalDependency> transform(
    Collection<DependencyResult> handledDependencyResults,
    Collection<DependencyResult> dependencyResults,
    Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap,
    Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap,
    String scope) {

    Set<ExternalDependency> dependencies = new LinkedHashSet<>()
    dependencyResults.each { DependencyResult dependencyResult ->

      // dependency cycles check
      if (!handledDependencyResults.contains(dependencyResult)) {
        handledDependencyResults.add(dependencyResult)

        if (dependencyResult instanceof ResolvedDependencyResult) {
          def componentResult = dependencyResult.selected
          def componentSelector = dependencyResult.requested
          def name = componentResult.moduleVersion.name
          def group = componentResult.moduleVersion.group
          def version = componentResult.moduleVersion.version
          def selectionReason = componentResult.selectionReason.description
          if (componentSelector instanceof ProjectComponentSelector) {
            final dependency = new DefaultExternalProjectDependency(
              name: name,
              group: group,
              version: version,
              scope: scope,
              selectionReason: selectionReason,
              projectPath: componentSelector.projectPath
            )
            dependency.projectDependencyArtifacts = artifactMap.get(componentResult.moduleVersion).collect {it.file.path}
            if (componentResult != dependencyResult.from) {
              dependency.dependencies.addAll(
                transform(handledDependencyResults, componentResult.dependencies, artifactMap, componentResultsMap, scope)
              )
            }

            dependencies.add(dependency)
          }
          if (componentSelector instanceof ModuleComponentSelector) {
            def artifacts = artifactMap.get(componentResult.moduleVersion)
            def artifact = artifacts?.find { true }

            if (artifacts?.isEmpty()) {
              dependencies.addAll(
                transform(handledDependencyResults, componentResult.dependencies, artifactMap, componentResultsMap, scope)
              )
            }
            boolean first = true
            artifacts?.each {
              artifact = it
              def packaging = it.extension ?: 'jar'
              def classifier = it.classifier
              final dependency = new DefaultExternalLibraryDependency(
                name: name,
                group: group,
                packaging: packaging,
                classifier: classifier,
                version: version,
                scope: scope,
                selectionReason: selectionReason,
                file: artifact.file
              )

              def artifactsResult = componentResultsMap.get(toComponentIdentifier(componentResult.moduleVersion))
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
              if (first) {
                dependency.dependencies.addAll(
                  transform(handledDependencyResults, componentResult.dependencies, artifactMap, componentResultsMap, scope)
                )
                first = false
              }

              dependencies.add(dependency)
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

  private static toMyModuleIdentifier(ModuleVersionIdentifier id) {
    return new MyModuleIdentifier(name: id.getName(), group: id.getGroup());
  }

  private static toMyModuleIdentifier(String name, String group) {
    return new MyModuleIdentifier(name: name, group: group);
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
