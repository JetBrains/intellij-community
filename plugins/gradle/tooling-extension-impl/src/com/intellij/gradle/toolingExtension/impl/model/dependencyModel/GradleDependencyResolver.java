// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicyCache;
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
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.*;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.specs.Spec;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.util.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.FileCollectionDependency;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class GradleDependencyResolver {

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

  private static @NotNull Set<ResolvedArtifactResult> resolveConfigurationDependencies(@NotNull Configuration configuration,
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
      return artifactView.getArtifacts().getArtifacts();
    }
    catch (Exception ignore) {
    }
    return Collections.emptySet();
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
    Set<ResolvedArtifactResult> configurationDependencies = resolveConfigurationDependencies(configuration, allowedDependencyGroups);

    LenientConfiguration lenientConfiguration = configuration.getResolvedConfiguration().getLenientConfiguration();
    Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts = new LinkedHashMap<>();
    boolean hasFailedToTransformDependencies = false;
    for (ResolvedDependency dependency : lenientConfiguration.getAllModuleDependencies()) {
      try {
        if (allowedDependencyGroups.isEmpty() || allowedDependencyGroups.contains(dependency.getModuleGroup())) {
          resolvedArtifacts.put(dependency, dependency.getModuleArtifacts());
        }
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
        //Note: we don't use here alloweded dependency groups filter, because it has sense only for ModuleComponentSelector
        if (dependencyResult instanceof ResolvedDependencyResult && resultRequested instanceof ProjectComponentSelector) {
          ResolvedComponentResult resolvedComponentResult = ((ResolvedDependencyResult)dependencyResult).getSelected();
          ModuleVersionIdentifier selectedResultVersion = resolvedComponentResult.getModuleVersion();
          transformedProjectDependenciesResultMap.put(selectedResultVersion, (ResolvedDependencyResult)dependencyResult);
        }
      }
    }
    // Here we collect java doc and source files for a given dependencies
    AuxiliaryConfigurationArtifacts auxiliaryArtifacts = getAuxiliaryArtifactResolver(resolvedArtifacts, allowedDependencyGroups)
      .resolve(configuration);
    Set<String> resolvedFiles = new HashSet<>();
    Collection<ExternalDependency> artifactDependencies = resolveArtifactDependencies(
      resolvedFiles, resolvedArtifacts, auxiliaryArtifacts, transformedProjectDependenciesResultMap
    );
    Collection<FileCollectionDependency> otherFileDependencies = resolveOtherFileDependencies(resolvedFiles, configurationDependencies);
    Collection<ExternalDependency> unresolvedDependencies = collectUnresolvedDependencies(lenientConfiguration, allowedDependencyGroups);

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
    @NotNull AuxiliaryConfigurationArtifacts auxiliaryArtifacts,
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
          ExternalDependency dependency = resolveLibraryDependency(artifact, artifactFile, auxiliaryArtifacts);
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
    @NotNull AuxiliaryConfigurationArtifacts auxiliaryArtifacts
  ) {
    DefaultExternalLibraryDependency libraryDependency = new DefaultExternalLibraryDependency();

    ModuleVersionIdentifier moduleVersionIdentifier = artifact.getModuleVersion().getId();
    libraryDependency.setName(moduleVersionIdentifier.getName());
    libraryDependency.setGroup(moduleVersionIdentifier.getGroup());
    libraryDependency.setVersion(moduleVersionIdentifier.getVersion());
    libraryDependency.setFile(artifactFile);

    ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
    File sourcesFile = auxiliaryArtifacts.getSources(componentIdentifier, artifactFile);
    if (sourcesFile != null) {
      libraryDependency.setSource(sourcesFile);
    }
    File javadocFile = auxiliaryArtifacts.getJavadoc(componentIdentifier, artifactFile);
    if (javadocFile != null) {
      libraryDependency.setJavadoc(javadocFile);
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

  private @NotNull AuxiliaryArtifactResolver getAuxiliaryArtifactResolver(
    @NotNull Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts,
    @NotNull Set<String> allowedDependencyGroups
  ) {
    String useLegacyResolverPropertyValue = System.getProperty("idea.gradle.daemon.legacy.dependency.resolver", "false");
    boolean useLegacyResolver = Boolean.parseBoolean(useLegacyResolverPropertyValue);
    if (useLegacyResolver || GradleVersionUtil.isCurrentGradleOlderThan("7.5")) {
      return new LegacyAuxiliaryArtifactResolver(myProject, myDownloadPolicy, resolvedArtifacts);
    }
    if (isIvyRepositoryUsed(myProject)) {
      return new LegacyAuxiliaryArtifactResolver(myProject, myDownloadPolicy, resolvedArtifacts);
    }
    return new AuxiliaryArtifactResolverImpl(myProject, myDownloadPolicy, allowedDependencyGroups);
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
    @NotNull LenientConfiguration lenientConfiguration,
    Set<String> allowedDependencyGroups
  ) {
    Collection<ExternalDependency> result = new LinkedHashSet<>();
    Set<UnresolvedDependency> unresolvedModuleDependencies = lenientConfiguration.getUnresolvedModuleDependencies();
    for (UnresolvedDependency unresolvedDependency : unresolvedModuleDependencies) {
      if (!allowedDependencyGroups.isEmpty() && !allowedDependencyGroups.contains(unresolvedDependency.getSelector().getGroup())) {
        continue;
      }
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

  private static @NotNull String getBuildName(@NotNull ProjectComponentIdentifier projectComponentIdentifier) {
    BuildIdentifier buildIdentifier = projectComponentIdentifier.getBuild();
    if (IS_83_OR_BETTER) {
      return buildIdentifier.getBuildPath();
    } else {
      // The getName method was removed in Gradle 9.0
      return GradleReflectionUtil.getValue(buildIdentifier, "getName", String.class);
    }
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

  private static boolean isIvyRepositoryUsed(@NotNull Project project) {
    for (ArtifactRepository repository : project.getRepositories()) {
      if (repository instanceof IvyArtifactRepository) {
        return true;
      }
    }
    return false;
  }
}
