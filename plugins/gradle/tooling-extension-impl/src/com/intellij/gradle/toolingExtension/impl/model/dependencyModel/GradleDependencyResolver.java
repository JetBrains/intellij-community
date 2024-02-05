// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicyCache;
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.*;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.component.Artifact;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.impldep.com.google.common.collect.ArrayListMultimap;
import org.gradle.internal.impldep.com.google.common.collect.HashMultimap;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.gradle.util.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.FileCollectionDependency;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Supplier;
import org.jetbrains.plugins.gradle.tooling.util.StringUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Vladislav.Soroka
 */
public final class GradleDependencyResolver {

  private static final @NotNull String COMPILE_SCOPE = "COMPILE";
  private static final @NotNull String RUNTIME_SCOPE = "RUNTIME";
  private static final @NotNull String PROVIDED_SCOPE = "PROVIDED";

  private static final boolean IS_83_OR_BETTER = GradleVersionUtil.isCurrentGradleAtLeast("8.3");
  private static final Pattern PUNCTUATION_IN_SUFFIX_PATTERN = Pattern.compile("[\\p{Punct}\\s]+$");

  private final @NotNull ModelBuilderContext myContext;
  private final @NotNull Project myProject;
  private final @NotNull GradleDependencyDownloadPolicy myDownloadPolicy;

  private final @NotNull GradleSourceSetCachedFinder mySourceSetFinder;

  public GradleDependencyResolver(
    @NotNull ModelBuilderContext context,
    @NotNull Project project,
    @NotNull GradleDependencyDownloadPolicy downloadPolicy
  ) {
    myContext = context;
    myProject = project;
    myDownloadPolicy = downloadPolicy;

    mySourceSetFinder = GradleSourceSetCachedFinder.getInstance(context);
  }

  public GradleDependencyResolver(@NotNull ModelBuilderContext context, @NotNull Project project) {
    this(context, project, GradleDependencyDownloadPolicyCache.getInstance(context).getDependencyDownloadPolicy(project));
  }

