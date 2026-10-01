// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicyCache;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.AuxiliaryArtifactProvider;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.AuxiliaryArtifactResolver;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.AuxiliaryArtifactResolverImpl;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.AuxiliaryConfigurationArtifacts;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.LegacyAuxiliaryArtifactResolver;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex.GradleSourceSetArtifactIndex;
import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.specs.Spec;
import org.gradle.util.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.AbstractExternalDependency;
import org.jetbrains.plugins.gradle.model.DefaultExternalLibraryDependency;
import org.jetbrains.plugins.gradle.model.DefaultExternalProjectDependency;
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency;
import org.jetbrains.plugins.gradle.model.DefaultUnresolvedExternalDependency;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency;
import org.jetbrains.plugins.gradle.model.FileCollectionDependency;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public final class GradleDependencyResolver {

  private static final boolean IS_52_OR_BETTER = GradleVersionUtil.isCurrentGradleAtLeast("5.2");
  private static final boolean IS_83_OR_BETTER = GradleVersionUtil.isCurrentGradleAtLeast("8.3");

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

  private static @Nullable ArtifactCollection resolveConfigurationDependencies(@NotNull Configuration configuration,
                                                                               Set<String> allowedDependencyGroups) {
    // The following statement should trigger parallel resolution of configuration artifacts
    // All subsequent iterations are expected to use cached results.
    try {
      ArtifactView artifactView = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
        @Override
        public void execute(@NotNull ArtifactView.ViewConfiguration configuration) {
          configuration.setLenient(true);

          if (!allowedDependencyGroups.isEmpty()) {
            configuration.componentFilter(new Spec<ComponentIdentifier>() {
              @Override
              public boolean isSatisfiedBy(ComponentIdentifier componentIdentifier) {
                if (componentIdentifier instanceof ModuleComponentIdentifier) {
                  return allowedDependencyGroups.contains(((ModuleComponentIdentifier)componentIdentifier).getGroup());
                }
                return false;
              }
            });
          }
        }
      });
      return artifactView.getArtifacts();
    }
    catch (Exception ignore) {
    }
    return null;
  }

  public @NotNull Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    return resolveDependencies(configuration, Collections.emptySet());
  }

  /**
   * @param configuration           resolvable configuration
   * @param allowedDependencyGroups this filter forces to use artifactView for getting docs and sources,
   *                                which is not working well with ivy repositories (see IDEA-275594).
   *                                So, be careful to use this parameter.
   * @return both resolved and unresolved with the reason dependencies from the given configuration
   */
  public @NotNull Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration,
                                                                     Set<String> allowedDependencyGroups) {
    if (configuration == null) {
      return Collections.emptySet();
    }
    // configurationDependencies can be empty, for example, in the case of a composite build. We should continue resolution anyway.
    ArtifactCollection artifactCollection = resolveConfigurationDependencies(configuration, allowedDependencyGroups);
    Set<ResolvedArtifactResult> configurationDependencies =
      artifactCollection == null ? Collections.emptySet() : artifactCollection.getArtifacts();
    boolean hasFailedToTransformDependencies =
      artifactCollection != null && !artifactCollection.getFailures().isEmpty();

    ResolutionResult resolutionResult = configuration.getIncoming().getResolutionResult();
    Map<ComponentIdentifier, ModuleVersionIdentifier> moduleVersions = new HashMap<>();
    for (ResolvedComponentResult component : resolutionResult.getAllComponents()) {
      ModuleVersionIdentifier moduleVersion = component.getModuleVersion();
      if (moduleVersion != null) {
        moduleVersions.put(component.getId(), moduleVersion);
      }
    }

    // Here we collect java doc and source files for a given dependencies
    AuxiliaryConfigurationArtifacts auxiliaryArtifacts = getAuxiliaryArtifactResolver(configurationDependencies, allowedDependencyGroups)
      .resolve(configuration);
    auxiliaryArtifacts = resolveSupplementaryArtifacts(configuration, auxiliaryArtifacts);
    Set<String> resolvedFiles = new HashSet<>();
    Collection<ExternalDependency> artifactDependencies = resolveArtifactDependencies(
      resolvedFiles, configurationDependencies, auxiliaryArtifacts, moduleVersions,
      configuration, resolutionResult, hasFailedToTransformDependencies
    );
    Collection<FileCollectionDependency> otherFileDependencies = resolveOtherFileDependencies(resolvedFiles, configurationDependencies);
    Collection<ExternalDependency> unresolvedDependencies = collectUnresolvedDependencies(resolutionResult, allowedDependencyGroups);

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
    @NotNull Set<ResolvedArtifactResult> configurationDependencies,
    @NotNull AuxiliaryConfigurationArtifacts auxiliaryArtifacts,
    @NotNull Map<ComponentIdentifier, ModuleVersionIdentifier> moduleVersions,
    @NotNull Configuration configuration,
    @NotNull ResolutionResult resolutionResult,
    boolean hasFailedToTransformDependencies
  ) {
    Collection<ExternalDependency> artifactDependencies = new LinkedHashSet<>();
    Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies = new HashMap<>();
    Set<String> resolvedVariants = new HashSet<>();
    for (ResolvedArtifactResult artifact : configurationDependencies) {
      ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
      boolean isProjectArtifact = componentIdentifier instanceof ProjectComponentIdentifier;
      boolean isModuleArtifact = componentIdentifier instanceof ModuleComponentIdentifier;
      boolean isLibraryBinaryArtifact = componentIdentifier instanceof LibraryBinaryIdentifier;
      if (!isProjectArtifact && !isModuleArtifact && !isLibraryBinaryArtifact) {
        // file collection dependencies are handled by resolveOtherFileDependencies
        continue;
      }
      String configurationName = artifact.getVariant().getDisplayName();
      resolvedVariants.add(getVariantKey(componentIdentifier, configurationName));
      File artifactFile = resolveArtifactFile(resolvedFiles, artifact.getFile());
      if (artifactFile == null) {
        continue;
      }
      ExternalDependency dependency;
      if (isProjectArtifact) {
        dependency = resolveProjectDependency(
          resolvedProjectDependencies, artifact, artifactFile, (ProjectComponentIdentifier)componentIdentifier, moduleVersions
        );
      }
      else {
        dependency = resolveLibraryDependency(artifact, artifactFile, auxiliaryArtifacts, moduleVersions);
      }
      if (dependency != null) {
        artifactDependencies.add(dependency);
      }
    }
    if (hasFailedToTransformDependencies) {
      artifactDependencies.addAll(
        IS_52_OR_BETTER
        ? resolveFailedToTransformProjectDependencies(resolvedProjectDependencies, resolutionResult, resolvedVariants)
        : resolveFailedToTransformProjectDependenciesLegacy(configuration, resolvedProjectDependencies, resolutionResult)
      );
    }
    return artifactDependencies;
  }

  private static @NotNull String getVariantKey(
    @NotNull ComponentIdentifier componentIdentifier,
    @NotNull String configurationName
  ) {
    String componentKey;
    if (componentIdentifier instanceof ProjectComponentIdentifier) {
      ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier)componentIdentifier;
      componentKey = getBuildName(projectComponentIdentifier) + "_" + projectComponentIdentifier.getProjectPath();
    }
    else {
      componentKey = componentIdentifier.getDisplayName();
    }
    return componentKey + "|" + configurationName;
  }

  // Returns null if artifact was already resolved
  private @Nullable File resolveArtifactFile(
    @NotNull Set<String> resolvedFiles, // mutable
    @NotNull File artifactFile
  ) {
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
    @NotNull ProjectComponentIdentifier projectComponentIdentifier,
    @NotNull String configurationName
  ) {
    String buildName = getBuildName(projectComponentIdentifier);
    String projectPath = projectComponentIdentifier.getProjectPath();
    return buildName + "_" + projectPath + "_" + configurationName;
  }

  // Returns null if artifact was already resolved
  private @Nullable DefaultExternalProjectDependency resolveProjectDependency(
    @NotNull Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies, // mutable
    @NotNull ResolvedArtifactResult artifact,
    @NotNull File artifactFile,
    @NotNull ProjectComponentIdentifier projectComponentIdentifier,
    @NotNull Map<ComponentIdentifier, ModuleVersionIdentifier> moduleVersions
  ) {
    String configurationName = artifact.getVariant().getDisplayName();
    String key = getProjectDependencyKey(projectComponentIdentifier, configurationName);
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

    ModuleVersionIdentifier moduleVersion = moduleVersions.get(projectComponentIdentifier);
    projectDependency.setName(projectComponentIdentifier.getProjectName());
    projectDependency.setGroup(moduleVersion == null ? "unspecified" : moduleVersion.getGroup());
    projectDependency.setVersion(moduleVersion == null ? "unspecified" : moduleVersion.getVersion());
    projectDependency.setProjectPath(projectComponentIdentifier.getProjectPath());
    projectDependency.setConfigurationName(configurationName);
    projectDependency.setProjectDependencyArtifacts(Collections.singleton(artifactFile));
    projectDependency.setProjectDependencyArtifactsSources(mySourceSetArtifactIndex.findArtifactSources(artifactFile));

    return projectDependency;
  }

  private static @Nullable DefaultExternalLibraryDependency resolveLibraryDependency(
    @NotNull ResolvedArtifactResult artifact,
    @NotNull File artifactFile,
    @NotNull AuxiliaryConfigurationArtifacts auxiliaryArtifacts,
    @NotNull Map<ComponentIdentifier, ModuleVersionIdentifier> moduleVersions
  ) {
    ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
    String group;
    String name;
    String version;
    if (componentIdentifier instanceof ModuleComponentIdentifier) {
      ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier)componentIdentifier;
      group = moduleComponentIdentifier.getGroup();
      name = moduleComponentIdentifier.getModule();
      version = moduleComponentIdentifier.getVersion();
    }
    else {
      // ResolvedVariantResult.getOwner() is a part of the public API only since Gradle 6.8,
      // but it is always equal to the component identifier of the artifact
      ModuleVersionIdentifier moduleVersionIdentifier = moduleVersions.get(componentIdentifier);
      if (moduleVersionIdentifier == null) {
        return null;
      }
      group = moduleVersionIdentifier.getGroup();
      name = moduleVersionIdentifier.getName();
      version = moduleVersionIdentifier.getVersion();
    }

    DefaultExternalLibraryDependency libraryDependency = new DefaultExternalLibraryDependency();
    libraryDependency.setName(name);
    libraryDependency.setGroup(group);
    libraryDependency.setVersion(version);
    libraryDependency.setFile(artifactFile);

    File sourcesFile = auxiliaryArtifacts.getSources(componentIdentifier, artifactFile);
    if (sourcesFile != null) {
      libraryDependency.setSource(sourcesFile);
    }
    File javadocFile = auxiliaryArtifacts.getJavadoc(componentIdentifier, artifactFile);
    if (javadocFile != null) {
      libraryDependency.setJavadoc(javadocFile);
    }
    String artifactType = artifact.getVariant().getAttributes().getAttribute(ARTIFACT_TYPE_ATTRIBUTE);
    if (artifactType != null) {
      libraryDependency.setPackaging(artifactType);
    }
    else {
      // Old Gradle versions do not expose the artifact type attribute for every artifact
      String fileName = artifact.getFile().getName();
      int dotIndex = fileName.lastIndexOf('.');
      if (dotIndex >= 0) {
        libraryDependency.setPackaging(fileName.substring(dotIndex + 1));
      }
    }
    libraryDependency.setClassifier(getArtifactClassifier(artifact.getFile().getName(), version));

    return libraryDependency;
  }

  // ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE is a part of the public API only since Gradle 7.3,
  // but the attribute itself exists since the introduction of variant-aware dependency management
  private static final Attribute<String> ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("artifactType", String.class);

  // Gradle derives the classifier of a module artifact from its file name the same way in ArtifactFile,
  // e.g. "test-fixtures" from "foo-1.0-test-fixtures.jar". Returns null when the file name does not match
  // the "<name>-<version>-<classifier>" layout, as with timestamped snapshot files.
  private static @Nullable String getArtifactClassifier(@NotNull String fileName, @NotNull String version) {
    int startVersion = fileName.lastIndexOf("-" + version);
    if (startVersion < 0) return null;
    int tailStart = startVersion + version.length() + 1;
    if (tailStart >= fileName.length() || fileName.charAt(tailStart) != '-') return null;
    String tail = fileName.substring(tailStart + 1);
    int dotIndex = tail.lastIndexOf('.');
    String classifier = dotIndex < 0 ? tail : tail.substring(0, dotIndex);
    return classifier.isEmpty() ? null : classifier;
  }

  // Returns dependencies which artifacts have failed to transform, with the artifacts taken from the target project
  private @NotNull Collection<ExternalProjectDependency> resolveFailedToTransformProjectDependencies(
    @NotNull Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies, // mutable
    @NotNull ResolutionResult resolutionResult,
    @NotNull Set<String> resolvedVariants
  ) {
    Collection<ExternalProjectDependency> failedToTransformDependencies = new ArrayList<>();
    for (DependencyResult dependencyResult : resolutionResult.getAllDependencies()) {
      if (!(dependencyResult instanceof ResolvedDependencyResult)) continue;
      ResolvedDependencyResult resolvedDependencyResult = (ResolvedDependencyResult)dependencyResult;
      ComponentSelector requested = resolvedDependencyResult.getRequested();
      if (!(requested instanceof ProjectComponentSelector)) continue;
      ResolvedComponentResult selected = resolvedDependencyResult.getSelected();
      ModuleVersionIdentifier selectedVersion = selected.getModuleVersion();
      for (ResolvedVariantResult variant : selected.getVariants()) {
        String configurationName = variant.getDisplayName();
        // skip variants which contributed at least one artifact, and variants already processed for another dependency
        if (!resolvedVariants.add(getVariantKey(selected.getId(), configurationName))) continue;

        String projectPath = ((ProjectComponentSelector)requested).getProjectPath();
        String key = projectPath + "_" + configurationName;
        if (resolvedProjectDependencies.containsKey(key)) continue;

        DefaultExternalProjectDependency projectDependency = new DefaultExternalProjectDependency();
        resolvedProjectDependencies.put(key, projectDependency);
        String projectName = Path.path(projectPath).getName();
        projectDependency.setName(projectName);
        projectDependency.setGroup(selectedVersion == null ? "unspecified" : selectedVersion.getGroup());
        projectDependency.setVersion(selectedVersion == null ? "unspecified" : selectedVersion.getVersion());
        projectDependency.setProjectPath(projectPath);
        projectDependency.setConfigurationName(configurationName);

        Project project = myProject.findProject(projectPath);
        if (project == null) continue;
        Configuration configuration = project.getConfigurations().findByName(configurationName);
        if (configuration == null) continue;
        Set<File> projectArtifacts = configuration.getArtifacts().getFiles().getFiles();
        projectDependency.setProjectDependencyArtifacts(projectArtifacts);
        projectDependency.setProjectDependencyArtifactsSources(mySourceSetArtifactIndex.findArtifactSources(projectArtifacts));
        failedToTransformDependencies.add(projectDependency);
      }
    }
    return failedToTransformDependencies;
  }

  // ResolvedComponentResult.getVariants() is a part of the public API only since Gradle 5.2.
  // On older Gradle the resolved configuration name is available only in the legacy ResolvedDependency graph.
  // The graph is built only on this path, that is, when an artifact transform has failed on an old Gradle.
  private @NotNull Collection<ExternalProjectDependency> resolveFailedToTransformProjectDependenciesLegacy(
    @NotNull Configuration configuration,
    @NotNull Map<String, DefaultExternalProjectDependency> resolvedProjectDependencies, // mutable
    @NotNull ResolutionResult resolutionResult
  ) {
    Map<ModuleVersionIdentifier, ResolvedDependencyResult> projectDependencyResults = new HashMap<>();
    for (DependencyResult dependencyResult : resolutionResult.getAllDependencies()) {
      ComponentSelector requested = dependencyResult.getRequested();
      if (dependencyResult instanceof ResolvedDependencyResult && requested instanceof ProjectComponentSelector) {
        ResolvedComponentResult selected = ((ResolvedDependencyResult)dependencyResult).getSelected();
        projectDependencyResults.put(selected.getModuleVersion(), (ResolvedDependencyResult)dependencyResult);
      }
    }
    Collection<ExternalProjectDependency> failedToTransformDependencies = new ArrayList<>();
    LenientConfiguration lenientConfiguration = configuration.getResolvedConfiguration().getLenientConfiguration();
    for (ResolvedDependency dependency : lenientConfiguration.getAllModuleDependencies()) {
      Set<ResolvedArtifact> artifacts;
      try {
        artifacts = dependency.getModuleArtifacts();
      }
      catch (GradleException e) {
        artifacts = Collections.emptySet();
      }
      catch (Exception ignore) {
        // ignore other artifact resolution exceptions
        continue;
      }
      if (!artifacts.isEmpty()) continue;
      ResolvedDependencyResult dependencyResult = projectDependencyResults.get(dependency.getModule().getId());
      if (dependencyResult == null) continue;

      String projectPath = ((ProjectComponentSelector)dependencyResult.getRequested()).getProjectPath();
      String key = projectPath + "_" + dependency.getConfiguration();
      if (resolvedProjectDependencies.containsKey(key)) continue;

      DefaultExternalProjectDependency projectDependency = new DefaultExternalProjectDependency();
      resolvedProjectDependencies.put(key, projectDependency);
      projectDependency.setName(Path.path(projectPath).getName());
      projectDependency.setGroup(dependency.getModuleGroup());
      projectDependency.setVersion(dependency.getModuleVersion());
      projectDependency.setProjectPath(projectPath);
      projectDependency.setConfigurationName(dependency.getConfiguration());

      Project project = myProject.findProject(projectPath);
      if (project == null) continue;
      Configuration targetConfiguration = project.getConfigurations().findByName(dependency.getConfiguration());
      if (targetConfiguration == null) continue;
      Set<File> projectArtifacts = targetConfiguration.getArtifacts().getFiles().getFiles();
      projectDependency.setProjectDependencyArtifacts(projectArtifacts);
      projectDependency.setProjectDependencyArtifactsSources(mySourceSetArtifactIndex.findArtifactSources(projectArtifacts));
      failedToTransformDependencies.add(projectDependency);
    }
    return failedToTransformDependencies;
  }

  private @NotNull AuxiliaryArtifactResolver getAuxiliaryArtifactResolver(
    @NotNull Set<ResolvedArtifactResult> configurationDependencies,
    @NotNull Set<String> allowedDependencyGroups
  ) {
    String useLegacyResolverPropertyValue = System.getProperty("idea.gradle.daemon.legacy.dependency.resolver", "false");
    boolean useLegacyResolver = Boolean.parseBoolean(useLegacyResolverPropertyValue);
    if (useLegacyResolver || GradleVersionUtil.isCurrentGradleOlderThan("7.5") || isIvyRepositoryUsed(myProject)) {
      return new LegacyAuxiliaryArtifactResolver(myProject, myDownloadPolicy, getModuleComponents(configurationDependencies));
    }
    return new AuxiliaryArtifactResolverImpl(myProject, myDownloadPolicy, allowedDependencyGroups);
  }

  private static @NotNull Collection<ComponentIdentifier> getModuleComponents(
    @NotNull Set<ResolvedArtifactResult> configurationDependencies
  ) {
    Set<ComponentIdentifier> moduleComponents = new LinkedHashSet<>();
    for (ResolvedArtifactResult artifact : configurationDependencies) {
      ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
      if (componentIdentifier instanceof ModuleComponentIdentifier) {
        moduleComponents.add(componentIdentifier);
      }
    }
    return moduleComponents;
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
    @NotNull ResolutionResult resolutionResult,
    Set<String> allowedDependencyGroups
  ) {
    Collection<ExternalDependency> result = new LinkedHashSet<>();
    for (DependencyResult dependencyResult : resolutionResult.getAllDependencies()) {
      if (!(dependencyResult instanceof UnresolvedDependencyResult)) continue;
      ComponentSelector attemptedSelector = ((UnresolvedDependencyResult)dependencyResult).getAttempted();
      if (!(attemptedSelector instanceof ModuleComponentSelector)) continue;
      ModuleComponentSelector selector = (ModuleComponentSelector)attemptedSelector;
      if (!allowedDependencyGroups.isEmpty() && !allowedDependencyGroups.contains(selector.getGroup())) {
        continue;
      }
      Throwable problem = ((UnresolvedDependencyResult)dependencyResult).getFailure();
      if (problem.getCause() != null) {
        problem = problem.getCause();
      }
      DefaultUnresolvedExternalDependency dependency = new DefaultUnresolvedExternalDependency();
      dependency.setName(selector.getModule());
      dependency.setGroup(selector.getGroup());
      dependency.setVersion(selector.getVersion());
      dependency.setFailureMessage(problem.getMessage());
      result.add(dependency);
    }
    return result;
  }

  private static @NotNull String getBuildName(@NotNull ProjectComponentIdentifier projectComponentIdentifier) {
    BuildIdentifier buildIdentifier = projectComponentIdentifier.getBuild();
    if (IS_83_OR_BETTER) {
      return buildIdentifier.getBuildPath();
    }
    else {
      // The getName method was removed in Gradle 9.0
      return GradleReflectionUtil.getValue(buildIdentifier, "getName", String.class);
    }
  }

  private @NotNull AuxiliaryConfigurationArtifacts resolveSupplementaryArtifacts(
    @NotNull Configuration configuration,
    @NotNull AuxiliaryConfigurationArtifacts primaryArtifacts
  ) {
    if (!myDownloadPolicy.isDownloadSources() && !myDownloadPolicy.isDownloadJavadoc()) {
      return primaryArtifacts;
    }
    ServiceLoader<AuxiliaryArtifactProvider> providers = ServiceLoader.load(
      AuxiliaryArtifactProvider.class, AuxiliaryArtifactProvider.class.getClassLoader());
    for (AuxiliaryArtifactProvider provider : providers) {
      try {
        AuxiliaryConfigurationArtifacts additional = provider.resolve(myProject, configuration, myDownloadPolicy);
        primaryArtifacts = primaryArtifacts.mergeWith(additional);
      }
      catch (Exception ignore) {
        // supplementary providers should not break the main resolution
      }
    }
    return primaryArtifacts;
  }

  private static boolean isIvyRepositoryUsed(@NotNull Project project) {
    for (ArtifactRepository repository : project.getRepositories()) {
      if (repository instanceof IvyArtifactRepository) {
        return true;
      }
    }
    return false;
  }
}
