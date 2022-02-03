// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

final class SaveStarter extends ApplicationStarterBase {
  private SaveStarter() {
    super(0);
  }

  @Override
  public String getCommandName() {
    return "save";
  }

  @Override
  public String getUsageMessage() {
    return IdeBundle.message("wrong.number.of.arguments.usage.ide.executable.save");
  }

  @NotNull
  @Override
  protected Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) {
    saveAll();
    return CompletableFuture.completedFuture(CliResult.OK);
  }
}