// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.projectModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.api.Project;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.DefaultExternalProject;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext.DataProvider;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GradleExternalProjectCache {

  private final ModelBuilderContext context;
  private final ConcurrentMap<ProjectIdentifier, DefaultExternalProject> models;

  private GradleExternalProjectCache(@NotNull ModelBuilderContext context) {
    this.context = context;
    this.models = new ConcurrentHashMap<>();
  }

  public @NotNull DefaultExternalProject getProjectModel(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    DefaultExternalProject model = models.get(projectIdentifier);
    if (model == null) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.PROJECT_MODEL_CACHE_GET_GROUP)
        .withTitle("Project model isn't found")
        .withText(
          "Projects for " + project.getDisplayName() + " wasn't collected. " +
          "All projects should be collected during " + GradleModelFetchPhase.PROJECT_MODEL_PHASE + "."
        )
        .withInternal().withStackTrace()
        .withKind(Message.Kind.ERROR)
        .reportMessage(project);
      return new DefaultExternalProject();
    }
    return model;
  }

  public void setProjectModel(@NotNull Project project, @NotNull DefaultExternalProject model) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    DefaultExternalProject previousModel = models.put(projectIdentifier, model);
    if (previousModel != null) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.PROJECT_MODEL_CACHE_SET_GROUP)
        .withTitle("Project model redefinition")
        .withText("Projects for " + project.getDisplayName() + " was already collected.")
        .withInternal().withStackTrace()
        .withKind(Message.Kind.ERROR)
        .reportMessage(project);
    }
  }

  /**
   * Marks that an external project model is loaded with errors.
   * This mark means that error for {@code project} is already processed and reported.
   */
  public void markProjectModelAsError(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    models.put(projectIdentifier, new DefaultExternalProject());
  }

  private static final @NotNull DataProvider<GradleExternalProjectCache> INSTANCE_PROVIDER = GradleExternalProjectCache::new;

  public static @NotNull GradleExternalProjectCache getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}
