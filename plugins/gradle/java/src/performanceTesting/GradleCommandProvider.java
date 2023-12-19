// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting;

import com.jetbrains.performancePlugin.CommandProvider;
import com.jetbrains.performancePlugin.CreateCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class GradleCommandProvider implements CommandProvider {
  @Override
  public @NotNull Map<String, CreateCommand> getCommands() {
    return Map.of(ImportGradleProjectCommand.PREFIX, ImportGradleProjectCommand::new,
                  ExecuteGradleTaskCommand.PREFIX, ExecuteGradleTaskCommand::new,
                  LinkGradleProjectCommand.PREFIX, LinkGradleProjectCommand::new,
                  UnlinkGradleProjectCommand.PREFIX, UnlinkGradleProjectCommand::new,
                  SetGradleJdkCommand.PREFIX, SetGradleJdkCommand::new);
  }
}
