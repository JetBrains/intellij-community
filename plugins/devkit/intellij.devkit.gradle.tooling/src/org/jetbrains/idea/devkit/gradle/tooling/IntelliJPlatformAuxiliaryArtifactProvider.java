// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle.tooling;

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

import java.io.File;
import java.util.*;

/**
 * Provides source artifacts for IntelliJ Platform dependencies.
 * <p>
 * IntelliJ Platform sources are published under different Maven coordinates
 * than the main artifacts. For example, PhpStorm sources come from IntelliJ IDEA Community (IC).
 * This provider maps platform dependency coordinates to the correct source coordinates
 * and resolves them via detached Gradle configurations.
 * <p>
 * This code is run on the Gradle daemon side.
 */
@SuppressWarnings("IO_FILE_USAGE") // API uses java.io.File
public final class IntelliJPlatformAuxiliaryArtifactProvider implements AuxiliaryArtifactProvider {

  private static final String JETBRAINS_INTELLIJ_PREFIX = "com.jetbrains.intellij.";

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

      String sourceNotation = sourceCoordinates + ":" + actualVersion + ":sources";
      File sourceFile = resolveSourceArtifact(project, sourceNotation);
      if (sourceFile == null && majorVersion > 0) {
        // Try ranged version fallback: find closest published version in the same major cycle
        String rangedNotation = sourceCoordinates + ":[" + majorVersion + "," + actualVersion + "]!!" + actualVersion + ":sources";
        sourceFile = resolveSourceArtifact(project, rangedNotation);
      }
      if (sourceFile == null && majorVersion > 0) {
        // Try SNAPSHOT fallback
        String snapshotNotation = sourceCoordinates + ":" + majorVersion + "-SNAPSHOT:sources";
        sourceFile = resolveSourceArtifact(project, snapshotNotation);
      }

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
    if ("com.jetbrains.plugins".equals(group)) {
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

  private static @Nullable File resolveSourceArtifact(@NotNull Project project, @NotNull String notation) {
    try {
      Configuration detached = project.getConfigurations().detachedConfiguration(project.getDependencies().create(notation));
      detached.setTransitive(false);
      Set<File> files = detached.resolve();
      if (files.size() == 1) {
        return files.iterator().next();
      }
      return null;
    }
    catch (Exception e) {
      return null;
    }
  }
}
