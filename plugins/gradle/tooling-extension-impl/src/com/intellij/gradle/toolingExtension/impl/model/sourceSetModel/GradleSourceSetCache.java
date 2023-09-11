// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public class GradleSourceSetCache {

  private final ModelBuilderContext context;
  private final ConcurrentMap<Project, DefaultGradleSourceSetModel> allSourceSetModels;

  private GradleSourceSetCache(@NotNull ModelBuilderContext context) {
    this.context = context;
    this.allSourceSetModels = new ConcurrentHashMap<>();
  }

  public @NotNull DefaultGradleSourceSetModel getSourceSetModel(@NotNull Project project) {
    DefaultGradleSourceSetModel sourceSetModel = allSourceSetModels.get(project);
    if (sourceSetModel == null) {
      Exception stackTrace = new IllegalStateException();
      Message message = MessageBuilder.create(
        "Project source set model isn't found",
        "Source sets for " + project + " wasn't collected. " +
        "All source sets should be collected during " + GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE + "."
      ).error().withException(stackTrace).build();
      context.report(project, message);
      return new DefaultGradleSourceSetModel();
    }
    return sourceSetModel;
  }

  public void setSourceSetModel(@NotNull Project project, @NotNull DefaultGradleSourceSetModel sourceSetModel) {
    DefaultGradleSourceSetModel previousSourceSetModel = allSourceSetModels.put(project, sourceSetModel);
    if (previousSourceSetModel != null) {
      Message message = MessageBuilder.create(
        "Project source set model redefinition",
        "Source sets for " + project + " was already collected."
      ).error().build();
      context.report(project, message);
    }
  }

  private static final @NotNull ModelBuilderContext.DataProvider<GradleSourceSetCache> INSTANCE_PROVIDER = GradleSourceSetCache::new;

  public static @NotNull GradleSourceSetCache getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}
