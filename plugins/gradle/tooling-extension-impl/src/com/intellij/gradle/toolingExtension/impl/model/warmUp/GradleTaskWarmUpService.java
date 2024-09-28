// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.warmUp;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleResultUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

@ApiStatus.Internal
public class GradleTaskWarmUpService extends AbstractModelBuilderService {

  private static final boolean TASKS_REFRESH_REQUIRED = GradleVersionUtil.isCurrentGradleOlderThan("5.0");

  @Override
  public boolean canBuild(String modelName) {
    return GradleTaskWarmUpRequest.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    // Android Studio (b/243767844, b/235320590): only register test tasks when fetching Gradle task information.
    // This is tested by TaskConfigurationNotTriggeredDuringSyncTest.testTasksAreNotConfiguredDuringSync().
    boolean skipTasks;
    if (GradleVersion.current().compareTo(GradleVersion.version("8.1")) >= 0) { // https://github.com/gradle/gradle/issues/19793
      Provider<String> buildTasksProvider = project.getProviders().gradleProperty("idea.gradle.do.not.build.tasks");
      skipTasks = Boolean.parseBoolean(String.valueOf(buildTasksProvider.getOrNull()).trim());
    } else {
      skipTasks = Boolean.parseBoolean(String.valueOf(project.getProperties().get("idea.gradle.do.not.build.tasks")).trim());
    }
    if (!skipTasks) {
      GradleResultUtil.runOrRetryOnce(() -> {
        if (TASKS_REFRESH_REQUIRED) {
          refreshProjectTasks(project);
        }
      });
      GradleResultUtil.runOrRetryOnce(() -> {
        project.getTasks().forEach(__ -> {});
      });
    }
    return null;
  }

  private static void refreshProjectTasks(@NotNull Project project) {
    TaskContainer tasks = project.getTasks();
    if (tasks instanceof DefaultTaskContainer) {
      ((DefaultTaskContainer)tasks).discoverTasks();
      for (String taskName : tasks.getNames()) {
        tasks.findByName(taskName);
      }
    }
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.TASK_WARM_UP_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Task warming-up failure")
      .withText("Unable to warm-up Gradle task model")
      .withException(exception)
      .reportMessage(project);
  }
}
