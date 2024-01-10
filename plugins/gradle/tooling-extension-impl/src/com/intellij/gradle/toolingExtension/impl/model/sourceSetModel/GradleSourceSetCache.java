// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.api.Project;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public class GradleSourceSetCache {

  private final ModelBuilderContext context;
  private final ConcurrentMap<ProjectIdentifier, DefaultGradleSourceSetModel> allSourceSetModels;

  private GradleSourceSetCache(@NotNull ModelBuilderContext context) {
    this.context = context;
    this.allSourceSetModels = new ConcurrentHashMap<>();
  }

  public @NotNull DefaultGradleSourceSetModel getSourceSetModel(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    DefaultGradleSourceSetModel sourceSetModel = allSourceSetModels.get(projectIdentifier);
    if (sourceSetModel == null) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.SOURCE_SET_CACHE_GET_GROUP)
        .withTitle("Source set model isn't found")
        .withText(
          "Source sets for " + project.getDisplayName() + " wasn't collected. " +
          "All source sets should be collected during " + GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE + "."
        )
        .withStackTrace()
        .withKind(Message.Kind.INTERNAL)
        .reportMessage(project);
      return new DefaultGradleSourceSetModel();
    }
    return sourceSetModel;
  }

  public void setSourceSetModel(@NotNull Project project, @NotNull DefaultGradleSourceSetModel sourceSetModel) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    DefaultGradleSourceSetModel previousSourceSetModel = allSourceSetModels.put(projectIdentifier, sourceSetModel);
    if (previousSourceSetModel != null) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.SOURCE_SET_CACHE_SET_GROUP)
        .withTitle("Source set model redefinition")
        .withText("Source sets for " + project.getDisplayName() + " was already collected.")
        .withStackTrace()
        .withKind(Message.Kind.INTERNAL)
        .reportMessage(project);
    }
  }

  /**
   * Marks that a project source set model is loaded with errors.
   * This mark means that error for {@code project} is already processed and reported.
   */
  public void markSourceSetModelAsError(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    allSourceSetModels.put(projectIdentifier, new DefaultGradleSourceSetModel());
  }

  private static final @NotNull ModelBuilderContext.DataProvider<GradleSourceSetCache> INSTANCE_PROVIDER = GradleSourceSetCache::new;

  public static @NotNull GradleSourceSetCache getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}
