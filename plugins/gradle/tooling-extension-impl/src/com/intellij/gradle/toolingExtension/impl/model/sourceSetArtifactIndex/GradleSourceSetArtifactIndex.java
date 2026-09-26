// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.jetbrains.plugins.gradle.tooling.ModelBuilderContext.DataProvider;

public class GradleSourceSetArtifactIndex {

  private final @NotNull ModelBuilderContext myContext;
  private final @NotNull ConcurrentMap<ProjectIdentifier, Boolean> myProjectProcessingStatus;

  private final @NotNull ConcurrentMap<String, SourceSet> mySourceSetArtifactMap;
  private final @NotNull ConcurrentMap<String, String> mySourceSetOutputArtifactMap;
  private final @NotNull ConcurrentMap<String, Set<File>> mySourceMap;

  private GradleSourceSetArtifactIndex(@NotNull ModelBuilderContext context) {
    myContext = context;
    myProjectProcessingStatus = new ConcurrentHashMap<>();
    mySourceSetArtifactMap = new ConcurrentHashMap<>();
    mySourceSetOutputArtifactMap = new ConcurrentHashMap<>();
    mySourceMap = new ConcurrentHashMap<>();
  }

  public @NotNull Set<File> findArtifactSources(@NotNull Collection<? extends File> artifactFiles) {
    Set<File> artifactSources = new LinkedHashSet<>();
    for (File artifactFile : artifactFiles) {
      artifactSources.addAll(findSourcesByArtifact(artifactFile.getPath()));
    }
    return artifactSources;
  }

  public @NotNull Set<File> findArtifactSources(@NotNull File artifactFile) {
    return findSourcesByArtifact(artifactFile.getPath());
  }

  private @NotNull Set<File> findSourcesByArtifact(@NotNull String path) {
    return mySourceMap.computeIfAbsent(path, it -> {
      SourceSet sourceSet = mySourceSetArtifactMap.get(it);
      if (sourceSet == null) {
        return Collections.emptySet();
      }
      return sourceSet.getAllJava().getSrcDirs();
    });
  }

  public @Nullable SourceSet findByArtifact(@NotNull String artifactPath) {
    return mySourceSetArtifactMap.get(artifactPath);
  }

  public @Nullable String findArtifactBySourceSetOutputDir(@NotNull String outputPath) {
    return mySourceSetOutputArtifactMap.get(outputPath);
  }

  public void setSourceSetArtifactModel(@NotNull Project project, @NotNull GradleSourceSetArtifactModel model) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    Boolean projectProcessingStatus = myProjectProcessingStatus.put(projectIdentifier, true);
    if (projectProcessingStatus != null) {
      myContext.getMessageReporter().createMessage()
        .withGroup(Messages.SOURCE_SET_ARTIFACT_INDEX_CACHE_SET_GROUP)
        .withTitle("Source set artifact index model redefinition")
        .withText("Source set artifact index model for " + project.getDisplayName() + " was already collected.")
        .withInternal().withStackTrace()
        .withKind(Message.Kind.ERROR)
        .reportMessage(project);
    }

    mySourceSetArtifactMap.putAll(model.getSourceSetArtifactMap());
    mySourceSetOutputArtifactMap.putAll(model.getSourceSetOutputArtifactMap());
  }

  /**
   * Marks that a project source set artifact model is loaded with errors.
   * This mark means that error for {@code project} is already processed and reported.
   */
  public void markSourceSetArtifactModelAsError(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    myProjectProcessingStatus.put(projectIdentifier, false);
  }

  private static final @NotNull DataProvider<GradleSourceSetArtifactIndex> INSTANCE_PROVIDER = GradleSourceSetArtifactIndex::new;

  public static @NotNull GradleSourceSetArtifactIndex getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}

