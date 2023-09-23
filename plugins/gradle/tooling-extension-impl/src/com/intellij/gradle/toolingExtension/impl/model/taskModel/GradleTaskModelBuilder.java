// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskModel;

import com.intellij.gradle.toolingExtension.impl.util.GradleResultUtil;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@ApiStatus.Internal
public class GradleTaskModelBuilder extends AbstractModelBuilderService {

  private static final GradleVersion gradleVersion = GradleVersion.current().getBaseVersion();
  private static final boolean TASKS_REFRESH_REQUIRED =
    gradleVersion.compareTo(GradleVersion.version("5.0")) < 0;


  @Override
  public boolean canBuild(String modelName) {
    return GradleTaskModel.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    GradleTaskCache taskCache = GradleTaskCache.getInstance(context);
    Set<Task> projectTasks = collectProjectTasks(project, context);
    taskCache.setProjectTasks(project, projectTasks);

    return new GradleTaskModel() {
    };
  }

  private static Set<Task> collectProjectTasks(@NotNull Project project, @NotNull ModelBuilderContext context) {
    try {
      GradleResultUtil.runOrRetryOnce(() -> {
        if (TASKS_REFRESH_REQUIRED) {
          refreshProjectTasks(project);
        }
      });
      return GradleResultUtil.runOrRetryOnce(() -> {
        return new TreeSet<>(project.getTasks());
      });
    }
    catch (Exception e) {
      context.getMessageReporter().createMessage()
        .withTitle("Project tasks collecting failure")
        .withText("Tasks for " + project + " cannot be collected due to plugin exception.")
        .withException(e)
        .withKind(Message.Kind.WARNING)
        .reportMessage(project);
      return Collections.emptySet();
    }
  }

  private static void refreshProjectTasks(@NotNull Project project) {
    TaskContainer tasks = project.getTasks();
    if (tasks instanceof DefaultTaskContainer) {
      ((DefaultTaskContainer)tasks).discoverTasks();
      SortedSet<String> taskNames = tasks.getNames();
      for (String taskName : taskNames) {
        tasks.findByName(taskName);
      }
    }
  }

  @Override
  public @NotNull ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(project, e, "Gradle import errors")
      .withDescription("Unable to warm-up Gradle task model");
  }
}