  public @NotNull Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    Collection<ExternalDependency> dependencies = resolveDependencies(configuration, null);
    int order = 0;
    for (ExternalDependency dependency : dependencies) {
      ((AbstractExternalDependency)dependency).setClasspathOrder(++order);
    }
    return dependencies;
  }

  public @NotNull Collection<ExternalDependency> resolveDependencies(@NotNull SourceSet sourceSet) {
    Collection<ExternalDependency> result = new ArrayList<>();

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

  private @NotNull FileCollection getCompileClasspath(@NotNull SourceSet sourceSet) {
    final String compileTaskName = sourceSet.getCompileJavaTaskName();
    Task compileTask = myProject.getTasks().findByName(compileTaskName);
    if (compileTask instanceof AbstractCompile) {
      try {
        return ((AbstractCompile)compileTask).getClasspath();
      } catch (Exception e) {
        myContext.getMessageReporter().createMessage()
          .withGroup(Messages.DEPENDENCY_CLASSPATH_MODEL_GROUP)
          .withKind(Message.Kind.INTERNAL)
          .withTitle("Compile classpath resolution error")
          .withText(String.format(
            "Error obtaining compile classpath for java compilation task for [%s] in project [%s]",
            sourceSet.getName(),
            myProject.getPath()
          ))
          .withException(e)
          .reportMessage(myProject);
      }
    }
    return sourceSet.getCompileClasspath();
  }

  private static void ensureConfigurationArtifactsResolved(@NotNull Configuration configuration) {
    // The following statement should trigger parallel resolution of configuration artifacts
    // All subsequent iterations are expected to use cached results.
    try {
      ArtifactView artifactView = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
        @Override
        public void execute(@NotNull ArtifactView.ViewConfiguration configuration) {
          configuration.setLenient(true);
        }
      });
      artifactView.getArtifacts().getArtifacts();
    }
    catch (Exception ignore) {
    }
  }

  private @NotNull Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration, @Nullable String scope) {
    if (configuration == null) {
      return Collections.emptySet();
    }

    ensureConfigurationArtifactsResolved(configuration);

    LenientConfiguration lenientConfiguration = configuration.getResolvedConfiguration().getLenientConfiguration();
    Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts = new LinkedHashMap<>();
    boolean hasFailedToTransformDependencies = false;
    for (ResolvedDependency dependency : lenientConfiguration.getAllModuleDependencies()) {
      try {
        resolvedArtifacts.put(dependency, dependency.getModuleArtifacts());
      }
      catch (GradleException e) {
        hasFailedToTransformDependencies = true;
        resolvedArtifacts.put(dependency, Collections.emptySet());
      }
      catch (Exception ignore) {
        // ignore other artifact resolution exceptions
      }
    }
    Map<ModuleVersionIdentifier, ResolvedDependencyResult> transformedProjectDependenciesResultMap = new HashMap<>();
    if (hasFailedToTransformDependencies) {
      for (DependencyResult dependencyResult : configuration.getIncoming().getResolutionResult().getAllDependencies()) {
        ComponentSelector resultRequested = dependencyResult.getRequested();
        if (dependencyResult instanceof ResolvedDependencyResult && resultRequested instanceof ProjectComponentSelector) {
          ResolvedComponentResult resolvedComponentResult = ((ResolvedDependencyResult)dependencyResult).getSelected();
          ModuleVersionIdentifier selectedResultVersion = resolvedComponentResult.getModuleVersion();
          transformedProjectDependenciesResultMap.put(selectedResultVersion, (ResolvedDependencyResult)dependencyResult);
        }
      }
    }
    Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap = buildAuxiliaryArtifactsMap(configuration, resolvedArtifacts);
    Collection<FileCollectionDependency> sourceSetsOutputDirsRuntimeFileDependencies = new LinkedHashSet<>();
    Set<String> resolvedFiles = new HashSet<>();
    Collection<ExternalDependency> artifactDependencies = resolveArtifactDependencies(
      resolvedFiles, sourceSetsOutputDirsRuntimeFileDependencies, resolvedArtifacts, auxiliaryArtifactsMap,
      transformedProjectDependenciesResultMap, scope
    );
    Collection<FileCollectionDependency> otherFileDependencies = resolveOtherFileDependencies(resolvedFiles, configuration, scope);
    Collection<ExternalDependency> unresolvedDependencies = collectUnresolvedDependencies(lenientConfiguration, scope);

    Collection<ExternalDependency> result = new LinkedHashSet<>();
    result.addAll(sourceSetsOutputDirsRuntimeFileDependencies);
    result.addAll(otherFileDependencies);
    result.addAll(artifactDependencies);
    result.addAll(unresolvedDependencies);

    int order = 0;
    for (ExternalDependency dependency : result) {
      ((AbstractExternalDependency)dependency).setClasspathOrder(++order);
    }
    return result;
  }

  private @NotNull Collection<ExternalDependency> resolveArtifactDependencies(
    @NotNull Set<String> resolvedFiles, // mutable
    @NotNull Collection<FileCollectionDependency> sourceSetsOutputDirsRuntimeFileDependencies, // mutable
    @NotNull Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts,
    @NotNull Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap,
    @NotNull Map<ModuleVersionIdentifier, ResolvedDependencyResult> transformedProjectDependenciesResultMap,
    @Nullable String scope
  ) {
    Collection<ExternalDependency> artifactDependencies = new LinkedHashSet<>();
    Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies = new HashMap<>();
    for (Map.Entry<ResolvedDependency, Set<ResolvedArtifact>> resolvedDependencySetEntry : resolvedArtifacts.entrySet()) {
      ResolvedDependency resolvedDependency = resolvedDependencySetEntry.getKey();
      Set<ResolvedArtifact> artifacts = resolvedDependencySetEntry.getValue();
      for (ResolvedArtifact artifact : artifacts) {
        File artifactFile = resolveArtifactFile(resolvedFiles, artifact);
        if (artifactFile == null) {
          continue;
        }
        ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
        if (componentIdentifier instanceof ProjectComponentIdentifier) {
          if (RUNTIME_SCOPE.equals(scope)) {
            SourceSet sourceSet = mySourceSetFinder.findByArtifact(artifactFile.getPath());
            if (sourceSet != null) {
              FileCollectionDependency outputDirsRuntimeFileDependency =
                resolveSourceSetOutputDirsRuntimeFileDependency(sourceSet.getOutput());
              if (outputDirsRuntimeFileDependency != null) {
                sourceSetsOutputDirsRuntimeFileDependencies.add(outputDirsRuntimeFileDependency);
              }
            }
          }
          ExternalDependency dependency = resolveProjectDependency(
            resolvedProjectDependencies, resolvedDependency, artifactFile, (ProjectComponentIdentifier)componentIdentifier, scope
          );
          if (dependency != null) {
            artifactDependencies.add(dependency);
          }
        }
        else {
          ExternalDependency dependency = resolveLibraryDependency(artifact, artifactFile, auxiliaryArtifactsMap, scope);
          artifactDependencies.add(dependency);
        }
      }
      if (artifacts.isEmpty()) {
        ExternalDependency dependency = resolveFailedToTransformProjectDependency(
          resolvedProjectDependencies, resolvedDependency, transformedProjectDependenciesResultMap, scope
        );
        if (dependency != null) {
          artifactDependencies.add(dependency);
        }
      }
    }
    return artifactDependencies;
  }

  // Returns null if artifact was already resolved
  private @Nullable File resolveArtifactFile(
    @NotNull Set<String> resolvedFiles, // mutable
    @NotNull ResolvedArtifact artifact
  ) {
    File artifactFile = artifact.getFile();
    if (resolvedFiles.contains(artifactFile.getPath())) {
      return null;
    }
    resolvedFiles.add(artifactFile.getPath());
    String artifactPath = mySourceSetFinder.findArtifactBySourceSetOutputDir(artifactFile.getPath());
    if (artifactPath != null) {
      artifactFile = new File(artifactPath);
      if (resolvedFiles.contains(artifactFile.getPath())) {
        return null;
      }
      resolvedFiles.add(artifactFile.getPath());
    }
    return artifactFile;
  }

  private static @NotNull String getProjectDependencyKey(
    @NotNull ResolvedDependency resolvedDependency,
    @NotNull ProjectComponentIdentifier projectComponentIdentifier
  ) {
    String buildName = getBuildName(projectComponentIdentifier);
    String projectPath = projectComponentIdentifier.getProjectPath();
    return buildName + "_" + projectPath + "_" + resolvedDependency.getConfiguration();
  }

  private static @NotNull String getProjectDependencyKey(
    @NotNull ResolvedDependency resolvedDependency,
    @NotNull ResolvedDependencyResult resolvedDependencyResult
  ) {
    ProjectComponentSelector dependencyResultRequested = (ProjectComponentSelector)resolvedDependencyResult.getRequested();
    String projectPath = dependencyResultRequested.getProjectPath();
    return projectPath + "_" + resolvedDependency.getConfiguration();
  }

  // Returns null if artifact was already resolved
  private @Nullable DefaultExternalProjectDependency resolveProjectDependency(
    @NotNull Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies, // mutable
    @NotNull ResolvedDependency resolvedDependency,
    @NotNull File artifactFile,
    @NotNull ProjectComponentIdentifier projectComponentIdentifier,
    @Nullable String scope
  ) {
    String key = getProjectDependencyKey(resolvedDependency, projectComponentIdentifier);
    DefaultExternalProjectDependency cachedProjectDependency = resolvedProjectDependencies.get(key);

    if (cachedProjectDependency != null) {
      Set<File> projectDependencyArtifacts = new LinkedHashSet<>(cachedProjectDependency.getProjectDependencyArtifacts());
      projectDependencyArtifacts.add(artifactFile);
      cachedProjectDependency.setProjectDependencyArtifacts(projectDependencyArtifacts);
      Set<File> artifactSources = new LinkedHashSet<>(cachedProjectDependency.getProjectDependencyArtifactsSources());
      artifactSources.addAll(mySourceSetFinder.findArtifactSources(artifactFile));
      cachedProjectDependency.setProjectDependencyArtifactsSources(artifactSources);
      return null;
    }

    DefaultExternalProjectDependency projectDependency = new DefaultExternalProjectDependency();
    resolvedProjectDependencies.put(key, projectDependency);

    projectDependency.setName(projectComponentIdentifier.getProjectName());
    projectDependency.setGroup(resolvedDependency.getModuleGroup());
    projectDependency.setVersion(resolvedDependency.getModuleVersion());
    projectDependency.setScope(scope);
    projectDependency.setProjectPath(projectComponentIdentifier.getProjectPath());
    projectDependency.setConfigurationName(resolvedDependency.getConfiguration());
    projectDependency.setProjectDependencyArtifacts(Collections.singleton(artifactFile));
    projectDependency.setProjectDependencyArtifactsSources(mySourceSetFinder.findArtifactSources(artifactFile));

    return projectDependency;
  }

  private static @NotNull DefaultExternalLibraryDependency resolveLibraryDependency(
    @NotNull ResolvedArtifact artifact,
    @NotNull File artifactFile,
    @NotNull Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap,
    @Nullable String scope
  ) {
    DefaultExternalLibraryDependency libraryDependency = new DefaultExternalLibraryDependency();

    ModuleVersionIdentifier moduleVersionIdentifier = artifact.getModuleVersion().getId();
    libraryDependency.setName(moduleVersionIdentifier.getName());
    libraryDependency.setGroup(moduleVersionIdentifier.getGroup());
    libraryDependency.setVersion(moduleVersionIdentifier.getVersion());
    libraryDependency.setFile(artifactFile);

    ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
    ComponentArtifactsResult artifactsResult = auxiliaryArtifactsMap.get(componentIdentifier);
    if (artifactsResult != null) {
      Set<File> sourcesArtifactFiles = getResolvedAuxiliaryArtifactFiles(artifactsResult, SourcesArtifact.class);
      File sourcesFile = chooseAuxiliaryArtifactFile(artifactFile, sourcesArtifactFiles);
      if (sourcesFile != null) {
        libraryDependency.setSource(sourcesFile);
      }
      Set<File> javadocArtifactFiles = getResolvedAuxiliaryArtifactFiles(artifactsResult, JavadocArtifact.class);
      File javadocFile = chooseAuxiliaryArtifactFile(artifactFile, javadocArtifactFiles);
      if (javadocFile != null) {
        libraryDependency.setJavadoc(javadocFile);
      }
    }
    if (artifact.getExtension() != null) {
      libraryDependency.setPackaging(artifact.getExtension());
    }
    libraryDependency.setScope(scope);
    libraryDependency.setClassifier(artifact.getClassifier());

    return libraryDependency;
  }

  // Returns null if dependency was already resolved or cannot be resolved
  private @Nullable ExternalProjectDependency resolveFailedToTransformProjectDependency(
    @NotNull Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies, // mutable
    @NotNull ResolvedDependency resolvedDependency,
    @NotNull Map<ModuleVersionIdentifier, ResolvedDependencyResult> transformedProjectDependenciesResultMap,
    @Nullable String scope
  ) {
    ModuleVersionIdentifier moduleVersionIdentifier = resolvedDependency.getModule().getId();
    ResolvedDependencyResult resolvedDependencyResult = transformedProjectDependenciesResultMap.get(moduleVersionIdentifier);
    if (resolvedDependencyResult == null) return null;

    String key = getProjectDependencyKey(resolvedDependency, resolvedDependencyResult);
    DefaultExternalProjectDependency cachedProjectDependency = resolvedProjectDependencies.get(key);
    if (cachedProjectDependency != null) return null;

    DefaultExternalProjectDependency projectDependency = new DefaultExternalProjectDependency();
    resolvedProjectDependencies.put(key, projectDependency);
    ProjectComponentSelector dependencyResultRequested = (ProjectComponentSelector)resolvedDependencyResult.getRequested();
    String projectPath = dependencyResultRequested.getProjectPath();
    String projectName = Path.path(projectPath).getName();
    projectDependency.setName(projectName);
    projectDependency.setGroup(resolvedDependency.getModuleGroup());
    projectDependency.setVersion(resolvedDependency.getModuleVersion());
    projectDependency.setScope(scope);
    projectDependency.setProjectPath(projectPath);
    projectDependency.setConfigurationName(resolvedDependency.getConfiguration());

    Project project = myProject.findProject(projectPath);
    if (project == null) return null;
    Configuration configuration = project.getConfigurations().findByName(resolvedDependency.getConfiguration());
    if (configuration == null) return null;
    Set<File> projectArtifacts = configuration.getArtifacts().getFiles().getFiles();
    projectDependency.setProjectDependencyArtifacts(projectArtifacts);
    projectDependency.setProjectDependencyArtifactsSources(mySourceSetFinder.findArtifactSources(projectArtifacts));

    return projectDependency;
  }

  private @NotNull Map<ComponentIdentifier, ComponentArtifactsResult> buildAuxiliaryArtifactsMap(
    @NotNull Configuration configuration,
    @NotNull Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts
  ) {
    List<ComponentIdentifier> components = new ArrayList<>();
    for (Collection<ResolvedArtifact> artifacts : resolvedArtifacts.values()) {
      for (ResolvedArtifact artifact : artifacts) {
        if (artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier) continue;
        ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
        components.add(DefaultModuleComponentIdentifier.create(id));
      }
    }

    if (components.isEmpty()) {
      return Collections.emptyMap();
    }

    List<Class<? extends Artifact>> artifactTypes = new ArrayList<>(2);
    if (myDownloadPolicy.isDownloadSources()) {
      artifactTypes.add(SourcesArtifact.class);
    }
    if (myDownloadPolicy.isDownloadJavadoc()) {
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

    Map<ComponentIdentifier, ComponentArtifactsResult> result = new HashMap<>(componentResults.size());
    for (ComponentArtifactsResult artifactsResult : componentResults) {
      result.put(artifactsResult.getId(), artifactsResult);
    }
    return result;
  }

  private static @NotNull Collection<FileCollectionDependency> resolveOtherFileDependencies(
    @NotNull Set<String> resolvedFiles, // mutable
    @NotNull Configuration configuration,
    @Nullable String scope
  ) {
    ArtifactView artifactView = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
      @Override
      public void execute(@SuppressWarnings("NullableProblems") ArtifactView.ViewConfiguration configuration) {
        configuration.setLenient(true);
        configuration.componentFilter(new Spec<ComponentIdentifier>() {
          @Override
          public boolean isSatisfiedBy(ComponentIdentifier identifier) {
            return !(identifier instanceof ProjectComponentIdentifier || identifier instanceof ModuleComponentIdentifier);
          }
        });
      }
    });
    Set<ResolvedArtifactResult> artifactResults = artifactView.getArtifacts().getArtifacts();
    Collection<FileCollectionDependency> result = new LinkedHashSet<>();
    for (ResolvedArtifactResult artifactResult : artifactResults) {
      File file = artifactResult.getFile();
      if (resolvedFiles.contains(file.getPath())) {
        continue;
      }
      resolvedFiles.add(file.getPath());

      DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(Collections.singleton(file));
      fileCollectionDependency.setScope(scope);
      result.add(fileCollectionDependency);
    }
    return result;
  }

  private static @NotNull Collection<ExternalDependency> collectUnresolvedDependencies(
    @NotNull LenientConfiguration lenientConfiguration,
    @Nullable String scope
  ) {
    Collection<ExternalDependency> result = new LinkedHashSet<>();
    Set<UnresolvedDependency> unresolvedModuleDependencies = lenientConfiguration.getUnresolvedModuleDependencies();
    for (UnresolvedDependency unresolvedDependency : unresolvedModuleDependencies) {
      MyModuleVersionSelector moduleVersionSelector = null;
      Throwable problem = unresolvedDependency.getProblem();
      if (problem.getCause() != null) {
        problem = problem.getCause();
      }
      try {
        if (problem instanceof ModuleVersionResolveException) {
          ComponentSelector componentSelector = ((ModuleVersionResolveException)problem).getSelector();
          if (componentSelector instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleComponentSelector = (ModuleComponentSelector)componentSelector;
            moduleVersionSelector = new MyModuleVersionSelector(
              moduleComponentSelector.getModule(),
              moduleComponentSelector.getGroup(),
              moduleComponentSelector.getVersion()
            );
          }
        }
      }
      catch (Throwable ignore) {
      }
      if (moduleVersionSelector == null) {
        problem = unresolvedDependency.getProblem();
        ModuleVersionSelector selector = unresolvedDependency.getSelector();
        moduleVersionSelector = new MyModuleVersionSelector(selector.getName(), selector.getGroup(), selector.getVersion());
      }
      DefaultUnresolvedExternalDependency dependency = new DefaultUnresolvedExternalDependency();
      dependency.setName(moduleVersionSelector.name);
      dependency.setGroup(moduleVersionSelector.group);
      dependency.setVersion(moduleVersionSelector.version);
      dependency.setScope(scope);
      dependency.setFailureMessage(problem.getMessage());
      result.add(dependency);
    }
    return result;
  }

  private static @NotNull Collection<ExternalDependency> resolveSourceOutputFileDependencies(
    @NotNull SourceSetOutput sourceSetOutput,
    @Nullable String scope
  ) {
    Collection<ExternalDependency> result = new ArrayList<>(2);
    List<File> files = new ArrayList<>(sourceSetOutput.getClassesDirs().getFiles());
    files.add(sourceSetOutput.getResourcesDir());
    DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(files);
    fileCollectionDependency.setScope(scope);
    result.add(fileCollectionDependency);

    if (RUNTIME_SCOPE.equals(scope)) {
      ExternalDependency outputDirsRuntimeFileDependency = resolveSourceSetOutputDirsRuntimeFileDependency(sourceSetOutput);
      if (outputDirsRuntimeFileDependency != null) {
        result.add(outputDirsRuntimeFileDependency);
      }
    }
    return result;
  }

  private static @Nullable FileCollectionDependency resolveSourceSetOutputDirsRuntimeFileDependency(
    @NotNull SourceSetOutput sourceSetOutput
  ) {
    List<File> runtimeOutputDirs = new ArrayList<>(sourceSetOutput.getDirs().getFiles());
    if (!runtimeOutputDirs.isEmpty()) {
      DefaultFileCollectionDependency runtimeOutputDirsDependency = new DefaultFileCollectionDependency(runtimeOutputDirs);
      runtimeOutputDirsDependency.setScope(RUNTIME_SCOPE);
      runtimeOutputDirsDependency.setExcludedFromIndexing(true);
      return runtimeOutputDirsDependency;
    }
    return null;
  }

  private static void filterRuntimeAndMarkCompileOnlyAsProvided(
    @NotNull Collection<? extends ExternalDependency> compileDependencies,
    @NotNull Collection<? extends ExternalDependency> runtimeDependencies
  ) {
    Multimap<Collection<File>, ExternalDependency> filesToRuntimeDependenciesMap = HashMultimap.create();
    for (ExternalDependency runtimeDependency : runtimeDependencies) {
      final Collection<File> resolvedFiles = getFiles(runtimeDependency);
      filesToRuntimeDependenciesMap.put(resolvedFiles, runtimeDependency);
    }

    for (ExternalDependency compileDependency : compileDependencies) {
      final Collection<File> resolvedFiles = getFiles(compileDependency);

      Collection<ExternalDependency> dependencies = filesToRuntimeDependenciesMap.get(resolvedFiles);
      final boolean hasRuntimeDependencies = !dependencies.isEmpty();

      if (hasRuntimeDependencies) {
        runtimeDependencies.removeAll(dependencies);
      }
      else {
        ((AbstractExternalDependency)compileDependency).setScope(PROVIDED_SCOPE);
      }
    }
  }

  private static @NotNull Set<File> getResolvedAuxiliaryArtifactFiles(
    @NotNull ComponentArtifactsResult artifactsResult,
    @NotNull Class<? extends Artifact> artifactType
  ) {
    return artifactsResult.getArtifacts(artifactType).stream()
      .filter(ResolvedArtifactResult.class::isInstance)
      .map(ResolvedArtifactResult.class::cast)
      .map(ResolvedArtifactResult::getFile)
      .collect(Collectors.toSet());
  }

  /**
   * If there are multiple auxiliary artifacts for the same `ComponentIdentifier`, we have to choose the "best match" based on file names.
   * For context, see IDEA-332969
   * 1. Find the common suffix of every auxiliary artifact (e.g. "-sources.jar" or ".src.jar") and ignore it going forward
   * 2. Find the common suffix of the main artifact with the auxiliary artifacts (e.g. ".jar") and ignore it going forward
   * 3. Filter the auxiliary artifacts, keeping only those that have the longest common prefix with the main artifact (not counting any
   * punctuation or whitespace at the end of the common prefix)
   * 4. Deterministically choose from the remaining auxiliary artifacts, preferring the shortest overall file name (the longer ones likely
   * belong to some different main artifact that also has a longer file name)
   *
   * @param main        path to the dependency Jar file
   * @param auxiliaries set of artifacts associated with this library
   * @return best match, null otherwise
   */
  @VisibleForTesting
  public static @Nullable File chooseAuxiliaryArtifactFile(@NotNull File main, @NotNull Set<File> auxiliaries) {
    Iterator<File> auxiliariesIterator = auxiliaries.iterator();
    if (!auxiliariesIterator.hasNext()) {
      return null;
    }

    File firstAuxiliary = auxiliariesIterator.next();
    if (!auxiliariesIterator.hasNext()) {
      return firstAuxiliary;
    }

    String mainName = main.getName();
    String firstAuxiliaryName = firstAuxiliary.getName();

    int commonSuffixOfAuxiliaries = firstAuxiliaryName.length();
    do {
      File nextAuxiliary = auxiliariesIterator.next();
      int commonSuffix = StringUtils.commonSuffixLength(firstAuxiliaryName, nextAuxiliary.getName());
      if (commonSuffix < commonSuffixOfAuxiliaries) {
        commonSuffixOfAuxiliaries = commonSuffix;
      }
    } while (auxiliariesIterator.hasNext());

    int commonSuffixOfMainAndAuxiliaries =
      Math.min(commonSuffixOfAuxiliaries, StringUtils.commonSuffixLength(mainName, firstAuxiliaryName));
    String mainSuffixlessName = mainName.substring(0, mainName.length() - commonSuffixOfMainAndAuxiliaries);

    int commonPrefixOfMainAndShortlistedAuxiliaries = 0;
    TreeMap<String, File> shortlistedAuxiliariesBySuffixlessName =
      new TreeMap<>(Comparator.comparingInt(String::length).thenComparing(String::compareTo));
    for (File auxiliary : auxiliaries) {
      String auxiliaryName = auxiliary.getName();
      String auxiliarySuffixlessName = auxiliaryName.substring(0, auxiliaryName.length() - commonSuffixOfAuxiliaries);
      int commonPrefixNaive = StringUtils.commonPrefixLength(mainSuffixlessName, auxiliarySuffixlessName);
      Matcher commonPrefixExcessMatcher = PUNCTUATION_IN_SUFFIX_PATTERN.matcher(auxiliarySuffixlessName).region(0, commonPrefixNaive);
      int commonPrefix = commonPrefixExcessMatcher.find() ? commonPrefixExcessMatcher.start() : commonPrefixNaive;
      if (commonPrefix >= commonPrefixOfMainAndShortlistedAuxiliaries) {
        if (commonPrefix > commonPrefixOfMainAndShortlistedAuxiliaries) {
          commonPrefixOfMainAndShortlistedAuxiliaries = commonPrefix;
          shortlistedAuxiliariesBySuffixlessName.clear();
        }
        shortlistedAuxiliariesBySuffixlessName.put(auxiliarySuffixlessName, auxiliary);
      }
    }

    return shortlistedAuxiliariesBySuffixlessName.firstEntry().getValue();
  }

  private void addAdditionalProvidedDependencies(@NotNull SourceSet sourceSet, @NotNull Collection<ExternalDependency> result) {
    final Set<Configuration> providedConfigurations = new LinkedHashSet<>();
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

  private @NotNull Collection<? extends ExternalDependency> getDependencies(
    @NotNull FileCollection fileCollection,
    @NotNull String scope
  ) {
    return resolveDependenciesWithDefault(fileCollection, scope, new Supplier<Collection<? extends ExternalDependency>>() {
      @Override
      public Collection<? extends ExternalDependency> get() {
        return Collections.singleton(new DefaultFileCollectionDependency(fileCollection.getFiles()));
      }
    });
  }

  private @NotNull Collection<? extends ExternalDependency> resolveDependenciesWithDefault(
    @NotNull FileCollection fileCollection,
    @NotNull String scope,
    @NotNull Supplier<Collection<? extends ExternalDependency>> defaultValueProvider
  ) {
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

  private @NotNull Collection<ExternalDependency> getDependencies(
    @NotNull Iterable<?> fileCollections,
    @NotNull String scope
  ) {
    Set<ExternalDependency> result = new LinkedHashSet<>();
    for (Object fileCollection : fileCollections) {
      if (fileCollection instanceof FileCollection) {
        result.addAll(getDependencies((FileCollection)fileCollection, scope));
      }
    }
    return result;
  }

  public static @NotNull Collection<File> getFiles(@NotNull ExternalDependency dependency) {
    if (dependency instanceof ExternalLibraryDependency) {
      return Collections.singleton(((ExternalLibraryDependency)dependency).getFile());
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
    return Collections.emptySet();
  }

  private static @NotNull String getBuildName(@NotNull ProjectComponentIdentifier projectComponentIdentifier) {
    BuildIdentifier buildIdentifier = projectComponentIdentifier.getBuild();
    if (IS_83_OR_BETTER) {
      return buildIdentifier.getBuildPath();
    }
    //noinspection deprecation
    return buildIdentifier.getName();
  }

  private static final class MyModuleVersionSelector {
    private final String name;
    private final String group;
    private final String version;

    private MyModuleVersionSelector(String name, String group, String version) {
      this.name = name;
      this.group = group;
      this.version = version;
    }
  }
}
