// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.gradle.tooling;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.AuxiliaryArtifactProvider;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary.AuxiliaryConfigurationArtifacts;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides source artifacts for IntelliJ Platform dependencies.
 * <p>
 * IntelliJ Platform sources are published under different Maven coordinates
 * than the main artifacts. For example, PhpStorm sources come from IntelliJ IDEA Community (IC).
 * This provider maps platform dependency coordinates to the correct source coordinates
 * and resolves them via detached Gradle configurations.
 * <p>
 * The IntelliJ Platform bundles third-party libraries whose jars keep the {@code META-INF/maven/<groupId>/<artifactId>/pom.properties}
 * file of the original library. The provider reads these coordinates from every jar of a platform component,
 * resolves the {@code sources} artifacts of all of them in one detached configuration per library version,
 * and attaches each sources archive to its jar. The IntelliJ Platform sources stay attached to the other jars of the component.
 * A jar gets one sources archive, so when every jar of a component packs a library, one jar keeps the IntelliJ Platform sources.
 * <p>
 * The provider resolves each sources notation one time per Gradle project and sync.
 * When the IntelliJ Platform sources are not found, it reports a sync warning with the tried notations.
 * A missing third-party sources archive is skipped without a warning.
 * <p>
 * This code is run on the Gradle daemon side.
 */
@SuppressWarnings("IO_FILE_USAGE") // API uses java.io.File
public final class IntelliJPlatformAuxiliaryArtifactProvider implements AuxiliaryArtifactProvider {

  private static final String JETBRAINS_INTELLIJ_PREFIX = "com.jetbrains.intellij.";
  private static final String LIBRARY_MODULE_PREFIX = "intellij.libraries.";
  private static final String MESSAGE_GROUP = "gradle.import.intellijPlatform.sources";
  private static final String DOCUMENTATION_URL = "https://jb.gg/ijpgp-docs#configuration.repositories";
  private static final String POM_PROPERTIES_PREFIX = "META-INF/maven/";
  private static final String POM_PROPERTIES_SUFFIX = "/pom.properties";

  /**
   * Keeps the resolved sources per Gradle project and sources notation for the current sync.
   */
  private static final ModelBuilderContext.DataProvider<ConcurrentMap<String, Optional<File>>> SOURCES_CACHE =
    context -> new ConcurrentHashMap<>();

  /**
   * Keeps the Maven coordinates read from a jar per jar path for the current sync.
   */
  private static final ModelBuilderContext.DataProvider<ConcurrentMap<String, List<String>>> LIBRARY_COORDINATES_CACHE =
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
    Map<File, List<String>> libraryCoordinates = new LinkedHashMap<>();

    Set<ResolvedComponentResult> components;
    try {
      components = configuration.getIncoming().getResolutionResult().getAllComponents();
    }
    catch (Exception e) {
      return AuxiliaryConfigurationArtifacts.EMPTY;
    }
    Map<ComponentIdentifier, List<File>> artifacts = resolveIntelliJPlatformArtifacts(configuration);

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

