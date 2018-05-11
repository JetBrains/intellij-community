// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.component.Artifact;
import org.gradle.api.specs.Specs;
import org.gradle.internal.impldep.com.google.common.collect.Iterables;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl;
import org.jetbrains.plugins.gradle.tooling.util.resolve.ExternalDepsResolutionResult;

import java.util.*;

public class ArtifactsCollectionResolver extends DependencyResolverImpl implements DependencyResolver {

  private static final Collection<ExternalDependency> EMPTY = Collections.emptySet();

  public ArtifactsCollectionResolver(@NotNull Project project, boolean isPreview) {
    super(project, isPreview);
  }

  public ArtifactsCollectionResolver(@NotNull Project project, boolean isPreview, boolean downloadJavadoc, boolean downloadSources, SourceSetCachedFinder sourceSetCachedFinder) {
    super(project, isPreview, downloadJavadoc, downloadSources, sourceSetCachedFinder);
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName) {
    return resolveDependencies(configurationName, null);
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName, @Nullable String scope) {
    if (configurationName == null) {
      return EMPTY;
    }
    return resolveDependencies(myProject.getConfigurations().findByName(configurationName), scope).getExternalDeps();
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    return resolveDependencies(configuration, null).getExternalDeps();
  }

  @Override
  public ExternalDepsResolutionResult resolveDependencies(@Nullable Configuration configuration,
                                                          @Nullable String scope) {
    if (configuration == null) {
      return ExternalDepsResolutionResult.EMPTY;
    }
    Collection<ExternalDependency> externalDeps = new LinkedHashSet<ExternalDependency>();

    ArtifactCollection resolvedArtifacts = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
      @Override
      public void execute(@NotNull ArtifactView.ViewConfiguration configuration) {
        configuration.setLenient(true);
        configuration.componentFilter(Specs.SATISFIES_ALL);
      }
    }).getArtifacts();

    Map<ModuleComponentIdentifier, Map<Class<? extends Artifact>, Set<ResolvedArtifactResult>>> sourcesAndJavadocs
    = getSourcesAndJavadocs(resolvedArtifacts, myDownloadJavadoc, myDownloadSources);

    Iterable<UnresolvedDependencyResult> unresolvedDependencies =
      Iterables.filter(configuration.getIncoming().getResolutionResult().getRoot().getDependencies(), UnresolvedDependencyResult.class);

    ScopedExternalDependenciesFactory factory = new ScopedExternalDependenciesFactory(myProject, scope);

    externalDeps.addAll(factory.processResolvedArtifacts(resolvedArtifacts, sourcesAndJavadocs));
    externalDeps.addAll(factory.processUnresolvedDeps(unresolvedDependencies));

    return new ExternalDepsResolutionResult(externalDeps, resolvedArtifacts.getArtifactFiles().getFiles());
  }

  @NotNull
  private Map<ModuleComponentIdentifier, Map<Class<? extends Artifact>, Set<ResolvedArtifactResult>>> getSourcesAndJavadocs(
    ArtifactCollection artifacts,
    boolean downloadJavadoc,
    boolean downloadSources) {

    List<Class<? extends Artifact>> types = getTypes(downloadJavadoc, downloadSources);

    if (types.isEmpty()) {
      return Collections.emptyMap();
    }
    Class[] typesArray = types.toArray(new Class[types.size()]);

    Collection<? extends ComponentIdentifier> moduleIds = collectModuleComponentIds(artifacts);

    if (moduleIds.isEmpty()) {
      return Collections.emptyMap();
    }

    ArtifactResolutionResult queryResult = myProject.getDependencies().createArtifactResolutionQuery()
      .forComponents(moduleIds)
      .withArtifacts(JvmLibrary.class, typesArray)
      .execute();

    Map<ModuleComponentIdentifier, Map<Class<? extends Artifact>, Set<ResolvedArtifactResult>>> result =
      new LinkedHashMap<ModuleComponentIdentifier, Map<Class<? extends Artifact>, Set<ResolvedArtifactResult>>>();

    for (ComponentArtifactsResult artifactsResult : queryResult.getResolvedComponents()) {
      for (Class<? extends Artifact> type : types) {
        Set<ResolvedArtifactResult> resolvedArtifactResults = getResolvedArtifactsOfType(artifactsResult, type);

        if (!resolvedArtifactResults.isEmpty()) {
          ModuleComponentIdentifier componentId = (ModuleComponentIdentifier)artifactsResult.getId();
          Map<Class<? extends Artifact>, Set<ResolvedArtifactResult>> typeToArtifacts = result.get(componentId);
          if (typeToArtifacts == null) {
            typeToArtifacts = new LinkedHashMap<Class<? extends Artifact>, Set<ResolvedArtifactResult>>();
            result.put(componentId, typeToArtifacts);
          }

          typeToArtifacts.put(type, resolvedArtifactResults);
        }
      }
    }

    return result;
  }

  @NotNull
  private Set<ResolvedArtifactResult> getResolvedArtifactsOfType(@NotNull ComponentArtifactsResult artifactsResult,
                                                                 @NotNull Class<? extends Artifact> type) {
    Set<ResolvedArtifactResult> resolvedArtifactResults = new LinkedHashSet<ResolvedArtifactResult>();

    for (ArtifactResult artifactResult : artifactsResult.getArtifacts(type)) {
      if (artifactResult instanceof ResolvedArtifactResult) {
        resolvedArtifactResults.add((ResolvedArtifactResult) artifactResult);
      }
    }
    return resolvedArtifactResults;
  }

  @NotNull
  private Collection<ModuleComponentIdentifier> collectModuleComponentIds(@NotNull ArtifactCollection artifacts) {
    Collection<ModuleComponentIdentifier> moduleIds = new LinkedHashSet<ModuleComponentIdentifier>();
    for (ResolvedArtifactResult result : artifacts.getArtifacts()) {
      ComponentIdentifier componentIdentifier = result.getId().getComponentIdentifier();
      if (componentIdentifier instanceof ModuleComponentIdentifier) {
        moduleIds.add((ModuleComponentIdentifier)componentIdentifier);
      }
    }
    return moduleIds;
  }

  @NotNull
  private List<Class<? extends Artifact>> getTypes(boolean downloadJavadoc, boolean downloadSources) {
    List<Class<? extends Artifact>> types = new ArrayList<Class<? extends Artifact>>();

    if (downloadJavadoc) {
      types.add(JavadocArtifact.class);
    }

    if (downloadSources) {
      types.add(SourcesArtifact.class);
    }
    return types;
  }
}
