// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.impldep.com.google.common.collect.Lists;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.gradle.model.internal.DummyModel;
import org.jetbrains.plugins.gradle.model.internal.TurnOffDefaultTasks;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import com.intellij.gradle.toolingExtension.impl.model.projectModel.ExternalProjectBuilderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public class ExtraModelBuilder implements ToolingModelBuilder {

  public static class ForGradle44 extends ExtraModelBuilder implements ParameterizedToolingModelBuilder<ModelBuilderService.Parameter> {
    @NotNull
    @Override
    public Class<ModelBuilderService.Parameter> getParameterType() {
      return ModelBuilderService.Parameter.class;
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger("org.jetbrains.plugins.gradle.toolingExtension.modelBuilder");

  private final List<ModelBuilderService> modelBuilderServices;

  @NotNull
  private final GradleVersion myCurrentGradleVersion;
  private DefaultModelBuilderContext myModelBuilderContext;

  public ExtraModelBuilder() {
    this(GradleVersion.current());
  }

  @TestOnly
  public ExtraModelBuilder(@NotNull GradleVersion gradleVersion) {
    myCurrentGradleVersion = gradleVersion;
    modelBuilderServices = Lists.newArrayList(ServiceLoader.load(ModelBuilderService.class, ExtraModelBuilder.class.getClassLoader()));
  }

  @Override
  public boolean canBuild(String modelName) {
    if (DummyModel.class.getName().equals(modelName)) return true;
    if (TurnOffDefaultTasks.class.getName().equals(modelName)) return true;
    for (ModelBuilderService service : modelBuilderServices) {
      if (service.canBuild(modelName)) return true;
    }
    return false;
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    return buildAll(modelName, null, project);
  }

  public Object buildAll(String modelName, ModelBuilderService.Parameter parameter, Project project) {
    if (DummyModel.class.getName().equals(modelName)) {
      return new DummyModel() {
      };
    }
    if (TurnOffDefaultTasks.class.getName().equals(modelName)) {
      StartParameter startParameter = project.getGradle().getStartParameter();
      List<String> taskNames = startParameter.getTaskNames();
      if (taskNames.isEmpty()) {
        startParameter.setTaskNames(null);
        List<String> helpTask = Collections.singletonList("help");
        project.setDefaultTasks(helpTask);
        startParameter.setExcludedTaskNames(helpTask);
      }
      return null;
    }

    if (myModelBuilderContext == null) {
      Gradle rootGradle = getRootGradle(project.getGradle());
      myModelBuilderContext = new DefaultModelBuilderContext(rootGradle);
    }

    for (ModelBuilderService service : modelBuilderServices) {
      if (service.canBuild(modelName)) {
        final long startTime = System.currentTimeMillis();
        try {
          if (service instanceof ModelBuilderService.ParameterizedModelBuilderService)
            return ((ModelBuilderService.ParameterizedModelBuilderService)service)
              .buildAll(modelName, project, myModelBuilderContext, parameter);
          if (service instanceof ModelBuilderService.Ex)
            return ((ModelBuilderService.Ex)service).buildAll(modelName, project, myModelBuilderContext);
          else {
            return service.buildAll(modelName, project);
          }
        }
        catch (Exception e) {
          if (service instanceof ExternalProjectBuilderImpl) {
            //Probably checked exception might still pop from poorly behaving implementation
            //noinspection ConstantValue
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            throw new ExternalSystemException(e);
          }
          reportModelBuilderFailure(project, service, myModelBuilderContext, e);
        }
        finally {
          if (Boolean.getBoolean("idea.gradle.custom.tooling.perf")) {
            final long timeInMs = (System.currentTimeMillis() - startTime);
            reportPerformanceStatistic(project, service, modelName, timeInMs);
          }
        }
        return null;
      }
    }
    throw new IllegalArgumentException("Unsupported model: " + modelName);
  }

  private static void reportPerformanceStatistic(Project project, ModelBuilderService service, String modelName, long timeInMs) {
    String msg = String.format("%s: service %s imported '%s' in %d ms", project.getDisplayName(), service.getClass().getSimpleName(), modelName, timeInMs);
    project.getLogger().error(msg);
  }

  public static void reportModelBuilderFailure(@NotNull Project project,
                                               @NotNull ModelBuilderService service,
                                               @NotNull ModelBuilderContext modelBuilderContext,
                                               @NotNull Exception modelBuilderError) {
    try {
      Message message = service.getErrorMessageBuilder(project, modelBuilderError).buildMessage();
      modelBuilderContext.report(project, message);
    }
    catch (Throwable e) {
      LOG.warn("Failed to report model builder error", e);
    }
  }

  @NotNull
  private static Gradle getRootGradle(@NotNull Gradle gradle) {
    Gradle root = gradle;
    while (root.getParent() != null) {
      root = root.getParent();
    }
    return root;
  }
}
