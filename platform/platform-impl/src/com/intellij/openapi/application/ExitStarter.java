// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    super("exit", 0, 1);
  }

  private static final String ourRestartParameter = "--restart";

  @Override
  public String getUsageMessage() {
    return IdeBundle.message("wrong.number.of.arguments.usage.ide.executable.exit");
  }

  @Override
  public int getRequiredModality() {
    return ANY_MODALITY;
  }

  @Override
  protected boolean checkArguments(@NotNull List<String> args) {
    return super.checkArguments(args) && (args.size() <= 1 || ourRestartParameter.equals(args.get(1)));
  }

  @NotNull
  @Override
  protected Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) {
    Application application = ApplicationManager.getApplication();
    LaterInvocator.cancelAllModals();
    application.invokeLater(() -> {
      application.exit(true, true, args.stream().anyMatch(it -> ourRestartParameter.equals(it)));
    }, ModalityState.NON_MODAL);
    return CompletableFuture.completedFuture(CliResult.OK);
  }
}