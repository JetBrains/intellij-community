// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

final class GradleToolWindowFactory extends AbstractExternalSystemToolWindowFactory {
  GradleToolWindowFactory() {
    super(GradleConstants.SYSTEM_ID);
  }

  @Override
  protected @NotNull AbstractExternalSystemSettings<?, ?, ?> getSettings(@NotNull Project project) {
    return GradleSettings.getInstance(project);
  }
}
