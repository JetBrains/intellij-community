// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util.resolve;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.*;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.impldep.com.google.common.collect.ArrayListMultimap;
import org.gradle.internal.impldep.com.google.common.collect.HashMultimap;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.internal.impldep.org.apache.commons.io.FilenameUtils;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.gradle.util.GradleVersion;
import org.gradle.util.Path;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Supplier;
import org.jetbrains.plugins.gradle.tooling.util.DependencyResolver;
import org.jetbrains.plugins.gradle.tooling.util.ModuleComponentIdentifierImpl;
import org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder;
import org.jetbrains.plugins.gradle.tooling.util.resolve.deprecated.DeprecatedDependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static java.util.Collections.*;

/**
 * @author Vladislav.Soroka
 */
public final class DependencyResolverImpl implements DependencyResolver {
  private static final Logger LOG = LoggerFactory.getLogger(DependencyResolverImpl.class);

  private static final boolean IS_NEW_DEPENDENCY_RESOLUTION_APPLICABLE =
    GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("4.5")) >= 0;

  @NotNull
  private final Project myProject;
  private final boolean myDownloadJavadoc;
  private final boolean myDownloadSources;
  @NotNull
  private final SourceSetCachedFinder mySourceSetFinder;

  public DependencyResolverImpl(@NotNull Project project,
                                boolean downloadJavadoc,
                                boolean downloadSources,
                                @NotNull
                                  SourceSetCachedFinder sourceSetFinder) {
    myProject = project;
    myDownloadJavadoc = downloadJavadoc;
    myDownloadSources = downloadSources;
    mySourceSetFinder = sourceSetFinder;
  }

  @ApiStatus.Internal
  public static boolean isIsNewDependencyResolutionApplicable() {
    return IS_NEW_DEPENDENCY_RESOLUTION_APPLICABLE;
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName) {
    if (!IS_NEW_DEPENDENCY_RESOLUTION_APPLICABLE) {
      //noinspection deprecation
      return new DeprecatedDependencyResolver(myProject, false, myDownloadJavadoc, myDownloadSources, mySourceSetFinder)
        .resolveDependencies(configurationName);
    }
    if (configurationName == null) return emptyList();
    Collection<ExternalDependency> dependencies = resolveDependencies(myProject.getConfigurations().findByName(configurationName), null);
    int order = 0;
    for (ExternalDependency dependency : dependencies) {
      ((AbstractExternalDependency)dependency).setClasspathOrder(++order);
    }
    return dependencies;
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    if (!IS_NEW_DEPENDENCY_RESOLUTION_APPLICABLE) {
      //noinspection deprecation
      return new DeprecatedDependencyResolver(myProject, false, myDownloadJavadoc, myDownloadSources, mySourceSetFinder)
        .resolveDependencies(configuration);
    }

    Collection<ExternalDependency> dependencies = resolveDependencies(configuration, null);
    int order = 0;
    for (ExternalDependency dependency : dependencies) {
      ((AbstractExternalDependency)dependency).setClasspathOrder(++order);
    }
    return dependencies;
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@NotNull final SourceSet sourceSet) {
    if (!IS_NEW_DEPENDENCY_RESOLUTION_APPLICABLE) {
      //noinspection deprecation
      return new DeprecatedDependencyResolver(myProject, false, myDownloadJavadoc, myDownloadSources, mySourceSetFinder)
        .resolveDependencies(sourceSet);
    }

    Collection<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();

    // resolve compile dependencies
    FileCollection compileClasspath = getCompileClasspath(sourceSet);
    Collection<? extends ExternalDependency> compileDependencies = resolveDependenciesWithDefault(
      compileClasspath, COMPILE_SCOPE,
      new Supplier<Collection<? extends ExternalDependency>>() {
        @Override
        public Collection<? extends ExternalDependency> get() {
          String configurationName = sourceSet.getCompileClasspathConfigurationName();
          Configuration configuration = myProject.getConfigurations().getByName(configurationName);
          return getDependencies(configuration, COMPILE_SCOPE);
        }
      }
    );
    // resolve runtime dependencies
    FileCollection runtimeClasspath = sourceSet.getRuntimeClasspath();
    Collection<? extends ExternalDependency> runtimeDependencies = resolveDependenciesWithDefault(
      runtimeClasspath, RUNTIME_SCOPE,
      new Supplier<Collection<? extends ExternalDependency>>() {
        @Override
        public Collection<? extends ExternalDependency> get() {
          String configurationName = sourceSet.getRuntimeClasspathConfigurationName();
          Configuration configuration = myProject.getConfigurations().getByName(configurationName);
          return getDependencies(configuration, RUNTIME_SCOPE);
        }
      }
    );

    filterRuntimeAndMarkCompileOnlyAsProvided(compileDependencies, runtimeDependencies);
    result.addAll(compileDependencies);
    result.addAll(runtimeDependencies);

    addAdditionalProvidedDependencies(sourceSet, result);

    int order = 0;
    for (ExternalDependency dependency : result) {
      ((AbstractExternalDependency)dependency).setClasspathOrder(++order);
    }
    return result;
  }

  private FileCollection getCompileClasspath(SourceSet sourceSet) {
    final String compileTaskName = sourceSet.getCompileJavaTaskName();
    Task compileTask = myProject.getTasks().findByName(compileTaskName);
    if (compileTask instanceof AbstractCompile) {
      try {
        return ((AbstractCompile)compileTask).getClasspath();
      } catch (Exception e) {
        LOG.warn("Error obtaining compile classpath for java compilation task for [" +
                 sourceSet.getName() +
                 "] in project [" +
                 myProject.getPath() +
                 "]", e);
      }
    }
    return sourceSet.getCompileClasspath();
  }

  private Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration, @Nullable String scope) {
    if (configuration == null) {
      return emptySet();
    }

    Set<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();

    ArtifactCollection artifactResults = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
      @Override
      public void execute(@NotNull ArtifactView.ViewConfiguration configuration) {
        configuration.setLenient(true);
      }
    }).getArtifacts();

    Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap = buildAuxiliaryArtifactsMap(configuration, artifactResults);
    Set<String> resolvedFiles = new HashSet<String>();
    Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies = new HashMap<String, DefaultExternalProjectDependency>();
    for (ResolvedArtifactResult artifactResult : artifactResults) {
      File artifactFile = artifactResult.getFile();
      if (!resolvedFiles.add(artifactFile.getPath())) {
        continue;
      }
      String artifactPath = mySourceSetFinder.findArtifactBySourceSetOutputDir(artifactFile.getPath());
      if (artifactPath != null) {
        artifactFile = new File(artifactPath);
        if (!resolvedFiles.add(artifactFile.getPath())) {
          continue;
        }
      }

      ComponentIdentifier componentIdentifier = artifactResult.getId().getComponentIdentifier();
      if (componentIdentifier instanceof ProjectComponentIdentifier) {
        ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier)componentIdentifier;
        if (scope == RUNTIME_SCOPE) {
          SourceSet sourceSet = mySourceSetFinder.findByArtifact(artifactFile.getPath());
          if (sourceSet != null) {
            FileCollectionDependency outputDirsRuntimeFileDependency =
              resolveSourceSetOutputDirsRuntimeFileDependency(sourceSet.getOutput());
            if (outputDirsRuntimeFileDependency != null) {
              result.add(outputDirsRuntimeFileDependency);
            }
          }
        }

        String key = artifactResult.getId().getDisplayName();
        DefaultExternalProjectDependency projectDependency = resolvedProjectDependencies.get(key);
        if (projectDependency != null) {
          Set<File> projectDependencyArtifacts = new LinkedHashSet<File>(projectDependency.getProjectDependencyArtifacts());
          projectDependencyArtifacts.add(artifactFile);
          projectDependency.setProjectDependencyArtifacts(projectDependencyArtifacts);
          Set<File> artifactSources = new LinkedHashSet<File>(projectDependency.getProjectDependencyArtifactsSources());
          artifactSources.addAll(findArtifactSources(singleton(artifactFile), mySourceSetFinder));
          projectDependency.setProjectDependencyArtifactsSources(artifactSources);
        }
        else {
          projectDependency = new DefaultExternalProjectDependency();
          resolvedProjectDependencies.put(key, projectDependency);
          String projectName = projectComponentIdentifier.getProjectName(); // since 4.5
          projectDependency.setName(projectName);
          projectDependency.setScope(scope);
          projectDependency.setProjectPath(projectComponentIdentifier.getProjectPath());
          Set<File> projectArtifacts = singleton(artifactFile);
          projectDependency.setProjectDependencyArtifacts(projectArtifacts);
          projectDependency.setProjectDependencyArtifactsSources(findArtifactSources(projectArtifacts, mySourceSetFinder));
          result.add(projectDependency);
        }
      }
      else if (componentIdentifier instanceof ModuleComponentIdentifier){
        ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier)componentIdentifier;
        DefaultExternalLibraryDependency libraryDependency = new DefaultExternalLibraryDependency();
        libraryDependency.setName(moduleComponentIdentifier.getModule());
        libraryDependency.setGroup(moduleComponentIdentifier.getGroup());
        libraryDependency.setVersion(moduleComponentIdentifier.getVersion());
        libraryDependency.setFile(artifactFile);
        ComponentArtifactsResult artifactsResult = auxiliaryArtifactsMap.get(componentIdentifier);
        if (artifactsResult != null) {
          Set<ArtifactResult> sourceArtifactResults = artifactsResult.getArtifacts(SourcesArtifact.class);
          for (ArtifactResult sourceArtifactResult : sourceArtifactResults) {
            if (sourceArtifactResult instanceof ResolvedArtifactResult) {
              libraryDependency.setSource(((ResolvedArtifactResult)sourceArtifactResult).getFile());
              break;
            }
          }
          Set<ArtifactResult> javadocArtifactResults = artifactsResult.getArtifacts(JavadocArtifact.class);
          for (ArtifactResult javadocArtifactResult : javadocArtifactResults) {
            if (javadocArtifactResult instanceof ResolvedArtifactResult) {
              libraryDependency.setJavadoc(((ResolvedArtifactResult)javadocArtifactResult).getFile());
              break;
            }
          }
        }
        libraryDependency.setPackaging(FilenameUtils.getExtension(artifactResult.getFile().getAbsolutePath()));
        libraryDependency.setScope(scope);
        result.add(libraryDependency);
      }
      else {
        DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(singleton(artifactResult.getFile()));
        fileCollectionDependency.setScope(scope);
        result.add(fileCollectionDependency);
      }
    }

    for (Throwable failure : artifactResults.getFailures()) {
      if (failure instanceof ModuleVersionResolveException) {
        ComponentSelector attempted = ((ModuleVersionResolveException)failure).getSelector();
        if (attempted instanceof ModuleComponentSelector) {
          ModuleComponentSelector attemptedModule = (ModuleComponentSelector) attempted;
          DefaultUnresolvedExternalDependency externalDependency = new DefaultUnresolvedExternalDependency();
          externalDependency.setName(attemptedModule.getModule());
          externalDependency.setGroup(attemptedModule.getGroup());
          externalDependency.setVersion(attemptedModule.getVersion());
          externalDependency.setScope(scope);
          externalDependency.setFailureMessage(failure.getMessage());
          result.add(externalDependency);
        }
      }
    }

    return result;
  }


  @NotNull
  private Map<ComponentIdentifier, ComponentArtifactsResult> buildAuxiliaryArtifactsMap(@NotNull Configuration configuration,
                                                                                        ArtifactCollection mainArtifacts) {
    Set<ComponentIdentifier> components = new LinkedHashSet<ComponentIdentifier>();
    for (ResolvedArtifactResult artifact : mainArtifacts) {
      ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
      if (componentIdentifier instanceof ModuleComponentIdentifier) {
        components.add(componentIdentifier);
      }
    }
    if (components.isEmpty()) {
      return emptyMap();
    }
    List<Class<? extends Artifact>> artifactTypes = new ArrayList<Class<? extends Artifact>>(2);
    if (myDownloadSources) {
      artifactTypes.add(SourcesArtifact.class);
    }
    if (myDownloadJavadoc) {
      artifactTypes.add(JavadocArtifact.class);
    }
    boolean isBuildScriptConfiguration = myProject.getBuildscript().getConfigurations().contains(configuration);
    DependencyHandler dependencyHandler =
      isBuildScriptConfiguration ? myProject.getBuildscript().getDependencies() : myProject.getDependencies();
    Set<ComponentArtifactsResult> componentResults = dependencyHandler.createArtifactResolutionQuery()
      .forComponents(components)
      .withArtifacts(JvmLibrary.class, artifactTypes)
      .execute()
      .getResolvedComponents();

    Map<ComponentIdentifier, ComponentArtifactsResult> artifactsResultMap =
      new HashMap<ComponentIdentifier, ComponentArtifactsResult>(componentResults.size());
    for (ComponentArtifactsResult artifactsResult : componentResults) {
      artifactsResultMap.put(artifactsResult.getId(), artifactsResult);
    }
    return artifactsResultMap;
  }

  private static Collection<ExternalDependency> resolveSourceOutputFileDependencies(@NotNull SourceSetOutput sourceSetOutput,
                                                                                    @Nullable String scope) {
    Collection<ExternalDependency> result = new ArrayList<ExternalDependency>(2);
    List<File> files = new ArrayList<File>(sourceSetOutput.getClassesDirs().getFiles());
    files.add(sourceSetOutput.getResourcesDir());
    DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(files);
    fileCollectionDependency.setScope(scope);
    result.add(fileCollectionDependency);

    if (scope == RUNTIME_SCOPE) {
      ExternalDependency outputDirsRuntimeFileDependency = resolveSourceSetOutputDirsRuntimeFileDependency(sourceSetOutput);
      if (outputDirsRuntimeFileDependency != null) {
        result.add(outputDirsRuntimeFileDependency);
      }
    }
    return result;
  }

  @Nullable
  private static FileCollectionDependency resolveSourceSetOutputDirsRuntimeFileDependency(@NotNull SourceSetOutput sourceSetOutput) {
    List<File> runtimeOutputDirs = new ArrayList<File>(sourceSetOutput.getDirs().getFiles());
    if (!runtimeOutputDirs.isEmpty()) {
      DefaultFileCollectionDependency runtimeOutputDirsDependency = new DefaultFileCollectionDependency(runtimeOutputDirs);
      runtimeOutputDirsDependency.setScope(RUNTIME_SCOPE);
      runtimeOutputDirsDependency.setExcludedFromIndexing(true);
      return runtimeOutputDirsDependency;
    }
    return null;
  }

  private static void filterRuntimeAndMarkCompileOnlyAsProvided(@NotNull Collection<? extends ExternalDependency> compileDependencies,
                                                                @NotNull Collection<? extends ExternalDependency> runtimeDependencies) {
    Multimap<Collection<File>, ExternalDependency> filesToRuntimeDependenciesMap = HashMultimap.create();
    for (ExternalDependency runtimeDependency : runtimeDependencies) {
      final Collection<File> resolvedFiles = getFiles(runtimeDependency);
      filesToRuntimeDependenciesMap.put(resolvedFiles, runtimeDependency);
    }

    for (ExternalDependency compileDependency : compileDependencies) {
      final Collection<File> resolvedFiles = getFiles(compileDependency);

      Collection<ExternalDependency> dependencies = filesToRuntimeDependenciesMap.get(resolvedFiles);
      final boolean hasRuntimeDependencies = dependencies != null && !dependencies.isEmpty();

      if (hasRuntimeDependencies) {
        runtimeDependencies.removeAll(dependencies);
      }
      else {
        ((AbstractExternalDependency)compileDependency).setScope(PROVIDED_SCOPE);
      }
    }
  }

  private void addAdditionalProvidedDependencies(@NotNull SourceSet sourceSet, @NotNull Collection<ExternalDependency> result) {
    final Set<Configuration> providedConfigurations = new LinkedHashSet<Configuration>();
    if (sourceSet.getName().equals("main") && myProject.getPlugins().findPlugin(WarPlugin.class) != null) {
      Configuration providedCompile = myProject.getConfigurations().findByName("providedCompile");
      if (providedCompile != null) {
        providedConfigurations.add(providedCompile);
      }
      Configuration providedRuntime = myProject.getConfigurations().findByName("providedRuntime");
      if (providedRuntime != null) {
        providedConfigurations.add(providedRuntime);
      }
    }
    if (providedConfigurations.isEmpty()) {
      return;
    }

    Multimap<Object, ExternalDependency> filesToDependenciesMap = ArrayListMultimap.create();
    for (ExternalDependency dep : result) {
      filesToDependenciesMap.put(getFiles(dep), dep);
    }

    for (Configuration configuration : providedConfigurations) {
      Collection<ExternalDependency> providedDependencies = resolveDependencies(configuration, PROVIDED_SCOPE);
      for (Iterator<ExternalDependency> iterator = providedDependencies.iterator(); iterator.hasNext(); ) {
        ExternalDependency providedDependency = iterator.next();
        Collection<File> files = getFiles(providedDependency);
        Collection<ExternalDependency> dependencies = filesToDependenciesMap.get(files);
        if (!dependencies.isEmpty()) {
          for (ExternalDependency depForScope : dependencies) {
            ((AbstractExternalDependency)depForScope).setScope(PROVIDED_SCOPE);
          }
          iterator.remove();
        }
      }
      result.addAll(providedDependencies);
    }
  }

  @NotNull
  private Collection<? extends ExternalDependency> getDependencies(@NotNull final FileCollection fileCollection, @NotNull String scope) {
    return resolveDependenciesWithDefault(fileCollection, scope, new Supplier<Collection<? extends ExternalDependency>>() {
      @Override
      public Collection<? extends ExternalDependency> get() {
        return singleton(new DefaultFileCollectionDependency(fileCollection.getFiles()));
      }
    });
  }

  @NotNull
  private Collection<? extends ExternalDependency> resolveDependenciesWithDefault(
    @NotNull FileCollection fileCollection,
    @NotNull String scope,
    @NotNull Supplier<Collection<? extends ExternalDependency>> defaultValueProvider) {
    if (fileCollection instanceof ConfigurableFileCollection) {
      return getDependencies(((ConfigurableFileCollection)fileCollection).getFrom(), scope);
    }
    else if (fileCollection instanceof UnionFileCollection) {
      return getDependencies(((UnionFileCollection)fileCollection).getSources(), scope);
    }
    else if (fileCollection instanceof Configuration) {
      return resolveDependencies((Configuration)fileCollection, scope);
    }
    else if (fileCollection instanceof SourceSetOutput) {
      return resolveSourceOutputFileDependencies((SourceSetOutput)fileCollection, scope);
    }
    return defaultValueProvider.get();
  }

  private Collection<ExternalDependency> getDependencies(@NotNull Iterable<?> fileCollections, @NotNull String scope) {
    Collection<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();
    for (Object fileCollection : fileCollections) {
      if (fileCollection instanceof FileCollection) {
        result.addAll(getDependencies((FileCollection)fileCollection, scope));
      }
    }
    return result;
  }

  @NotNull
  public static Collection<File> getFiles(ExternalDependency dependency) {
    if (dependency instanceof ExternalLibraryDependency) {
      return singleton(((ExternalLibraryDependency)dependency).getFile());
    }
    else if (dependency instanceof FileCollectionDependency) {
      return ((FileCollectionDependency)dependency).getFiles();
    }
    else if (dependency instanceof ExternalMultiLibraryDependency) {
      return ((ExternalMultiLibraryDependency)dependency).getFiles();
    }
    else if (dependency instanceof ExternalProjectDependency) {
      return ((ExternalProjectDependency)dependency).getProjectDependencyArtifacts();
    }
    return emptySet();
  }

  @NotNull
  public static List<File> findArtifactSources(Collection<? extends File> artifactFiles, SourceSetCachedFinder sourceSetFinder) {
    List<File> artifactSources = new ArrayList<File>();
    for (File artifactFile : artifactFiles) {
      Set<File> sources = sourceSetFinder.findSourcesByArtifact(artifactFile.getPath());
      if (sources != null) {
        artifactSources.addAll(sources);
      }
    }
    return artifactSources;
  }

  public static ModuleComponentIdentifier toComponentIdentifier(ModuleVersionIdentifier id) {
    return new ModuleComponentIdentifierImpl(id.getGroup(), id.getName(), id.getVersion());
  }

  public static ModuleComponentIdentifier toComponentIdentifier(@NotNull String group, @NotNull String module, @NotNull String version) {
    return new ModuleComponentIdentifierImpl(group, module, version);
  }

}
