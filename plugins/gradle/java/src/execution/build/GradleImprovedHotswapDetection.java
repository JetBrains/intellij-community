// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.task.ProjectTaskContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@ApiStatus.Experimental
public final class GradleImprovedHotswapDetection {

  private GradleImprovedHotswapDetection() {
    // static class cannot be constructed constructor
  }

  public static void processInitScriptOutput(ProjectTaskContext context, @Nullable Path outputPathsFile) {
    if (outputPathsFile == null) {
      return;
    }

    Collection<GradleImprovedHotswapOutput> outputs = GradleImprovedHotswapOutput.parseOutputFile(outputPathsFile);

    outputs.stream()
      .filter(output -> !StringUtil.isEmpty(output.getPath()))
      .forEach(output -> context.fileGenerated(output.getRoot(), output.getPath()));

    Set<String> dirtyRoots = outputs.stream()
      .filter(output -> StringUtil.isEmpty(output.getPath()))
      .map(GradleImprovedHotswapOutput::getRoot)
      .collect(Collectors.toSet());

    context.addDirtyOutputPathsProvider(() -> dirtyRoots);
  }

  public static boolean isEnabled() {
    return Registry.is("gradle.improved.hotswap.detection", false);
  }
}
