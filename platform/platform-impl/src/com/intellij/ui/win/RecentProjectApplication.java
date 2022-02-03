// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.win;

import com.intellij.ide.CliResult;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationStarterBase;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

final class RecentProjectApplication extends ApplicationStarterBase {
  RecentProjectApplication() {
    super(1);
  }

  @Override
  public String getCommandName() {
    return "reopen";
  }

  @Override
  public String getUsageMessage() {
    return "This command is used for internal purpose only."; //NON-NLS
  }

  @NotNull
  @Override
  protected Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) {
    ProjectManagerEx.getInstanceEx().openProject(Paths.get(args.get(1)).normalize(), new OpenProjectTask());
    return CompletableFuture.completedFuture(CliResult.OK);
  }
}