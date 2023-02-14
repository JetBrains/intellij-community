// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.integrations.maven;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
final class GradleProjectStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull final Project project) {
    new ImportMavenRepositoriesTask(project).schedule();
  }
}
