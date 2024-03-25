// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel;

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
public class GradleSourceSetDependencyCache {

  private final ModelBuilderContext context;
  private final ConcurrentMap<ProjectIdentifier, DefaultGradleSourceSetDependencyModel> allSourceSetDependencyModels;

  private GradleSourceSetDependencyCache(@NotNull ModelBuilderContext context) {
    this.context = context;
    this.allSourceSetDependencyModels = new ConcurrentHashMap<>();
  }

  public @NotNull DefaultGradleSourceSetDependencyModel getSourceSetDependencyModel(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    DefaultGradleSourceSetDependencyModel sourceSetDependencyModel = allSourceSetDependencyModels.get(projectIdentifier);
    if (sourceSetDependencyModel == null) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.SOURCE_SET_DEPENDENCY_MODEL_CACHE_GET_GROUP)
        .withTitle("Source set model isn't found")
        .withText(
          "Source set dependencies for " + project.getDisplayName() + " wasn't collected. " +
          "All source set dependencies should be collected during " + GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE + "."
        )
        .withInternal().withStackTrace()
        .withKind(Message.Kind.ERROR)
        .reportMessage(project);
      return new DefaultGradleSourceSetDependencyModel();
    }
    return sourceSetDependencyModel;
  }

  public void setSourceSetDependencyModel(
    @NotNull Project project,
    @NotNull DefaultGradleSourceSetDependencyModel sourceSetDependencyModel
  ) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    DefaultGradleSourceSetDependencyModel previousSourceSetDependencyModel =
      allSourceSetDependencyModels.put(projectIdentifier, sourceSetDependencyModel);
    if (previousSourceSetDependencyModel != null) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.SOURCE_SET_DEPENDENCY_MODEL_CACHE_SET_GROUP)
        .withTitle("Source set dependency model redefinition")
        .withText("Source set dependencies for " + project.getDisplayName() + " was already collected.")
        .withInternal().withStackTrace()
        .withKind(Message.Kind.ERROR)
        .reportMessage(project);
    }
  }

  /**
   * Marks that a project source set model is loaded with errors.
   * This mark means that error for {@code project} is already processed and reported.
   */
  public void markSourceSetDependencyModelAsError(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    allSourceSetDependencyModels.put(projectIdentifier, new DefaultGradleSourceSetDependencyModel());
  }

  private static final @NotNull ModelBuilderContext.DataProvider<GradleSourceSetDependencyCache> INSTANCE_PROVIDER =
    GradleSourceSetDependencyCache::new;

  public static @NotNull GradleSourceSetDependencyCache getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}
