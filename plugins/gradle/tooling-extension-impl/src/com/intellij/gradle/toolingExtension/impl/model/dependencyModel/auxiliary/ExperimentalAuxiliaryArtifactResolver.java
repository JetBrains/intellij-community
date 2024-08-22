// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.Artifact;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Can be used only since Gradle 7.3.
 * Does not support ivy repositories and multi-artifact mapping when a few different Sources/Javadoc are available for a single library.
 * Relies on ArtifactView, as a result, if a library does not declare Sources/Javadoc in Gradle Module Metadata, the artifact will not be
 * picked up by ArtifactView.
 */
@ApiStatus.Internal
public class ExperimentalAuxiliaryArtifactResolver extends AuxiliaryArtifactResolver {

  public ExperimentalAuxiliaryArtifactResolver(@NotNull Project project,
                                               @NotNull GradleDependencyDownloadPolicy policy) {
    super(project, policy);
  }

  @Override
  public @NotNull AuxiliaryConfigurationArtifacts resolve(@NotNull Configuration configuration) {
    boolean downloadSources = policy.isDownloadSources();
    boolean downloadJavadoc = policy.isDownloadJavadoc();
    if (!(downloadSources || downloadJavadoc)) {
      return new AuxiliaryConfigurationArtifacts(Collections.emptyMap());
    }
    Set<ResolvedArtifactResult> javadocs = Collections.emptySet();
    if (downloadJavadoc) {
      javadocs = resolve(configuration, JavadocArtifact.class);
    }
    Set<ResolvedArtifactResult> sources = Collections.emptySet();
    if (downloadSources) {
      sources = resolve(configuration, SourcesArtifact.class);
    }
    Map<ComponentIdentifier, Map<Class<? extends Artifact>, Set<File>>> classifiedArtifacts = classify(sources, javadocs);
    return new AuxiliaryConfigurationArtifacts(classifiedArtifacts);
  }

  private static @NotNull Map<ComponentIdentifier, Map<Class<? extends Artifact>, Set<File>>> classify(
    @NotNull Set<ResolvedArtifactResult> sources,
    @NotNull Set<ResolvedArtifactResult> javadocs
  ) {
    Map<ComponentIdentifier, Map<Class<? extends Artifact>, Set<File>>> result = new HashMap<>();
    for (ResolvedArtifactResult source : sources) {
      ComponentArtifactIdentifier identifier = source.getId();
      if (identifier instanceof ModuleComponentArtifactIdentifier) {
        File file = source.getFile();
        result.computeIfAbsent(identifier.getComponentIdentifier(), ignore -> new HashMap<>(2))
          .put(SourcesArtifact.class, Collections.singleton(file));
      }
    }
    for (ResolvedArtifactResult javadoc : javadocs) {
      ComponentArtifactIdentifier identifier = javadoc.getId();
      if (identifier instanceof ModuleComponentArtifactIdentifier) {
        File file = javadoc.getFile();
        result.computeIfAbsent(identifier.getComponentIdentifier(), ignore -> new HashMap<>(2))
          .put(JavadocArtifact.class, Collections.singleton(file));
      }
    }
    return result;
  }

  private @NotNull Set<ResolvedArtifactResult> resolve(@NotNull Configuration configuration,
                                                       @NotNull Class<? extends Artifact> artifactType) {
    ObjectFactory objects = project.getObjects();
    return configuration
      .getIncoming()
      .artifactView(view -> {
        // we are interested only in variant reselection
        view.withVariantReselection();
        // do not propagate resolution exceptions; try to resolve as many artifacts as possible
        view.setLenient(true);
        view.attributes(container -> {
          // all sources/Javadoc are considered as Runtime libraries from the perspective of API/Implementation use
          container.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
          container.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
          container.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
          container.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, getDocsType(artifactType)));
        });
      })
      .getArtifacts()
      .getArtifacts();
  }

  private static @NotNull String getDocsType(@NotNull Class<? extends Artifact> artifactType) {
    if (artifactType == JavadocArtifact.class) {
      return DocsType.JAVADOC;
    }
    else {
      // always fallback to sources
      return DocsType.SOURCES;
    }
  }
}
