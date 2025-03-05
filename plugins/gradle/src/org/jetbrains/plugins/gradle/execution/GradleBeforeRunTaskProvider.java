// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTask;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTaskProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 */
public final class GradleBeforeRunTaskProvider extends ExternalSystemBeforeRunTaskProvider implements DumbAware {
  public static final Key<ExternalSystemBeforeRunTask> ID = Key.create("Gradle.BeforeRunTask");

  public GradleBeforeRunTaskProvider(Project project) {
    super(GradleConstants.SYSTEM_ID, project, ID);
  }

  @Override
  public Icon getIcon() {
    return GradleIcons.Gradle;
  }

  @Override
  public @Nullable Icon getTaskIcon(ExternalSystemBeforeRunTask task) {
    return GradleIcons.Gradle;
  }

  @Override
  public @Nullable ExternalSystemBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    return new ExternalSystemBeforeRunTask(ID, GradleConstants.SYSTEM_ID);
  }
}
