// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * Can be used only since Gradle 7.5 because the `withVariantReselection` method is mandatory.
 * Does not support ivy repositories and multi-artifact mapping when a few different Sources/Javadoc are available for a single library.
 * Relies on ArtifactView, as a result, if a library does not declare Sources/Javadoc in Gradle Module Metadata, the artifact will not be
 * picked up by ArtifactView.
 */
@ApiStatus.Internal
public class AuxiliaryArtifactResolverImpl implements AuxiliaryArtifactResolver {

  private final @NotNull Project project;
  private final @NotNull GradleDependencyDownloadPolicy policy;
  private final @NotNull Set<String> allowedDependencyGroups;

  public AuxiliaryArtifactResolverImpl(@NotNull Project project,
                                       @NotNull GradleDependencyDownloadPolicy policy,
                                       @NotNull Set<String> allowedDependencyGroups
  ) {
    this.project = project;
    this.policy = policy;
    this.allowedDependencyGroups = allowedDependencyGroups;
  }

  @Override
  public @NotNull AuxiliaryConfigurationArtifacts resolve(@NotNull Configuration configuration) {
    boolean downloadSources = policy.isDownloadSources();
    boolean downloadJavadoc = policy.isDownloadJavadoc();
    if (!(downloadSources || downloadJavadoc)) {
      return new AuxiliaryConfigurationArtifacts(Collections.emptyMap(), Collections.emptyMap());
    }
    Map<ComponentIdentifier, Set<File>> javadocs = Collections.emptyMap();
    if (downloadJavadoc) {
      Set<ResolvedArtifactResult> artifacts = resolve(configuration, DocsType.JAVADOC);
      javadocs = classify(artifacts);
    }
    Map<ComponentIdentifier, Set<File>> sources = Collections.emptyMap();
    if (downloadSources) {
      Set<ResolvedArtifactResult> artifacts = resolve(configuration, DocsType.SOURCES);
      sources = classify(artifacts);
    }
    return new AuxiliaryConfigurationArtifacts(sources, javadocs);
  }

  private static @NotNull Map<ComponentIdentifier, Set<File>> classify(@NotNull Set<ResolvedArtifactResult> artifacts) {
    Map<ComponentIdentifier, Set<File>> result = new HashMap<>();
    for (ResolvedArtifactResult artifact : artifacts) {
      ComponentArtifactIdentifier identifier = artifact.getId();
      if (identifier instanceof ModuleComponentArtifactIdentifier) {
        File file = artifact.getFile();
        result.computeIfAbsent(identifier.getComponentIdentifier(), __ -> new HashSet<>())
          .add(file);
      }
    }
    return result;
  }

  private @NotNull Set<ResolvedArtifactResult> resolve(
    @NotNull Configuration configuration,
    @MagicConstant(stringValues = {DocsType.JAVADOC, DocsType.SOURCES}) @NotNull String docsType
  ) {
    ObjectFactory objects = project.getObjects();
    return configuration
      .getIncoming()
      .artifactView(view -> {
        view.componentFilter(componentIdentifier ->  {
          if (allowedDependencyGroups.isEmpty()) return true;
          if (componentIdentifier instanceof ModuleComponentIdentifier) {
            return allowedDependencyGroups.contains(((ModuleComponentIdentifier)componentIdentifier).getGroup());
          }
          return false;
        });
        // we are interested only in variant reselection
        view.withVariantReselection();
        // do not propagate resolution exceptions; try to resolve as many artifacts as possible
        view.setLenient(true);
        view.attributes(container -> {
          // all sources/Javadoc are considered as Runtime libraries from the perspective of API/Implementation use
          container.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
          container.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
          container.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
          container.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, docsType));
        });
      })
      .getArtifacts()
      .getArtifacts();
  }
}
