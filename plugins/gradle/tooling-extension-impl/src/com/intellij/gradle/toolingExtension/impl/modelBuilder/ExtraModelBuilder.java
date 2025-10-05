// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import com.google.common.collect.Lists;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Vladislav.Soroka
 */
public class ExtraModelBuilder implements ParameterizedToolingModelBuilder<ModelBuilderService.Parameter> {

  private final List<ModelBuilderService> modelBuilderServices =
    Lists.newArrayList(ServiceLoader.load(ModelBuilderService.class, ExtraModelBuilder.class.getClassLoader()));

  private final AtomicReference<DefaultModelBuilderContext> modelBuilderContext = new AtomicReference<>(null);

  @Override
  public @NotNull Class<ModelBuilderService.Parameter> getParameterType() {
    return ModelBuilderService.Parameter.class;
  }

  @Override
  public boolean canBuild(@NotNull String modelName) {
    for (ModelBuilderService service : modelBuilderServices) {
      if (service.canBuild(modelName)) return true;
    }
    return false;
  }

  @Override
  @SuppressWarnings("NullableProblems")
  public @Nullable Object buildAll(
    @NotNull String modelName,
    @NotNull Project project
  ) {
    return buildAll(modelName, null, project);
  }

  @Override
  @SuppressWarnings("NullableProblems")
  public @Nullable Object buildAll(
    @NotNull String modelName,
    @Nullable ModelBuilderService.Parameter parameter,
    @NotNull Project project
  ) {
    ModelBuilderContext context = modelBuilderContext.updateAndGet(it -> {
      if (it == null) {
        Gradle rootGradle = getRootGradle(project.getGradle());
        return new DefaultModelBuilderContext(rootGradle);
      }
      return it;
    });

    for (ModelBuilderService service : modelBuilderServices) {
      if (service.canBuild(modelName)) {
        return buildServiceModel(modelName, project, context, service, parameter);
      }
    }
    throw new IllegalArgumentException("Unsupported model: " + modelName);
  }

  private static @Nullable Object buildServiceModel(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull ModelBuilderService service,
    @Nullable ModelBuilderService.Parameter parameter
  ) {
    final long startTime = System.currentTimeMillis();
    try {
      if (service instanceof ModelBuilderService.ParameterizedModelBuilderService) {
        return ((ModelBuilderService.ParameterizedModelBuilderService)service)
          .buildAll(modelName, project, context, parameter);
      }
      if (service instanceof ModelBuilderService.Ex) {
        return ((ModelBuilderService.Ex)service).buildAll(modelName, project, context);
      }
      else {
        return service.buildAll(modelName, project);
      }
    }
    catch (Exception exception) {
      reportErrorMessage(modelName, project, context, service, exception);
      return null;
    }
    finally {
      if (Boolean.getBoolean("idea.gradle.custom.tooling.perf")) {
        final long timeInMs = (System.currentTimeMillis() - startTime);
        reportPerformanceStatistic(project, service, modelName, timeInMs);
      }
    }
  }

  private static void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull ModelBuilderService service,
    @NotNull Exception exception
  ) {
    @SuppressWarnings("deprecation")
    ErrorMessageBuilder builder = service.getErrorMessageBuilder(project, exception);
    if (builder != null) {
      context.getMessageReporter().reportMessage(project, builder.buildMessage());
    }
    else {
      service.reportErrorMessage(modelName, project, context, exception);
    }
  }

  private static void reportPerformanceStatistic(
    @NotNull Project project,
    @NotNull ModelBuilderService service,
    @NotNull String modelName,
    long timeInMs
  ) {
    String projectName = project.getDisplayName();
    String serviceName = service.getClass().getSimpleName();
    String msg = String.format("%s: service %s imported '%s' in %d ms", projectName, serviceName, modelName, timeInMs);
    project.getLogger().error(msg);
  }

  private static @NotNull Gradle getRootGradle(@NotNull Gradle gradle) {
    Gradle root = gradle;
    while (root.getParent() != null) {
      root = root.getParent();
    }
    return root;
  }
}
