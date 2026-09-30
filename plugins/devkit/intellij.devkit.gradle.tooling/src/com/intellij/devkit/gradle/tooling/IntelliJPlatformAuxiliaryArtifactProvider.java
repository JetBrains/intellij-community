// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.gradle.tooling;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.AuxiliaryArtifactProvider;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.AuxiliaryConfigurationArtifacts;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides source artifacts for IntelliJ Platform dependencies.
 * <p>
 * IntelliJ Platform sources are published under different Maven coordinates
 * than the main artifacts. For example, PhpStorm sources come from IntelliJ IDEA Community (IC).
 * This provider maps platform dependency coordinates to the correct source coordinates
 * and resolves them via detached Gradle configurations.
 * <p>
 * The provider resolves each sources notation one time per Gradle project and sync.
 * When the sources are not found, it reports a sync warning with the tried notations.
 * <p>
 * This code is run on the Gradle daemon side.
 */
@SuppressWarnings("IO_FILE_USAGE") // API uses java.io.File
public final class IntelliJPlatformAuxiliaryArtifactProvider implements AuxiliaryArtifactProvider {

  private static final String JETBRAINS_INTELLIJ_PREFIX = "com.jetbrains.intellij.";
  private static final String MESSAGE_GROUP = "gradle.import.intellijPlatform.sources";
  private static final String DOCUMENTATION_URL = "https://jb.gg/ijpgp-docs#configuration.repositories";

  /**
   * Keeps the resolved sources per Gradle project and sources notation for the current sync.
   */
  private static final ModelBuilderContext.DataProvider<ConcurrentMap<String, Optional<File>>> SOURCES_CACHE =
    context -> new ConcurrentHashMap<>();

  @SuppressWarnings("SSBasedInspection") // don't use platform utils here
  private static final Set<String> CDN_GROUPS = new HashSet<>(Arrays.asList(
    // sync with IntelliJPlatformProduct
    "idea",
    "ruby",
    "python",
    "webide",
    "webstorm",
    "cpp",
    "datagrip",
    "rider",
    "go",
    "com.google.android.studio",
    "idea/code-with-me",
    "idea/gateway",
    "aqua",
    "rustrover",
    "mps"
  ));

  @Override
  public @NotNull AuxiliaryConfigurationArtifacts resolve(
    @NotNull ModelBuilderContext context,
    @NotNull Project project,
    @NotNull Configuration configuration,
    @NotNull GradleDependencyDownloadPolicy policy
  ) {
    if (!policy.isDownloadSources()) {
      return AuxiliaryConfigurationArtifacts.EMPTY;
    }

    Map<ComponentIdentifier, Set<File>> sources = new LinkedHashMap<>();

    Set<ResolvedComponentResult> components;
    try {
      components = configuration.getIncoming().getResolutionResult().getAllComponents();
    }
    catch (Exception e) {
      return AuxiliaryConfigurationArtifacts.EMPTY;
    }

    for (ResolvedComponentResult component : components) {
      ComponentIdentifier id = component.getId();
      if (!(id instanceof ModuleComponentIdentifier)) continue;
      ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier)id;

      String group = moduleId.getGroup();
      if (!isIntelliJPlatformComponent(group)) continue;

      String name = moduleId.getModule();
      String version = moduleId.getVersion();
      String actualVersion = IntelliJPlatformSourceCoordinates.extractActualVersion(version);
      int majorVersion = IntelliJPlatformSourceCoordinates.extractMajorVersion(actualVersion);

      String sourceCoordinates = resolveSourceCoordinates(group, name, version, majorVersion);
      if (sourceCoordinates == null) continue;

      File sourceFile = resolveSources(context, project, sourceCoordinates, actualVersion, majorVersion);
      if (sourceFile != null) {
        sources.put(id, Collections.singleton(sourceFile));
      }
    }

