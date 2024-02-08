// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import com.intellij.gradle.toolingExtension.impl.util.GradleIdeaPluginUtil;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@ApiStatus.Internal
class GradleSourceSetResolutionContext {

  public final @NotNull Collection<SourceSet> testSourceSets;

  public final @NotNull Set<File> ideaSourceDirs;
  public final @NotNull Set<File> ideaResourceDirs;
  public final @NotNull Set<File> ideaTestSourceDirs;
  public final @NotNull Set<File> ideaTestResourceDirs;
  public final @NotNull Set<File> ideaGeneratedSourceDirs;

  public final @NotNull Set<File> unprocessedIdeaGeneratedSourceDirs;

  GradleSourceSetResolutionContext(
    @NotNull Project project,
    @Nullable IdeaModule ideaPluginModule
  ) {
    testSourceSets = GradleSourceSetModelBuilder.collectTestSourceSets(project);

    if (ideaPluginModule != null) {
      ideaSourceDirs = GradleIdeaPluginUtil.getSourceDirectories(ideaPluginModule);
      ideaResourceDirs = GradleIdeaPluginUtil.getResourceDirectories(ideaPluginModule);
      ideaTestSourceDirs = GradleIdeaPluginUtil.getTestSourceDirectories(ideaPluginModule);
      ideaTestResourceDirs = GradleIdeaPluginUtil.getTestResourceDirectories(ideaPluginModule);
      ideaGeneratedSourceDirs = GradleIdeaPluginUtil.getGeneratedSourceDirectories(ideaPluginModule);
    }
    else {
      ideaSourceDirs = new LinkedHashSet<>();
      ideaResourceDirs = new LinkedHashSet<>();
      ideaTestSourceDirs = new LinkedHashSet<>();
      ideaTestResourceDirs = new LinkedHashSet<>();
      ideaGeneratedSourceDirs = new LinkedHashSet<>();
    }
    unprocessedIdeaGeneratedSourceDirs = new LinkedHashSet<>(ideaGeneratedSourceDirs);
  }
}
