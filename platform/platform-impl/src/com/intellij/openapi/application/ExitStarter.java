// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.impl.LaterInvocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

final class ExitStarter extends ApplicationStarterBase {
  private ExitStarter() {
    // extra argument count (2) to allow for usage of remote-dev-server.sh exit /path/to/project --restart
    super(0, 1, 2);
  }

  private static final String ourRestartParameter = "--restart";


  @Override
  public String getCommandName() {
    return "exit";
  }

  @Override
  public String getUsageMessage() {
    return IdeBundle.message("wrong.number.of.arguments.usage.ide.executable.exit");
  }

  @Override
  public int getRequiredModality() {
    return ANY_MODALITY;
  }

  @Override
  public boolean isHeadless() {
    return true;
  }

  @NotNull
  @Override
  protected Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) {
    Application application = ApplicationManager.getApplication();
    LaterInvocator.forceLeaveAllModals();
    application.invokeLater(() -> {
      application.exit(true, true, args.stream().anyMatch(it -> ourRestartParameter.equals(it)));
    }, ModalityState.NON_MODAL);
    return CompletableFuture.completedFuture(CliResult.OK);
  }
}