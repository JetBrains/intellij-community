// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.DefaultModuleComponentIdentifier;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class LegacyAuxiliaryArtifactResolver implements AuxiliaryArtifactResolver {

  private final @NotNull Project project;
  private final @NotNull GradleDependencyDownloadPolicy policy;
  private final @NotNull Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts;

  public LegacyAuxiliaryArtifactResolver(@NotNull Project project,
                                         @NotNull GradleDependencyDownloadPolicy policy,
                                         @NotNull Map<ResolvedDependency, Set<ResolvedArtifact>> resolvedArtifacts) {
    this.project = project;
    this.policy = policy;
    this.resolvedArtifacts = resolvedArtifacts;
  }

  @Override
  public @NotNull AuxiliaryConfigurationArtifacts resolve(@NotNull Configuration configuration) {
    List<Class<? extends Artifact>> artifactTypes = getRequiredArtifactTypes();
    if (artifactTypes.isEmpty()) {
      return new AuxiliaryConfigurationArtifacts(Collections.emptyMap(), Collections.emptyMap());
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
      return new AuxiliaryConfigurationArtifacts(Collections.emptyMap(), Collections.emptyMap());
    }
    Set<ComponentArtifactsResult> componentResults = getDependencyHandler(configuration)
      .createArtifactResolutionQuery()
      .forComponents(components)
      .withArtifacts(JvmLibrary.class, artifactTypes)
      .execute()
      .getResolvedComponents();
    return classify(componentResults);
  }

  private @NotNull List<Class<? extends Artifact>> getRequiredArtifactTypes() {
    List<Class<? extends Artifact>> artifactTypes = new ArrayList<>(2);
    if (policy.isDownloadSources()) {
      artifactTypes.add(SourcesArtifact.class);
    }
    if (policy.isDownloadJavadoc()) {
      artifactTypes.add(JavadocArtifact.class);
    }
    return artifactTypes;
  }

  private @NotNull DependencyHandler getDependencyHandler(@NotNull Configuration configuration) {
    ScriptHandler buildscript = project.getBuildscript();
    boolean isBuildScriptConfiguration = buildscript.getConfigurations().contains(configuration);
    return isBuildScriptConfiguration ? buildscript.getDependencies() : project.getDependencies();
  }

  private static @NotNull AuxiliaryConfigurationArtifacts classify(
    @NotNull Set<ComponentArtifactsResult> components
  ) {
    Map<ComponentIdentifier, Set<File>> sources = new HashMap<>();
    Map<ComponentIdentifier, Set<File>> javadocs = new HashMap<>();
    for (ComponentArtifactsResult component : components) {
      ComponentIdentifier componentId = component.getId();
      putIfNotNull(sources, componentId, getResolvedAuxiliaryArtifactFiles(component, SourcesArtifact.class));
      putIfNotNull(javadocs, componentId, getResolvedAuxiliaryArtifactFiles(component, JavadocArtifact.class));
    }
    return new AuxiliaryConfigurationArtifacts(sources, javadocs);
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

  private static <K, V> void putIfNotNull(@NotNull Map<K, V> target, @NotNull K key, @Nullable V value) {
    if (value != null) {
      target.put(key, value);
    }
  }
}
