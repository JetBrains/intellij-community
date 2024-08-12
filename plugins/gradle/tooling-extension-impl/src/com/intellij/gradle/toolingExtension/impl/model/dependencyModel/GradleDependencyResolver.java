// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicyCache;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex.GradleSourceSetArtifactIndex;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.*;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.component.Artifact;
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
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
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

  private static final boolean IS_83_OR_BETTER = GradleVersionUtil.isCurrentGradleAtLeast("8.3");
  private static final Pattern PUNCTUATION_IN_SUFFIX_PATTERN = Pattern.compile("[\\p{Punct}\\s]+$");

  private final @NotNull Project myProject;
  private final @NotNull GradleDependencyDownloadPolicy myDownloadPolicy;

  private final @NotNull GradleSourceSetArtifactIndex mySourceSetArtifactIndex;

  public GradleDependencyResolver(
    @NotNull ModelBuilderContext context,
    @NotNull Project project,
    @NotNull GradleDependencyDownloadPolicy downloadPolicy
  ) {
    myProject = project;
    myDownloadPolicy = downloadPolicy;

    mySourceSetArtifactIndex = GradleSourceSetArtifactIndex.getInstance(context);
  }

  public GradleDependencyResolver(@NotNull ModelBuilderContext context, @NotNull Project project) {
    this(context, project, GradleDependencyDownloadPolicyCache.getInstance(context).getDependencyDownloadPolicy(project));
  }

  private static @NotNull Set<ResolvedArtifactResult> resolveConfigurationDependencies(@NotNull Configuration configuration) {
    // The following statement should trigger parallel resolution of configuration artifacts
    // All subsequent iterations are expected to use cached results.
    try {
      ArtifactView artifactView = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
        @Override
        public void execute(@NotNull ArtifactView.ViewConfiguration configuration) {
          configuration.setLenient(true);
        }
      });
      return artifactView.getArtifacts().getArtifacts();
    }
    catch (Exception ignore) {
    }
    return Collections.emptySet();
  }

  public @NotNull Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    if (configuration == null) {
      return Collections.emptySet();
    }
    // configurationDependencies can be empty, for example, in the case of a composite build. We should continue resolution anyway.
    Set<ResolvedArtifactResult> configurationDependencies = resolveConfigurationDependencies(configuration);

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
    Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap = resolveAuxiliaryArtifacts(configuration, resolvedArtifacts);
    Set<String> resolvedFiles = new HashSet<>();
    Collection<ExternalDependency> artifactDependencies = resolveArtifactDependencies(
      resolvedFiles, resolvedArtifacts, auxiliaryArtifactsMap, transformedProjectDependenciesResultMap
    );
    Collection<FileCollectionDependency> otherFileDependencies = resolveOtherFileDependencies(resolvedFiles, configurationDependencies);
    Collection<ExternalDependency> unresolvedDependencies = collectUnresolvedDependencies(lenientConfiguration);

    Collection<ExternalDependency> result = new LinkedHashSet<>();
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
    @NotNull Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts,
    @NotNull Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap,
    @NotNull Map<ModuleVersionIdentifier, ResolvedDependencyResult> transformedProjectDependenciesResultMap
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
          ExternalDependency dependency = resolveProjectDependency(
            resolvedProjectDependencies, resolvedDependency, artifactFile, (ProjectComponentIdentifier)componentIdentifier
          );
          if (dependency != null) {
            artifactDependencies.add(dependency);
          }
        }
        else {
          ExternalDependency dependency = resolveLibraryDependency(artifact, artifactFile, auxiliaryArtifactsMap);
          artifactDependencies.add(dependency);
        }
      }
      if (artifacts.isEmpty()) {
        ExternalDependency dependency = resolveFailedToTransformProjectDependency(
          resolvedProjectDependencies, resolvedDependency, transformedProjectDependenciesResultMap
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
    String artifactPath = mySourceSetArtifactIndex.findArtifactBySourceSetOutputDir(artifactFile.getPath());
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
    @NotNull ProjectComponentIdentifier projectComponentIdentifier
  ) {
    String key = getProjectDependencyKey(resolvedDependency, projectComponentIdentifier);
    DefaultExternalProjectDependency cachedProjectDependency = resolvedProjectDependencies.get(key);

    if (cachedProjectDependency != null) {
      Set<File> projectDependencyArtifacts = new LinkedHashSet<>(cachedProjectDependency.getProjectDependencyArtifacts());
      projectDependencyArtifacts.add(artifactFile);
      cachedProjectDependency.setProjectDependencyArtifacts(projectDependencyArtifacts);
      Set<File> artifactSources = new LinkedHashSet<>(cachedProjectDependency.getProjectDependencyArtifactsSources());
      artifactSources.addAll(mySourceSetArtifactIndex.findArtifactSources(artifactFile));
      cachedProjectDependency.setProjectDependencyArtifactsSources(artifactSources);
      return null;
    }

    DefaultExternalProjectDependency projectDependency = new DefaultExternalProjectDependency();
    resolvedProjectDependencies.put(key, projectDependency);

    projectDependency.setName(projectComponentIdentifier.getProjectName());
    projectDependency.setGroup(resolvedDependency.getModuleGroup());
    projectDependency.setVersion(resolvedDependency.getModuleVersion());
    projectDependency.setProjectPath(projectComponentIdentifier.getProjectPath());
    projectDependency.setConfigurationName(resolvedDependency.getConfiguration());
    projectDependency.setProjectDependencyArtifacts(Collections.singleton(artifactFile));
    projectDependency.setProjectDependencyArtifactsSources(mySourceSetArtifactIndex.findArtifactSources(artifactFile));

    return projectDependency;
  }

  private static @NotNull DefaultExternalLibraryDependency resolveLibraryDependency(
    @NotNull ResolvedArtifact artifact,
    @NotNull File artifactFile,
    @NotNull Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap
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
    libraryDependency.setClassifier(artifact.getClassifier());

    return libraryDependency;
  }

  // Returns null if dependency was already resolved or cannot be resolved
  private @Nullable ExternalProjectDependency resolveFailedToTransformProjectDependency(
    @NotNull Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies, // mutable
    @NotNull ResolvedDependency resolvedDependency,
    @NotNull Map<ModuleVersionIdentifier, ResolvedDependencyResult> transformedProjectDependenciesResultMap
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
    projectDependency.setProjectPath(projectPath);
    projectDependency.setConfigurationName(resolvedDependency.getConfiguration());

    Project project = myProject.findProject(projectPath);
    if (project == null) return null;
    Configuration configuration = project.getConfigurations().findByName(resolvedDependency.getConfiguration());
    if (configuration == null) return null;
    Set<File> projectArtifacts = configuration.getArtifacts().getFiles().getFiles();
    projectDependency.setProjectDependencyArtifacts(projectArtifacts);
    projectDependency.setProjectDependencyArtifactsSources(mySourceSetArtifactIndex.findArtifactSources(projectArtifacts));

    return projectDependency;
  }

  private @NotNull Map<ComponentIdentifier, ComponentArtifactsResult> resolveAuxiliaryArtifacts(
    @NotNull Configuration configuration,
    @NotNull Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts
  ) {
    boolean downloadSources = myDownloadPolicy.isDownloadSources();
    boolean downloadJavadoc = myDownloadPolicy.isDownloadJavadoc();
    if (!(downloadSources || downloadJavadoc)) {
      return Collections.emptyMap();
    }
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
    if (downloadSources) {
      artifactTypes.add(SourcesArtifact.class);
    }
    if (downloadJavadoc) {
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

  // resolve generated dependencies such as annotation processing build roots and compilation result
  private static @NotNull Collection<FileCollectionDependency> resolveOtherFileDependencies(
    @NotNull Set<String> resolvedFiles, // mutable
    @NotNull Set<ResolvedArtifactResult> configurationDependencies
  ) {
    Collection<FileCollectionDependency> result = new LinkedHashSet<>();
    for (ResolvedArtifactResult dependency : configurationDependencies) {
      ComponentIdentifier identifier = dependency.getId().getComponentIdentifier();
      // libraries, modules and subprojects are already well known
      if (identifier instanceof LibraryBinaryIdentifier
          || identifier instanceof ModuleComponentIdentifier
          || identifier instanceof ProjectComponentIdentifier) {
        continue;
      }
      File file = dependency.getFile();
      String path = file.getPath();
      if (resolvedFiles.add(path)) {
        result.add(new DefaultFileCollectionDependency(Collections.singleton(file)));
      }
    }
    return result;
  }

  private static @NotNull Collection<ExternalDependency> collectUnresolvedDependencies(
    @NotNull LenientConfiguration lenientConfiguration
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
      dependency.setFailureMessage(problem.getMessage());
      result.add(dependency);
    }
    return result;
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
  static @Nullable File chooseAuxiliaryArtifactFile(@NotNull File main, @NotNull Set<File> auxiliaries) {
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
