// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.utilTurnOffDefaultTasksModel;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.Collections;
import java.util.List;

public class TurnOffDefaultTasksModelBuilder extends AbstractModelBuilderService {

  @Override
  public boolean canBuild(String modelName) {
    return TurnOffDefaultTasks.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    StartParameter startParameter = project.getGradle().getStartParameter();
    List<String> taskNames = startParameter.getTaskNames();
    if (taskNames.isEmpty()) {
      startParameter.setTaskNames(null);
      List<String> helpTask = Collections.singletonList(":help");
      project.setDefaultTasks(helpTask);
      startParameter.setExcludedTaskNames(helpTask);
    }
    return new DefaultTurnOffDefaultTasks();
  }
}
