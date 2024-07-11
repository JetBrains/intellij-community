// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel.GradleResourceFilterModelBuilder;
import com.intellij.gradle.toolingExtension.impl.util.GradleIdeaPluginUtil;
import com.intellij.gradle.toolingExtension.impl.util.GradleObjectUtil;
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public class GradleSourceSetResolutionContext {

  public final @Nullable String projectSourceCompatibility;
  public final @Nullable String projectTargetCompatibility;

  public final @NotNull Collection<SourceSet> testSourceSets;

  public final boolean isIdeaInheritOutputDirs;
  public final @Nullable File ideaOutputDir;
  public final @Nullable File ideaTestOutputDir;

  public final @NotNull Set<File> ideaSourceDirs;
  public final @NotNull Set<File> ideaResourceDirs;
  public final @NotNull Set<File> ideaTestSourceDirs;
  public final @NotNull Set<File> ideaTestResourceDirs;
  public final @NotNull Set<File> ideaGeneratedSourceDirs;

  public final @NotNull Set<File> unprocessedIdeaGeneratedSourceDirs;

  public final @NotNull Set<String> resourcesIncludes;
  public final @NotNull Set<String> resourcesExcludes;
  public final @NotNull Set<String> testResourcesIncludes;
  public final @NotNull Set<String> testResourcesExcludes;
  public final @NotNull List<DefaultExternalFilter> resourceFilters;
  public final @NotNull List<DefaultExternalFilter> testResourceFilters;

  public GradleSourceSetResolutionContext(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    projectSourceCompatibility = JavaPluginUtil.getSourceCompatibility(project);
    projectTargetCompatibility = JavaPluginUtil.getTargetCompatibility(project);

    testSourceSets = GradleSourceSetModelBuilder.collectTestSourceSets(project);


    IdeaModule ideaPluginModule = GradleIdeaPluginUtil.getIdeaModule(project);
    if (ideaPluginModule != null) {
      isIdeaInheritOutputDirs = GradleObjectUtil.notNull(ideaPluginModule.getInheritOutputDirs(), false);
      ideaOutputDir = ideaPluginModule.getOutputDir();
      ideaTestOutputDir = ideaPluginModule.getTestOutputDir();

      ideaSourceDirs = GradleIdeaPluginUtil.getSourceDirectories(ideaPluginModule);
      ideaResourceDirs = GradleIdeaPluginUtil.getResourceDirectories(ideaPluginModule);
      ideaTestSourceDirs = GradleIdeaPluginUtil.getTestSourceDirectories(ideaPluginModule);
      ideaTestResourceDirs = GradleIdeaPluginUtil.getTestResourceDirectories(ideaPluginModule);
      ideaGeneratedSourceDirs = GradleIdeaPluginUtil.getGeneratedSourceDirectories(ideaPluginModule);
    }
    else {
      isIdeaInheritOutputDirs = false;
      ideaOutputDir = null;
      ideaTestOutputDir = null;

      ideaSourceDirs = new LinkedHashSet<>();
      ideaResourceDirs = new LinkedHashSet<>();
      ideaTestSourceDirs = new LinkedHashSet<>();
      ideaTestResourceDirs = new LinkedHashSet<>();
      ideaGeneratedSourceDirs = new LinkedHashSet<>();
    }
    unprocessedIdeaGeneratedSourceDirs = new LinkedHashSet<>(ideaGeneratedSourceDirs);

    resourcesIncludes = GradleResourceFilterModelBuilder.getIncludes(project, "processResources");
    resourcesExcludes = GradleResourceFilterModelBuilder.getExcludes(project, "processResources");
    resourceFilters = GradleResourceFilterModelBuilder.getFilters(project, context, "processResources");
    testResourcesIncludes = GradleResourceFilterModelBuilder.getIncludes(project, "processTestResources");
    testResourcesExcludes = GradleResourceFilterModelBuilder.getExcludes(project, "processTestResources");
    testResourceFilters = GradleResourceFilterModelBuilder.getFilters(project, context, "processTestResources");
  }

  public boolean isJavaTestSourceSet(@NotNull SourceSet sourceSet) {
    String sourceSetName = sourceSet.getName();
    Set<File> sourceDirs = sourceSet.getAllJava().getSrcDirs();

    boolean resolveSourceSetDependencies = Boolean.getBoolean("idea.resolveSourceSetDependencies");
    boolean isIdeaTestSourceSet = ideaTestSourceDirs.containsAll(sourceDirs);
    boolean isKnownTestSourceSet = testSourceSets.contains(sourceSet);
    boolean isCustomTestSourceSet = (isIdeaTestSourceSet || isKnownTestSourceSet) && !SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSetName);
    return SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName()) || resolveSourceSetDependencies && isCustomTestSourceSet;
  }
}