    if (sources.isEmpty()) {
      return AuxiliaryConfigurationArtifacts.EMPTY;
    }
    return new AuxiliaryConfigurationArtifacts(sources, Collections.emptyMap());
  }

  private static boolean isIntelliJPlatformComponent(@NotNull String group) {
    return group.startsWith(JETBRAINS_INTELLIJ_PREFIX)
           || CDN_GROUPS.contains(group)
           || "com.jetbrains.plugins".equals(group)
           || "localIde".equals(group)
           || "bundledPlugin".equals(group)
           || "bundledModule".equals(group);
  }

  private static @Nullable String resolveSourceCoordinates(
    @NotNull String group, @NotNull String name, @NotNull String version, int majorVersion
  ) {
    // PyCharm variants → PC sources
    if (("com.jetbrains.intellij.pycharm".equals(group) && ("pycharmPY".equals(name) || "pycharmPC".equals(name)))
        || "python".equals(group)) {
      return IntelliJPlatformSourceCoordinates.PYCHARM_COMMUNITY_SOURCES;
    }

    if (isIdea("ideaIU", group, name)) {
      return IntelliJPlatformSourceCoordinates.ideaUltimateSources(majorVersion);
    }
    if (isIdea("ideaIC", group, name)) {
      return IntelliJPlatformSourceCoordinates.defaultPlatformSources(majorVersion);
    }
    // IDEA (unified coordinates for 253+)
    if (isIdea("idea", group, name)) {
      return "com.jetbrains.intellij.idea:idea";
    }

    // localIde: artifact name is the product code (e.g., "IC", "IU")
    if ("localIde".equals(group)) {
      return IntelliJPlatformSourceCoordinates.sourceCoordinatesForProductCode(name, majorVersion);
    }

    // bundledPlugin / bundledModule: product code is in the version prefix (e.g., "IC-243.21565.193")
    if ("bundledPlugin".equals(group) || "bundledModule".equals(group)) {
      String productCode = IntelliJPlatformSourceCoordinates.extractProductCode(version);
      if (productCode != null) {
        return IntelliJPlatformSourceCoordinates.sourceCoordinatesForProductCode(productCode, majorVersion);
      }
      return IntelliJPlatformSourceCoordinates.defaultPlatformSources(majorVersion);
    }

    // Non-bundled plugins from JetBrains Maven repo
    if (IntelliJPlatformSourceCoordinates.JETBRAINS_PLUGIN_GROUP.equals(group)) {
      if ("PythonCore".equals(name) || "Pythonid".equals(name)) {
        return IntelliJPlatformSourceCoordinates.PYCHARM_COMMUNITY_SOURCES;
      }
      return IntelliJPlatformSourceCoordinates.defaultPlatformSources(majorVersion);
    }

    // All other IntelliJ Platform products → IC/idea sources
    if (group.startsWith(JETBRAINS_INTELLIJ_PREFIX) || CDN_GROUPS.contains(group)) {
      return IntelliJPlatformSourceCoordinates.defaultPlatformSources(majorVersion);
    }

    return null;
  }

  private static boolean isIdea(@NotNull String expectedName, @NotNull String group, @NotNull String name) {
    return ("com.jetbrains.intellij.idea".equals(group) && expectedName.equals(name))
           || ("idea".equals(group) && expectedName.equals(name));
  }

  /**
   * Resolves the sources for {@code sourceCoordinates} in {@code actualVersion}, with the ranged and SNAPSHOT fallbacks.
   * The result is cached per Gradle project, so a failure is reported one time.
   */
  private static @Nullable File resolveSources(
    @NotNull ModelBuilderContext context,
    @NotNull Project project,
    @NotNull String sourceCoordinates,
    @NotNull String actualVersion,
    int majorVersion
  ) {
    List<String> notations = new ArrayList<>();
    notations.add(sourceCoordinates + ":" + actualVersion + ":sources");
    if (majorVersion > 0) {
      // Ranged version fallback: find the closest published version in the same major cycle
      notations.add(sourceCoordinates + ":[" + majorVersion + "," + actualVersion + "]!!" + actualVersion + ":sources");
      // SNAPSHOT fallback
      notations.add(sourceCoordinates + ":" + majorVersion + "-SNAPSHOT:sources");
    }

    String key = project.getProjectDir().getAbsolutePath() + "|" + notations.get(0);
    return context.getData(SOURCES_CACHE).computeIfAbsent(key, k -> {
      Exception failure = null;
      for (String notation : notations) {
        try {
          File file = resolveSourceArtifact(project, notation);
          if (file != null) {
            return Optional.of(file);
          }
        }
        catch (Exception e) {
          if (failure == null) failure = e;
        }
      }
      reportSourcesNotFound(context, project, notations, failure);
      return Optional.empty();
    }).orElse(null);
  }

  private static void reportSourcesNotFound(
    @NotNull ModelBuilderContext context,
    @NotNull Project project,
    @NotNull List<String> notations,
    @Nullable Throwable failure
  ) {
    String text = "Unable to resolve IntelliJ Platform sources in " + project.getDisplayName() + ".\n" +
                  "Tried notations:\n  " + String.join("\n  ", notations) + "\n" +
                  "Add the IntelliJ Platform Maven repositories to the settings.gradle(.kts) file or to this project.\n" +
                  "The IntelliJ Platform SDK Docs show how to set up the repositories: " + DOCUMENTATION_URL;
    if (failure != null) {
      Throwable cause = failure;
      while (cause.getCause() != null && cause.getCause() != cause) {
        cause = cause.getCause();
      }
      text += "\n\nCause: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName());
    }

    context.getMessageReporter().createMessage()
      .withGroup(MESSAGE_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("IntelliJ Platform sources not found")
      .withText(text)
      .reportMessage(project);
  }

  /**
   * Resolves {@code notation} in a detached configuration of {@code project}.
   * Gradle throws an unchecked exception when it cannot resolve the notation.
   *
   * @return the single resolved file, or {@code null} when the resolution result is not a single file
   */
  private static @Nullable File resolveSourceArtifact(@NotNull Project project, @NotNull String notation) {
    Configuration detached = project.getConfigurations().detachedConfiguration(project.getDependencies().create(notation));
    detached.setTransitive(false);
    Set<File> files = detached.resolve();
    if (files.size() == 1) {
      return files.iterator().next();
    }
    return null;
  }
}
