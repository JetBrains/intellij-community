// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.projectModel;

import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.DefaultGradleSourceSetModel;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetCache;
import com.intellij.gradle.toolingExtension.impl.model.taskModel.GradleTaskCache;
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleObjectUtil;
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.Test;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalProject;
import org.jetbrains.plugins.gradle.model.DefaultExternalTask;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalProjectPreview;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.builder.ProjectExtensionsDataBuilderImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.intellij.gradle.toolingExtension.impl.util.GradleIdeaPluginUtil.getIdeaModuleName;
import static com.intellij.gradle.toolingExtension.util.GradleNegotiationUtil.getProjectIdentityPath;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class ExternalProjectBuilderImpl extends AbstractModelBuilderService {

  @Override
  public boolean canBuild(@NotNull String modelName) {
    return ExternalProject.class.getName().equals(modelName) ||
           ExternalProjectPreview.class.getName().equals(modelName);
  }

  @Override
  public @Nullable Object buildAll(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    if (Objects.equals(System.getProperty("idea.internal.failEsModelBuilder"), "true")) {
      throw new RuntimeException("Boom!");
    }
    return buildExternalProject(project, context);
  }

  @NotNull
  private static DefaultExternalProject buildExternalProject(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    String projectPath = project.getPath();
    String projectName = project.getName();
    String projectIdentityPath = GradleObjectUtil.notNull(getProjectIdentityPath(project), projectPath);
    String ideaModuleName = GradleObjectUtil.notNull(getIdeaModuleName(project), projectName);

    DefaultExternalProject externalProject = new DefaultExternalProject();
    externalProject.setExternalSystemId("GRADLE");
    externalProject.setName(projectName);
    externalProject.setQName(":".equals(projectPath) ? projectName : projectPath);
    externalProject.setId(":".equals(projectIdentityPath) ? ideaModuleName : projectIdentityPath);
    externalProject.setPath(projectPath);
    externalProject.setIdentityPath(projectIdentityPath);
    externalProject.setVersion(wrap(project.getVersion()));
    externalProject.setDescription(project.getDescription());
    externalProject.setBuildDir(project.getBuildDir());
    externalProject.setBuildFile(project.getBuildFile());
    externalProject.setGroup(wrap(project.getGroup()));
    externalProject.setProjectDir(project.getProjectDir());
    externalProject.setTasks(getTasks(project, context));
    externalProject.setSourceSetModel(getSourceSetModel(project, context));

    return externalProject;
  }

  private static @NotNull Map<String, DefaultExternalTask> getTasks(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    Map<String, DefaultExternalTask> result = new HashMap<>();

    GradleTaskCache taskCache = GradleTaskCache.getInstance(context);
    for (Task task : taskCache.getAllTasks(project)) {
      String taskName = task.getName();
      DefaultExternalTask externalTask = result.get(taskName);
      if (externalTask == null) {
        externalTask = new DefaultExternalTask();
        externalTask.setName(taskName);
        externalTask.setQName(taskName);
        externalTask.setDescription(task.getDescription());
        externalTask.setGroup(GradleObjectUtil.notNull(task.getGroup(), "other"));
        boolean isInternalTest = GradleTaskUtil.getBooleanProperty(task, "idea.internal.test", false);
        boolean isEffectiveTest = "check".equals(taskName) && "verification".equals(task.getGroup());
        boolean isJvmTest = task instanceof Test;
        boolean isAbstractTest = task instanceof AbstractTestTask;
        externalTask.setTest(isJvmTest || isAbstractTest || isInternalTest || isEffectiveTest);
        externalTask.setJvmTest(isJvmTest || isAbstractTest);
        externalTask.setType(ProjectExtensionsDataBuilderImpl.getType(task));
        result.put(externalTask.getName(), externalTask);
      }

      String projectTaskPath = (":".equals(project.getPath()) ? ":" : project.getPath() + ":") + task.getName();
      if (projectTaskPath.equals(task.getPath())) {
        externalTask.setQName(task.getPath());
      }
    }
    return result;
  }

  private static @NotNull DefaultGradleSourceSetModel getSourceSetModel(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    return GradleSourceSetCache.getInstance(context)
      .getSourceSetModel(project);
  }

  private static @NotNull String wrap(@Nullable Object o) {
    return o instanceof CharSequence ? o.toString() : "";
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.PROJECT_MODEL_GROUP)
      .withKind(Message.Kind.ERROR)
      .withTitle("Project resolution failure")
      .withText("Unable to resolve additional project configuration")
      .withException(exception)
      .reportMessage(project);
  }
}
