// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.ide.CliResult;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationStarterBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

final class RecentProjectApplication extends ApplicationStarterBase {
  RecentProjectApplication() {
    super("reopen", 1);
  }

  @Override
  public String getUsageMessage() {
    return "This command is used for internal purpose only.";
  }

  @NotNull
  @Override
  protected Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) {
    ProjectUtil.openProject(args.get(1), null, false);
    return CliResult.OK_FUTURE;
  }
}