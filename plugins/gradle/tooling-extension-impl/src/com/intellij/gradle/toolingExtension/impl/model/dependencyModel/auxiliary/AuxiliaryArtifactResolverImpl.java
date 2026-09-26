// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Can be used only since Gradle 7.5 because the `withVariantReselection` method is mandatory.
 * Does not support ivy repositories and multi-artifact mapping when a few different Sources/Javadoc are available for a single library.
 * Relies on ArtifactView, as a result, if a library does not declare Sources/Javadoc in Gradle Module Metadata, the artifact will not be
 * picked up by ArtifactView.
 */
@ApiStatus.Internal
public class AuxiliaryArtifactResolverImpl implements AuxiliaryArtifactResolver {

  /**
   * Archive artifact types that IntelliJ IDEA can mount as a library source or Javadoc root.
   * <p>
   * The request must name a concrete artifact type. Without it the view can return an intermediate format that IntelliJ cannot read
   * (see IDEA-254862). A named type also lets Gradle run an artifact transform to that type.
   * <p>
   * IntelliJ IDEA registers these archive extensions for the {@code ARCHIVE} file type: {@code ane, apk, ear, egg, jar, swc, war, zip}
   * (see {@code intellij.platform.ide.impl.xml}). Only {@code jar} and {@code zip} ever hold source or Javadoc content, so the resolver
   * requests these two types. The tooling extension runs in the Gradle daemon and cannot read the platform file type registration, so the
   * list is fixed here.
   * <p>
   * The resolver runs one view for each type and merges the results.
   */
  private static final List<String> SUPPORTED_ARCHIVE_ARTIFACT_TYPES = Arrays.asList(
    ArtifactTypeDefinition.JAR_TYPE,
    ArtifactTypeDefinition.ZIP_TYPE
  );

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
      javadocs = resolveAuxiliaryArtifacts(configuration, DocsType.JAVADOC);
    }
    Map<ComponentIdentifier, Set<File>> sources = Collections.emptyMap();
    if (downloadSources) {
      sources = resolveAuxiliaryArtifacts(configuration, DocsType.SOURCES);
    }
    return new AuxiliaryConfigurationArtifacts(sources, javadocs);
  }

  private @NotNull Map<ComponentIdentifier, Set<File>> resolveAuxiliaryArtifacts(
    @NotNull Configuration configuration,
    @MagicConstant(stringValues = {DocsType.JAVADOC, DocsType.SOURCES}) @NotNull String docsType
  ) {
    Map<ComponentIdentifier, Set<File>> result = new HashMap<>();
    for (String artifactType : SUPPORTED_ARCHIVE_ARTIFACT_TYPES) {
      Set<ResolvedArtifactResult> artifacts = resolve(configuration, docsType, artifactType);
      classifyInto(result, artifacts);
    }
    return result;
  }

  private static void classifyInto(
    @NotNull Map<ComponentIdentifier, Set<File>> target,
    @NotNull Set<ResolvedArtifactResult> artifacts
  ) {
    for (Map.Entry<ComponentIdentifier, Set<File>> entry : classify(artifacts).entrySet()) {
      target.computeIfAbsent(entry.getKey(), __ -> new HashSet<>()).addAll(entry.getValue());
    }
  }

  @VisibleForTesting
  static @NotNull Map<ComponentIdentifier, Set<File>> classify(@NotNull Set<ResolvedArtifactResult> artifacts) {
    Map<ComponentIdentifier, Set<File>> result = new HashMap<>();
    for (ResolvedArtifactResult artifact : artifacts) {
      ComponentArtifactIdentifier identifier = artifact.getId();
      ComponentIdentifier componentIdentifier = identifier.getComponentIdentifier();
      if (componentIdentifier instanceof ModuleComponentIdentifier) {
        File file = artifact.getFile();
        result.computeIfAbsent(componentIdentifier, __ -> new HashSet<>())
          .add(file);
      }
    }
    return result;
  }

  private @NotNull Set<ResolvedArtifactResult> resolve(
    @NotNull Configuration configuration,
    @MagicConstant(stringValues = {DocsType.JAVADOC, DocsType.SOURCES}) @NotNull String docsType,
    @NotNull String artifactType
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
          // request a concrete archive type so IntelliJ receives a readable artifact and Gradle can transform to it
          container.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType);
        });
      })
      .getArtifacts()
      .getArtifacts();
  }
}
