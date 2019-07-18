// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;

public class SaveStarter extends ApplicationStarterBase {
  protected SaveStarter() {
    super("save", 0);
  }

  @Override
  public String getUsageMessage() {
    return "Wrong number of arguments. Usage: <ide executable> save";
  }

  @NotNull
  @Override
  protected Future<? extends CliResult> processCommand(@NotNull String[] args, @Nullable String currentDirectory) {
    saveAll();
    return CliResult.ok();
  }
}