      List<File> jars = artifacts.getOrDefault(id, Collections.emptyList());
      Map<File, List<String>> componentCoordinates = new LinkedHashMap<>();
      for (File jar : jars) {
        List<String> coordinates = readLibraryCoordinates(context, jar);
        if (!coordinates.isEmpty()) {
          componentCoordinates.put(jar, coordinates);
        }
      }
      if (sourceFile != null && componentCoordinates.size() == jars.size() && !name.startsWith(LIBRARY_MODULE_PREFIX)) {
        File platformJar = choosePlatformJar(componentCoordinates);
        if (platformJar != null) {
          componentCoordinates.remove(platformJar);
        }
      }
      libraryCoordinates.putAll(componentCoordinates);
    }

    Map<File, File> artifactSources = resolveLibrarySources(context, project, libraryCoordinates);
    if (sources.isEmpty() && artifactSources.isEmpty()) {
      return AuxiliaryConfigurationArtifacts.EMPTY;
    }
    return new AuxiliaryConfigurationArtifacts(sources, Collections.emptyMap(), artifactSources);
  }

  private static boolean isIntelliJPlatformComponent(@NotNull String group) {
    return group.startsWith(JETBRAINS_INTELLIJ_PREFIX)
           || CDN_GROUPS.contains(group)
           || "com.jetbrains.plugins".equals(group)
           || "localIde".equals(group)
           || "bundledPlugin".equals(group)
           || "bundledModule".equals(group);
  }

  /**
   * Chooses the jar that keeps the IntelliJ Platform sources in a component whose every jar has Maven coordinates.
   * A jar gets one sources archive, so without this choice the component would lose the IntelliJ Platform sources.
   * The first jar that is not named after one of its libraries wins, because such a jar packs IntelliJ Platform classes too.
   *
   * @param componentCoordinates the Maven coordinates per jar of the component
   * @return the jar, or {@code null} when every jar is named after one of its libraries
   */
  private static @Nullable File choosePlatformJar(@NotNull Map<File, List<String>> componentCoordinates) {
    for (Map.Entry<File, List<String>> entry : componentCoordinates.entrySet()) {
      if (!isLibraryJar(entry.getKey(), entry.getValue())) {
        return entry.getKey();
      }
    }
    return null;
  }

  /**
   * Checks if {@code jar} is a library jar: a library module jar such as {@code intellij.libraries.commons.cli.jar},
   * or a jar named after one of the libraries in {@code coordinates}.
   */
  private static boolean isLibraryJar(@NotNull File jar, @NotNull List<String> coordinates) {
    String jarName = normalize(jar.getName());
    if (jarName.startsWith(normalize(LIBRARY_MODULE_PREFIX))) {
      return true;
    }
    for (String coordinate : coordinates) {
      if (jarName.contains(normalize(artifactId(coordinate)))) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull String artifactId(@NotNull String coordinate) {
    return coordinate.split(":")[1];
  }

  /**
   * Collects the resolved jars of the IntelliJ Platform components in {@code configuration}.
   */
  private static @NotNull Map<ComponentIdentifier, List<File>> resolveIntelliJPlatformArtifacts(@NotNull Configuration configuration) {
    Map<ComponentIdentifier, List<File>> result = new LinkedHashMap<>();
    try {
      Set<ResolvedArtifactResult> artifacts = configuration.getIncoming()
        .artifactView(view -> {
          view.setLenient(true);
          view.componentFilter(id -> id instanceof ModuleComponentIdentifier
                                     && isIntelliJPlatformComponent(((ModuleComponentIdentifier)id).getGroup()));
        })
        .getArtifacts()
        .getArtifacts();
      for (ResolvedArtifactResult artifact : artifacts) {
        result.computeIfAbsent(artifact.getId().getComponentIdentifier(), k -> new ArrayList<>()).add(artifact.getFile());
      }
    }
    catch (Exception ignore) {
      // the configuration dependencies are reported by the dependency resolver
    }
    return result;
  }

  private static @NotNull List<String> readLibraryCoordinates(@NotNull ModelBuilderContext context, @NotNull File jar) {
    return context.getData(LIBRARY_COORDINATES_CACHE).computeIfAbsent(jar.getAbsolutePath(), k -> readLibraryCoordinates(jar));
  }

  /**
   * Reads the Maven coordinates of the libraries packed into {@code jar} from every
   * {@code META-INF/maven/<groupId>/<artifactId>/pom.properties} entry.
   * A library whose artifact ID is part of the jar name comes first, so a jar that merges a library with its dependencies
   * (for example {@code intellij.libraries.netty.codec.http.jar}) gets the sources of the main library.
   *
   * @return the coordinates in the {@code groupId:artifactId:version} form, or an empty list when the jar has no Maven metadata
   */
  @VisibleForTesting
  public static @NotNull List<String> readLibraryCoordinates(@NotNull File jar) {
    if (!jar.isFile() || !jar.getName().endsWith(".jar")) {
      return Collections.emptyList();
    }
    Map<String, String> artifactIds = new LinkedHashMap<>();
    try (ZipFile zip = new ZipFile(jar)) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String entryName = entry.getName();
        if (!entryName.startsWith(POM_PROPERTIES_PREFIX) || !entryName.endsWith(POM_PROPERTIES_SUFFIX)) continue;
        Properties properties = new Properties();
        try (InputStream stream = zip.getInputStream(entry)) {
          properties.load(stream);
        }
        String groupId = properties.getProperty("groupId");
        String artifactId = properties.getProperty("artifactId");
        String version = properties.getProperty("version");
        if (groupId == null || artifactId == null || version == null) continue;
        artifactIds.put(groupId + ":" + artifactId + ":" + version, artifactId);
      }
    }
    catch (IOException | RuntimeException e) {
      return Collections.emptyList();
    }
    if (artifactIds.isEmpty()) {
      return Collections.emptyList();
    }

    String jarName = normalize(jar.getName());
    List<String> coordinates = new ArrayList<>(artifactIds.keySet());
    coordinates.sort(Comparator.comparingInt((String it) -> {
      String artifactId = normalize(artifactIds.get(it));
      return jarName.contains(artifactId) ? -artifactId.length() : 0;
    }));
    return coordinates;
  }

  private static @NotNull String normalize(@NotNull String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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

    String key = cacheKey(project, notations.get(0));
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

  private static @NotNull String cacheKey(@NotNull Project project, @NotNull String notation) {
    return project.getProjectDir().getAbsolutePath() + "|" + notation;
  }

  /**
   * Resolves the sources archives of the third-party libraries in {@code libraryCoordinates} and maps each jar to one of them.
   * The uncached notations resolve in one detached configuration. A missing archive is cached and skipped without a warning.
   *
   * @param libraryCoordinates the Maven coordinates of the libraries packed into a jar, in the order of preference
   * @return the sources archive of the first resolved library per jar
   */
  private static @NotNull Map<File, File> resolveLibrarySources(
    @NotNull ModelBuilderContext context,
    @NotNull Project project,
    @NotNull Map<File, List<String>> libraryCoordinates
  ) {
    if (libraryCoordinates.isEmpty()) {
      return Collections.emptyMap();
    }
    ConcurrentMap<String, Optional<File>> cache = context.getData(SOURCES_CACHE);

    Set<String> unresolved = new LinkedHashSet<>();
    for (List<String> coordinates : libraryCoordinates.values()) {
      for (String coordinate : coordinates) {
        if (!cache.containsKey(cacheKey(project, sourcesNotation(coordinate)))) {
          unresolved.add(coordinate);
        }
      }
    }
    if (!unresolved.isEmpty()) {
      Map<String, File> resolved = resolveSourceArtifacts(project, unresolved);
      for (String coordinate : unresolved) {
        cache.put(cacheKey(project, sourcesNotation(coordinate)), Optional.ofNullable(resolved.get(coordinate)));
      }
    }

    Map<File, File> result = new LinkedHashMap<>();
    for (Map.Entry<File, List<String>> entry : libraryCoordinates.entrySet()) {
      for (String coordinate : entry.getValue()) {
        Optional<File> file = cache.get(cacheKey(project, sourcesNotation(coordinate)));
        if (file != null && file.isPresent()) {
          result.put(entry.getKey(), file.get());
          break;
        }
      }
    }
    return result;
  }

  private static @NotNull String sourcesNotation(@NotNull String coordinate) {
    return coordinate + ":sources";
  }

  /**
   * Resolves the {@code sources} artifacts of {@code coordinates} in lenient detached configurations of {@code project}.
   * Gradle selects one version per module in a configuration, so each further version of a module goes to a further configuration.
   * The IntelliJ Platform bundles some libraries in two versions, for example {@code guava} in a plugin and in the platform.
   *
   * @return the resolved sources archive per {@code groupId:artifactId:version} coordinate
   */
  private static @NotNull Map<String, File> resolveSourceArtifacts(@NotNull Project project, @NotNull Set<String> coordinates) {
    List<Set<String>> batches = new ArrayList<>();
    Map<String, Integer> versionsPerModule = new HashMap<>();
    for (String coordinate : coordinates) {
      String module = coordinate.substring(0, coordinate.lastIndexOf(':'));
      int batch = versionsPerModule.merge(module, 1, Integer::sum) - 1;
      if (batch == batches.size()) {
        batches.add(new LinkedHashSet<>());
      }
      batches.get(batch).add(coordinate);
    }

    Map<String, File> result = new HashMap<>();
    for (Set<String> batch : batches) {
      result.putAll(resolveSourceArtifactsBatch(project, batch));
    }
    return result;
  }

  /**
   * Resolves the {@code sources} artifacts of {@code coordinates} in one lenient detached configuration of {@code project}.
   * Every module in {@code coordinates} must have one version.
   */
  private static @NotNull Map<String, File> resolveSourceArtifactsBatch(@NotNull Project project, @NotNull Set<String> coordinates) {
    Map<String, File> result = new HashMap<>();
    try {
      Dependency[] dependencies = coordinates.stream()
        .map(coordinate -> project.getDependencies().create(sourcesNotation(coordinate)))
        .toArray(Dependency[]::new);
      Configuration detached = project.getConfigurations().detachedConfiguration(dependencies);
      detached.setTransitive(false);
      Set<ResolvedArtifactResult> artifacts = detached.getIncoming()
        .artifactView(view -> view.setLenient(true))
        .getArtifacts()
        .getArtifacts();
      for (ResolvedArtifactResult artifact : artifacts) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        if (!(id instanceof ModuleComponentIdentifier)) continue;
        ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier)id;
        result.put(moduleId.getGroup() + ":" + moduleId.getModule() + ":" + moduleId.getVersion(), artifact.getFile());
      }
    }
    catch (Exception ignore) {
      // a missing third-party sources archive is not an error of the IntelliJ Platform project
    }
    return result;
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
