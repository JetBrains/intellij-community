// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.google.gson.Gson;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public class ArchivedCompilationContextUtil {
  private static Map<String, List<String>> ourArchivedCompiledClassesMapping;

  /**
   * NB: actual jars might be in subdirectories
   */
  public static @Nullable String getArchivedCompiledClassesLocation() {
    String relevantJarsRoot = System.getProperty("intellij.test.jars.location");
    if (relevantJarsRoot != null) {
      return relevantJarsRoot;
    }

    relevantJarsRoot = getArchivedCompiledClassesLocationIfIsRunningFromBazelOut();
    boolean isRunningFromBazelOut = relevantJarsRoot != null;
    if (isRunningFromBazelOut) {
      return relevantJarsRoot;
    }

    return null;
  }

  private static @Nullable String getArchivedCompiledClassesLocationIfIsRunningFromBazelOut() {
    String utilJar = PathManager.getJarPathForClass(PathManager.class);
    String bazelOutPattern = Paths.get("bazel-out", "jvm-fastbuild").toString();
    int index = utilJar != null ? utilJar.indexOf(bazelOutPattern) : -1;
    boolean isRunningFromBazelOut = index != -1 && utilJar.endsWith(".jar");
    return isRunningFromBazelOut ? utilJar.substring(0, index + bazelOutPattern.length()) : null;
  }

  /**
   * Returns a map of IntelliJ modules to .jar absolute paths, e.g.:
   * "production/intellij.platform.util" => ".../production/intellij.platform.util/$hash.jar"
   */
  public static @Nullable Map<String, List<String>> getArchivedCompiledClassesMapping() {
    if (ourArchivedCompiledClassesMapping == null) {
      ourArchivedCompiledClassesMapping = computeArchivedCompiledClassesMapping();
    }
    return ourArchivedCompiledClassesMapping;
  }

  private static @Nullable Map<String, List<String>> computeArchivedCompiledClassesMapping() {
    final String filePath = System.getProperty("intellij.test.jars.mapping.file");
    if (StringUtilRt.isEmptyOrSpaces(filePath)) {
      if (getArchivedCompiledClassesLocationIfIsRunningFromBazelOut() != null) {
        return computeArchivedCompiledClassesMappingIfIsRunningFromBazelOut();
      }
      return null;
    }
    final List<String> lines;
    try {
      lines = Files.readAllLines(Paths.get(filePath));
    }
    catch (Exception e) {
      log("Failed to load jars mappings from " + filePath);
      return null;
    }
    final Map<String, List<String>> mapping = new HashMap<>(lines.size());
    for (String line : lines) {
      String[] split = line.split("=", 2);
      if (split.length < 2) {
        log("Ignored jars mapping line: " + line);
        continue;
      }
      mapping.put(split[0], Collections.singletonList(split[1]));
    }
    return Collections.unmodifiableMap(mapping);
  }

  private static @Nullable @UnmodifiableView Map<String, List<String>> computeArchivedCompiledClassesMappingIfIsRunningFromBazelOut() {
    final BazelTargetsInfo.TargetsFile targetsFile;
    try {
      targetsFile = BazelTargetsInfo.loadTargetsFileFromBazelTargetsJson(PathManager.getHomeDir());
    }
    catch (IOException e) {
      log("Failed to load targets info from bazel-targets.json");
      return null;
    }

    final Map<String, List<String>> result = new HashMap<>();
    targetsFile.modules.forEach((moduleName, desc) -> {
      if (!desc.productionJars.isEmpty()) {
        result.put("production/" + moduleName, ContainerUtil.map(desc.productionJars, s -> PathManager.getHomeDir().resolve(s).toString()));
      }
      if (!desc.testJars.isEmpty()) {
        result.put("test/" + moduleName, ContainerUtil.map(desc.testJars, s -> PathManager.getHomeDir().resolve(s).toString()));
      }
    });

    return Collections.unmodifiableMap(result);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void log(String x) {
    System.err.println(x);
  }

  private static final class BazelTargetsInfo {
    public static TargetsFile loadTargetsFileFromBazelTargetsJson(@NotNull Path projectRoot) throws IOException {
      final Path bazelTargetsJsonFile = projectRoot.resolve("build").resolve("bazel-targets.json");
      try (BufferedReader bufferedReader = Files.newBufferedReader(bazelTargetsJsonFile)) {
        return new Gson().fromJson(bufferedReader, TargetsFile.class);
      }
    }

    public static final class TargetsFileModuleDescription {
      public List<String> productionJars;
      public List<String> testJars;
    }

    public static final class TargetsFile {
      public Map<String, TargetsFileModuleDescription> modules;
    }
  }
}
