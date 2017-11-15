// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.Nullable;

public class SaveStarter extends ApplicationStarterBase {
  protected SaveStarter() {
    super("save", 0);
  }

  @Override
  public String getUsageMessage() {
    return "Wrong number of arguments. Usage: <ide executable> save";
  }

  @Override
  protected void processCommand(String[] args, @Nullable String currentDirectory) throws Exception {
    saveAll();
  }
}
