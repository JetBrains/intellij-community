// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting;

import com.jetbrains.performancePlugin.CommandProvider;
import com.jetbrains.performancePlugin.CreateCommand;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class GradleCommandProvider implements CommandProvider {
  @Override
  public @NotNull Map<String, CreateCommand> getCommands() {
    return new HashMap<>() {{
      put(ImportGradleProjectCommand.PREFIX, ImportGradleProjectCommand::new);
      put(ExecuteGradleTaskCommand.PREFIX, ExecuteGradleTaskCommand::new);
      put(LinkGradleProjectCommand.PREFIX, LinkGradleProjectCommand::new);
      put(UnlinkGradleProjectCommand.PREFIX, UnlinkGradleProjectCommand::new);
      put(SetGradleJdkCommand.PREFIX, SetGradleJdkCommand::new);
      put(DownloadGradleSourcesCommand.PREFIX, DownloadGradleSourcesCommand::new);
      put(SetGradleDelegatedBuildCommand.PREFIX, SetGradleDelegatedBuildCommand::new);
      put(SetBuildToolsAutoReloadTypeCommand.PREFIX, SetBuildToolsAutoReloadTypeCommand::new);
      put(ProjectNotificationAwareShouldBeVisibleCommand.PREFIX, ProjectNotificationAwareShouldBeVisibleCommand::new);
      put(RefreshGradleProjectCommand.PREFIX, RefreshGradleProjectCommand::new);
      put(CreateGradleProjectCommand.PREFIX, CreateGradleProjectCommand::new);
      put(ValidateGradleMatrixCompatibilityCommand.PREFIX, ValidateGradleMatrixCompatibilityCommand::new);
      put(AnalyzeDependenciesCommand.PREFIX, AnalyzeDependenciesCommand::new);
    }};
  }
}
