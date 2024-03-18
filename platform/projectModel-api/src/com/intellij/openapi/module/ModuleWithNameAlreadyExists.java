// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import org.jetbrains.annotations.NotNull;

public class ModuleWithNameAlreadyExists extends Exception {
  private final @NotNull String myModuleName;

  public ModuleWithNameAlreadyExists(@NotNull String message, @NotNull String moduleName) {
    super(message);
    myModuleName = moduleName;
  }

  public @NotNull String getModuleName() {
    return myModuleName;
  }
}
