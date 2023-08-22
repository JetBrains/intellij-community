// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.task.BuildTask;
import com.intellij.task.ProjectModelBuildTask;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.task.VersionSpecificInitScript;

import java.util.function.BiConsumer;

/**
 * @author Vladislav.Soroka
 */
public interface GradleBuildTasksProvider {
  ExtensionPointName<GradleBuildTasksProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.plugins.gradle.buildTasksProvider");

  boolean isApplicable(@NotNull BuildTask buildTask);

  void addBuildTasks(@NotNull BuildTask buildTask,
                     @NotNull Consumer<ExternalTaskPojo> buildTasksConsumer,
                     @NotNull BiConsumer<String, VersionSpecificInitScript> initScriptConsumer);
}
