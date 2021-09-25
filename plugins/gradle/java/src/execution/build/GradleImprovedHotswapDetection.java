// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.task.ProjectTaskContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@ApiStatus.Experimental
public final class GradleImprovedHotswapDetection {
  private GradleImprovedHotswapDetection() {
    // static class cannot be constructed constructor
  }

  public static String getInitScript(File outputPathsFile) {
    String path = FileUtil.toCanonicalPath(outputPathsFile.getAbsolutePath());
    return String.format(InitScriptResourceHolder.INIT_SCRIPT_TEMPLATE, path);
  }

  public static String getInitScriptUsingService(File outputPathsFile) {
    String path = FileUtil.toCanonicalPath(outputPathsFile.getAbsolutePath());
    return String.format(InitScriptResourceHolder.INIT_SCRIPT_TEMPLATE_USING_SERVICE, path);
  }

  public static void processInitScriptOutput(ProjectTaskContext context, @Nullable File outputPathsFile) {
    if (outputPathsFile == null || !context.isCollectionOfGeneratedFilesEnabled()) {
      return;
    }

    Collection<GradleImprovedHotswapOutput> outputs = GradleImprovedHotswapOutput.parseOutputFile(outputPathsFile);

    outputs.stream()
      .filter(output -> !StringUtil.isEmpty(output.getPath()))
      .forEach(output -> context.fileGenerated(output.getRoot(), output.getPath()));

    Set<String> dirtyRoots = outputs.stream()
      .map(GradleImprovedHotswapOutput::getRoot)
      .collect(Collectors.toSet());

    context.addDirtyOutputPathsProvider(() -> dirtyRoots);
  }

  public static boolean isEnabled() {
    return Registry.is("gradle.improved.hotswap.detection", false);
  }

  // Utility class to lazily load the GradleImprovedHotswapDetectionInitScript.groovy resource
  private static class InitScriptResourceHolder {
    private static final String PATH_SIMPLE = "/org/jetbrains/plugins/gradle/GradleImprovedHotswapDetectionInitScript.groovy";
    private static final String PATH_USING_SERVICE = "/org/jetbrains/plugins/gradle/GradleImprovedHotswapDetectionInitScriptUsingService.groovy";
    private static final String COMMON_HASH_UTILS = "/org/jetbrains/plugins/gradle/GradleImprovedHotswapDetectionUtils.groovy";

    static final String COMMON_HASH_UTILS_CLASS = loadResource(COMMON_HASH_UTILS);
    static final String INIT_SCRIPT_TEMPLATE = loadResource(PATH_SIMPLE) + COMMON_HASH_UTILS_CLASS;
    static final String INIT_SCRIPT_TEMPLATE_USING_SERVICE = loadResource(PATH_USING_SERVICE) + COMMON_HASH_UTILS_CLASS;

    private static String loadResource(String path) {
      try (
        InputStream stream = GradleProjectTaskRunner.class.getResourceAsStream(path);
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return StreamUtil.readText(reader);
      }
      catch (IOException e) {
        throw new IllegalStateException(String.format("Could not load resource '%s'", path), e);
      }
    }
  }
}
